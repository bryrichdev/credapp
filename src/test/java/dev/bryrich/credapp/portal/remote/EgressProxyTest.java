package dev.bryrich.credapp.portal.remote;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EgressProxyTest {

    @Test
    void publicAddressesOnly() throws Exception {
        for (String blocked : new String[]{"127.0.0.1", "10.1.2.3", "172.17.0.2", "192.168.1.10", "169.254.169.254",
                "100.100.1.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1", "::1", "::",
                "fd00:ec2::254", "fe80::1", "::ffff:127.0.0.1", "::ffff:169.254.169.254", "64:ff9b::a9fe:a9fe"}) {
            assertThat(EgressProxy.isPublic(InetAddress.getByName(blocked))).as(blocked).isFalse();
        }
        for (String open : new String[]{"8.8.8.8", "52.95.110.1", "172.32.0.1", "100.128.0.1",
                "2606:4700:4700::1111", "::ffff:8.8.8.8"}) {
            assertThat(EgressProxy.isPublic(InetAddress.getByName(open))).as(open).isTrue();
        }
    }

    @Test
    void refusesTheServersOwnNetwork() throws Exception {
        try (EgressProxy proxy = EgressProxy.start()) {
            assertThat(send(proxy, "CONNECT 127.0.0.1:443 HTTP/1.1\r\nHost: 127.0.0.1:443\r\n\r\n")).startsWith("HTTP/1.1 403");
            assertThat(send(proxy, "CONNECT 169.254.169.254:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
            assertThat(send(proxy, "CONNECT localhost:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
            assertThat(send(proxy, "CONNECT [::1]:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
        }
    }

    @Test
    void httpsPortsOnlyAndNoPlainHttp() throws Exception {
        try (EgressProxy proxy = new EgressProxy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")},
                address -> true, EgressProxy.PORTS)) {
            assertThat(send(proxy, "CONNECT portal.example.com:22 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
            assertThat(send(proxy, "CONNECT portal.example.com HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
            String page = send(proxy, "GET http://portal.example.com/ HTTP/1.1\r\nHost: portal.example.com\r\n\r\n");
            assertThat(page).startsWith("HTTP/1.1 403").contains("only opens secure (https) pages");
        }
    }

    @Test
    void refusesANameWithAnyPrivateAddress() throws Exception {
        try (EgressProxy proxy = new EgressProxy(host -> new InetAddress[]{
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.5")},
                EgressProxy::isPublic, EgressProxy.PORTS)) {
            assertThat(send(proxy, "CONNECT mixed.example.com:443 HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 403");
            assertThat(proxy.refused()).isEqualTo(1);
        }
    }

    @Test
    void tunnelsToAnAllowedHost() throws Exception {
        try (ServerSocket echo = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
             EgressProxy proxy = new EgressProxy(host -> new InetAddress[]{InetAddress.getLoopbackAddress()},
                     address -> true, Set.of(echo.getLocalPort()))) {
            Thread.ofVirtual().start(() -> {
                try (Socket s = echo.accept()) {
                    byte[] got = s.getInputStream().readNBytes(5);
                    s.getOutputStream().write(("echo:" + new String(got, StandardCharsets.US_ASCII)).getBytes());
                } catch (IOException ignored) {
                    // test fails on its own
                }
            });
            URI address = URI.create(proxy.address());
            try (Socket client = new Socket(address.getHost(), address.getPort())) {
                client.getOutputStream().write(("CONNECT portal.test:" + echo.getLocalPort()
                        + " HTTP/1.1\r\n\r\nhello").getBytes(StandardCharsets.US_ASCII));
                String reply = new String(client.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                assertThat(reply).isEqualTo("HTTP/1.1 200 Connection Established\r\n\r\necho:hello");
            }
        }
    }

    private static String send(EgressProxy proxy, String request) throws IOException {
        URI address = URI.create(proxy.address());
        try (Socket client = new Socket(address.getHost(), address.getPort())) {
            client.setSoTimeout(5000);
            client.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            InputStream in = client.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
