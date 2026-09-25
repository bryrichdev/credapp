package dev.bryrich.credapp.mail;

/** One plain-text email to one person. */
public record Email(String to, String subject, String text) {
}
