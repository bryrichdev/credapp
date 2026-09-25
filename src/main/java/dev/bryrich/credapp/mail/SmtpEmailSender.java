package dev.bryrich.credapp.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends through an SMTP server, such as Gmail's, using the spring.mail settings. */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(Email email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.text());
        mail.send(message);
    }
}
