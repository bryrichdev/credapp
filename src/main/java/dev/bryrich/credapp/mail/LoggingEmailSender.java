package dev.bryrich.credapp.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no email service is configured. Logs who would have been emailed. The body,
 * which can hold a password reset link, is only logged when credapp.mail.log-body is on,
 * as it is for the dev profile.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean logBody;

    public LoggingEmailSender(boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public void send(Email email) {
        if (logBody) {
            log.info("Email to {} (no email service configured, not sent)\nSubject: {}\n\n{}",
                    email.to(), email.subject(), email.text());
        } else {
            log.warn("Email to {} not sent, no email service configured: {}", email.to(), email.subject());
        }
    }
}
