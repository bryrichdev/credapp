package dev.bryrich.credapp.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    @Test
    void itSendsAPlainTextMessageFromTheConfiguredAddress() {
        JavaMailSender mail = mock(JavaMailSender.class);
        new SmtpEmailSender(mail, "CredCloud <me@gmail.com>")
                .send(new Email("you@example.com", "Hello", "Body text"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("CredCloud <me@gmail.com>");
        assertThat(sent.getValue().getTo()).containsExactly("you@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Hello");
        assertThat(sent.getValue().getText()).isEqualTo("Body text");
    }

    @Test
    @SuppressWarnings("unchecked")
    void smtpIsUsedOnlyWhenAMailServerIsConfigured() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mock(JavaMailSender.class));
        MailConfig config = new MailConfig();

        assertThat(config.emailSender(provider, "", "", "", false)).isInstanceOf(LoggingEmailSender.class);
        assertThat(config.emailSender(provider, "smtp.gmail.com", "me@gmail.com", "", false))
                .isInstanceOf(SmtpEmailSender.class);
    }

    /** Otherwise an unreachable mail server marks the app unhealthy and every deploy rolls back. */
    @Test
    void theHealthCheckTheDeployWaitsOnIgnoresTheMailServer() throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        try (var in = new org.springframework.core.io.ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }
        assertThat(properties.getProperty("management.health.mail.enabled")).isEqualTo("false");
    }

    @Test
    void theSenderDefaultsToTheGmailAccountUnderTheAppsName() {
        assertThat(MailConfig.fromAddress("", "me@gmail.com")).isEqualTo("CredCloud <me@gmail.com>");
        assertThat(MailConfig.fromAddress(" Alerts <a@b.com> ", "me@gmail.com")).isEqualTo("Alerts <a@b.com>");
        assertThat(MailConfig.fromAddress(null, null)).isEqualTo("CredCloud <no-reply@credcloud.app>");
    }
}
