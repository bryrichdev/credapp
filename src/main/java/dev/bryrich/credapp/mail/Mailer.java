package dev.bryrich.credapp.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends the app's emails. Three rules:
 * <ul>
 *   <li>Only after the change that caused it is saved. An approval that rolls back sends nothing.</li>
 *   <li>In the background, so a slow email service never slows a page, and the forgot-password
 *       page takes the same time whether or not the email has an account.</li>
 *   <li>A failed send is logged, never shown as an error: the approval or request itself worked.</li>
 * </ul>
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final EmailSender sender;
    private final TaskExecutor executor;
    private final boolean async;
    private final String baseUrl;

    public Mailer(EmailSender sender, @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                  @Value("${credapp.mail.async:true}") boolean async,
                  @Value("${credapp.base-url}") String baseUrl) {
        this.sender = sender;
        this.executor = executor;
        this.async = async;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** An absolute link into the app, from the configured address, never the request's Host header. */
    public String link(String path) {
        return baseUrl + path;
    }

    public void send(String to, String subject, String text) {
        Email email = new Email(to, subject, text + "\n\n— CredCloud\n" + baseUrl + "\n");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(email);
                }
            });
        } else {
            dispatch(email);
        }
    }

    private void dispatch(Email email) {
        if (async) {
            executor.execute(() -> deliver(email));
        } else {
            deliver(email);
        }
    }

    private void deliver(Email email) {
        try {
            sender.send(email);
        } catch (Exception ex) {
            log.warn("Couldn't send '{}' to {}: {}", email.subject(), email.to(), ex.toString());
        }
    }
}
