package dev.bryrich.credapp.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps every email instead of sending it, for tests to read. */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern RESET_LINK = Pattern.compile("/password-reset/([A-Za-z0-9_-]+)");

    private final List<Email> sent = new ArrayList<>();

    @Override
    public synchronized void send(Email email) {
        sent.add(email);
    }

    public synchronized List<Email> to(String address) {
        return sent.stream().filter(email -> email.to().equalsIgnoreCase(address)).toList();
    }

    /** The token in the newest reset link emailed to this address. */
    public synchronized Optional<String> resetToken(String address) {
        List<Email> emails = to(address);
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher matcher = RESET_LINK.matcher(emails.get(i).text());
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    /** Add with @Import; also set credapp.mail.async=false so emails are recorded before the request returns. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
