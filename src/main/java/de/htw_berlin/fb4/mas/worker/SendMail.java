package de.htw_berlin.fb4.mas.worker;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import static jakarta.mail.Message.RecipientType.TO;

/**
 * Implementierung eines {@link ExternalTaskHandler} zum Versenden einfacher Text-Mails.
 * <p>
 * Der Handler erwartet, dass die Variablen "from", "to", "subject" und "body" gesetzt sind.
 * <p>
 * Für eine bessere Wiederverwendbarkeit, können diese Variablen über ein
 * <a href="https://docs.camunda.org/manual/7.21/user-guide/process-engine/variables/#input-output-variable-mapping">Input/Output Variable Mapping</a>
 * auf Basis von existierenden Variablen oder im Prozess definierten Werten gesetzt werden. Beispiel:
 * <ul>
 *     <li>from = sales@example.org</li>
 *     <li>to = ${customer_mail_address}</li>
 *     <li>subject = Auftragsbestätigung</li>
 *     <li>body = Sehr geehrte Damen und Herren, hiermit bestätigen wir Ihnen Auftrag ${auftragsnummer}.</li>
 * </ul>
 */
public class SendMail implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SendMail.class);

    private static final String smtpHost = "mail.htw-berlin.de";

    private static final int smtpPort = 25;

    private static final String smtpUser = "s0xxxxxx";

    private static final String smtpPassword = "geheim";

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        log.info("Handling external task (Task ID: {} - Process Instance ID {})", externalTask.getId(), externalTask.getProcessInstanceId());

        String from = externalTask.getVariable("from");
        String to = externalTask.getVariable("to");
        String subject = externalTask.getVariable("subject");
        String body = externalTask.getVariable("body");

        try {
            log.info("Sending mail with subject '{}' to '{}'", subject, to);
            sendMail(from, to, subject, body);

            externalTaskService.complete(externalTask);
        } catch (MessagingException e) {
            handleFailure(externalTask, externalTaskService, e);
        }
    }

    private void sendMail(String from, String to, String subject, String body) throws MessagingException {
        Session session = createSession();

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }

    private static Session createSession() {
        Properties mailProperties = new Properties();
        mailProperties.put("mail.smtp.auth", true);
        mailProperties.put("mail.smtp.starttls.enable", "true");
        mailProperties.put("mail.smtp.host", smtpHost);
        mailProperties.put("mail.smtp.port", smtpPort);
        mailProperties.put("mail.smtp.ssl.trust", smtpHost);

        return Session.getInstance(mailProperties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });
    }

    private static void handleFailure(ExternalTask externalTask, ExternalTaskService externalTaskService, Exception exception) {
        String errorMessage = "Send Mail Failed";

        StringWriter stackTraceWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTraceWriter));
        String errorDetails = stackTraceWriter.toString();

        createIncident(externalTask, externalTaskService, errorMessage, errorDetails);
    }

    private static void createIncident(ExternalTask externalTask, ExternalTaskService externalTaskService, String errorMessage, String errorDetails) {
        int retries = 0;
        long retryTimeout = 0;

        log.info("Creating incident for process instance {} with message '{}'", externalTask.getProcessInstanceId(), errorMessage);
        externalTaskService.handleFailure(externalTask, errorMessage, errorDetails, retries, retryTimeout);
    }
}
