package org.jembi.ciol.notificationservice;

import com.mysql.cj.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jembi.ciol.AppConfig;
import org.jembi.ciol.models.*;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;

public class EmailService {
    private static final Logger LOGGER = LogManager.getLogger(EmailService.class);
    public final Map<String, DestinationElement> emailAddressMap = new HashMap<>();
    private String username = null;
    private String password = null;
    public Properties props = null;

    public void init() {
        LOGGER.debug("init");
        if (Main.restConfig == null){
            LOGGER.warn("No Configurations");
            return;
        }
        LOGGER.debug("email: {}", Main.restConfig.adminEmails());

        final var username = Main.restConfig.smtpConfig().username();
        final var password = Main.restConfig.smtpConfig().password();
        final var port = Main.restConfig.smtpConfig().port();
        final var host = Main.restConfig.smtpConfig().host();

        LOGGER.debug("username: {}", username);


        props = new Properties();
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        this.username = username;
        this.password = password;

        if(Main.restConfig != null) {
            try {
                InternetAddress[] internetAddresses = new InternetAddress[Main.restConfig.adminEmails().size()];
                for (int i = 0; i < Main.restConfig.adminEmails().size(); i++) {
                    InternetAddress internetAddress = new InternetAddress(Main.restConfig.adminEmails().get(i));
                    internetAddresses[i] = internetAddress;
                }

                emailAddressMap.put(
                        "Admin",
                        new DestinationElement("""
                                               Code:    $Code
                                               Message: $Message""",
                                internetAddresses));

            } catch (AddressException e) {
                LOGGER.error(e.getLocalizedMessage(), e);
            }
        }
        LOGGER.debug("username: {}", this.username);
        LOGGER.debug("password: {}", this.password);
        LOGGER.debug("properties: {}", this.props);
        LOGGER.debug("admin email: {}", this.emailAddressMap);

    }
    private EmailService() {
    }

    record DestinationElement (
            String template,
            InternetAddress[] destination) {}

    public static EmailService getInstance() {
        return EmailServiceHolder.INSTANCE;
    }

    private String formatErrorMessageBody(final String template,
                                          final NotificationMessage.Message message) {
        return template.replace("$Code",Integer.toString(message.statusCode()))
                       .replace("$Message",message.messageBody());
    }

    public void sendErrorEmail(final String from, NotificationMessage.Message m) {
        LOGGER.debug("username:{}", username);
        LOGGER.debug("password:{}", password);
        LOGGER.debug("email: {}", emailAddressMap);
        LOGGER.debug("recipient: {}", m.recipientTag());

        if (StringUtils.isNullOrEmpty(username) || StringUtils.isNullOrEmpty(password) || StringUtils.isNullOrEmpty(m.recipientTag())){
            return;
        }
        if (emailAddressMap.get(m.recipientTag()) == null) {
            return;
        }
        var toAd = emailAddressMap.get(m.recipientTag()).destination;
        String subject = m.messageType();
        String messageBody = formatErrorMessageBody(emailAddressMap.get(m.recipientTag()).template, m);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, toAd);
            message.setSubject(subject);

            MimeBodyPart mimeBodyPart = new MimeBodyPart();
            mimeBodyPart.setText(messageBody);

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(mimeBodyPart);

            message.setContent(multipart);

//            Transport.send(message);

        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage(), e);
        }

    }

    private static class EmailServiceHolder {
        public static final EmailService INSTANCE = new EmailService();
    }
}
