package dev.bryrich.credapp.mail;

/** Hands an email to whatever delivers it. Throws if it couldn't be handed over. */
public interface EmailSender {
    void send(Email email) throws Exception;
}
