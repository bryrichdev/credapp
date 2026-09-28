package dev.bryrich.credapp.portal.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * The only way out for CredCloud's browsers. Chromium starts with this as its proxy, so every
 * page, script, image and websocket connects through here, and only https to a public address
 * gets through. The proxy looks the host up itself and connects to the address it checked, so
 * a portal can't steer CredCloud into the server's own network (the database, the EC2 metadata
 * service) with a redirect, a script, or a DNS answer that changes between lookups.
 */
public final class EgressProxy implements AutoCloseable {

    /** Looks a host up. Tests swap in their own so a made-up name can reach a local server. */
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    static final Set<Integer> PORTS = Set.of(443, 8443);
    private static final int MAX_HEAD = 8192;
    private static final byte[] REFUSED = ("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    private static final String HTTPS_ONLY_PAGE = """
            <!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>Not opened</title></head>
            <body style="font:16px sans-serif;margin:48px;color:#1a202c">
            <h1 style="font-size:20px">CredCloud didn't open this page</h1>
            <p>CredCloud's browser only opens secure (https) pages.</p></body></html>""";

    private final ServerSocket server;
    private final Resolver resolver;
    private final Predicate<InetAddress> allowed;
    private final Set<Integer> ports;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong refused = new AtomicLong();

    /** A proxy that lets https through to public addresses only. */
    public static EgressProxy start() throws IOException {
        return new EgressProxy(InetAddress::getAllByName, EgressProxy::isPublic, PORTS);
    }

    EgressProxy(Resolver resolver, Predicate<InetAddress> allowed, Set<Integer> ports) throws IOException {
        this.resolver = resolver;
        this.allowed = allowed;
        this.ports = Set.copyOf(ports);
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        threads.submit(this::accept);
    }

    /** For Chromium's proxy setting. */
    public String address() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    /** How many connections were turned away, for tests. */
    long refused() {
        return refused.get();
    }

    @Override
    public void close() throws IOException {
        server.close();
        threads.shutdownNow();
    }

    private void accept() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                threads.submit(() -> handle(client));
            } catch (IOException e) {
                // closed, or one bad accept; either way the loop decides
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(30_000);
            String head = readHead(client.getInputStream());
            if (head == null) {
                return;
            }
            String[] request = head.substring(0, head.indexOf("\r\n")).split(" ");
            if (request.length != 3) {
                refuse(client, REFUSED);
                return;
            }
            if (!request[0].equals("CONNECT")) {
                // A plain-http page. Say so on the page rather than failing silently.
                byte[] body = HTTPS_ONLY_PAGE.getBytes(StandardCharsets.UTF_8);
                refuse(client, concat(("HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=utf-8\r\n"
                        + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII), body));
                return;
            }
            InetSocketAddress target = target(request[1]);
            if (target == null) {
                refuse(client, REFUSED);
                return;
            }
            try (Socket upstream = new Socket()) {
                upstream.connect(target, 10_000);
                upstream.setSoTimeout(300_000);
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().flush();
                client.setSoTimeout(300_000);
                InputStream fromUpstream = upstream.getInputStream();
                OutputStream toClient = client.getOutputStream();
                var back = threads.submit(() -> {
                    copy(fromUpstream, toClient);
                    try {
                        client.shutdownOutput();
                    } catch (IOException ignored) {
                        // already gone
                    }
                });
                copy(client.getInputStream(), upstream.getOutputStream());
                try {
                    upstream.shutdownOutput();
                } catch (IOException ignored) {
                    // already gone
                }
                back.get();
            }
        } catch (Exception e) {
            // A dropped connection is normal for a browser; nothing to report.
        }
    }

    /**
     * Where a CONNECT may go, or null. The host is looked up here and every address it has
     * must be public, so a name can't mix a public address with a private one.
     */
    private InetSocketAddress target(String authority) {
        int colon = authority.lastIndexOf(':');
        if (colon <= 0) {
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(authority.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        String host = authority.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (!ports.contains(port) || host.isEmpty()) {
            return null;
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0 || !Arrays.stream(addresses).allMatch(allowed)) {
                return null;
            }
            return new InetSocketAddress(addresses[0], port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private void refuse(Socket client, byte[] response) throws IOException {
        refused.incrementAndGet();
        client.getOutputStream().write(response);
        client.getOutputStream().flush();
    }

    /** The request line and headers, read a byte at a time so nothing after them is lost. */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (head.size() < MAX_HEAD) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            head.write(b);
            state = (b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3))
                    ? state + 1 : (b == '\r' ? 1 : 0);
            if (state == 4) {
                return head.toString(StandardCharsets.ISO_8859_1);
            }
        }
        return null;
    }

    private static void copy(InputStream in, OutputStream out) {
        try {
            in.transferTo(out);
            out.flush();
        } catch (IOException ignored) {
            // one side hung up
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    /**
     * True for an address on the public internet. Everything else is refused: loopback,
     * private and carrier-grade NAT ranges, link-local (which includes the EC2 metadata
     * service at 169.254.169.254 and fd00:ec2::254), multicast, and reserved blocks.
     */
    static boolean isPublic(InetAddress address) {
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                return isPublicV4(Arrays.copyOfRange(b, 12, 16));
            }
            // NAT64 (64:ff9b::/96) carries an IPv4 address in its last four bytes.
            if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
                return isPublicV4(Arrays.copyOfRange(b, 12, 16));
            }
            if ((b[0] & 0xfe) == 0xfc) {
                return false; // fc00::/7, unique local
            }
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
                return false; // 2001:db8::/32, documentation
            }
            return !(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress());
        }
        return address instanceof Inet4Address && isPublicV4(b);
    }

    private static boolean isPublicV4(byte[] b) {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        int a2 = b[2] & 0xff;
        return !(a0 == 0 || a0 == 10 || a0 == 127 || a0 >= 224
                || (a0 == 100 && a1 >= 64 && a1 <= 127)
                || (a0 == 169 && a1 == 254)
                || (a0 == 172 && a1 >= 16 && a1 <= 31)
                || (a0 == 192 && a1 == 168)
                || (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2))
                || (a0 == 198 && (a1 == 18 || a1 == 19))
                || (a0 == 198 && a1 == 51 && a2 == 100)
                || (a0 == 203 && a1 == 0 && a2 == 113));
    }
}
