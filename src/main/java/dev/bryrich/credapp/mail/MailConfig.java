package dev.bryrich.credapp.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class MailConfig {

    /** SMTP when a mail server is configured; otherwise emails are written to the log. */
    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mail,
                            @Value("${spring.mail.host:}") String host,
                            @Value("${spring.mail.username:}") String username,
                            @Value("${credapp.mail.from:}") String from,
                            @Value("${credapp.mail.log-body:false}") boolean logBody) {
        if (host.isBlank()) {
            return new LoggingEmailSender(logBody);
        }
        return new SmtpEmailSender(mail.getObject(), fromAddress(from, username));
    }

    /** Gmail only sends as the signed-in address, so by default that's the sender, under the app's name. */
    static String fromAddress(String from, String username) {
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        if (username != null && !username.isBlank()) {
            return "CredCloud <" + username.trim() + ">";
        }
        return "CredCloud <no-reply@credcloud.app>";
    }
}
