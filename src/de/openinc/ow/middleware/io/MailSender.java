package de.openinc.ow.middleware.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.middleware.services.DataService;

public class MailSender extends ActuatorAdapter {

    private String host;
    private int port;
    private String user;
    private String pw;
    private String mailName;
    private String textCharSet = "UTF-8";
    private static final String type = "email";
    private boolean debug;
    private String debugMail;
    private boolean tlsActive;
    private String mailDebugURL;

    // ExecutorService für den Retry-Mechanismus
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private static final int MAX_SCHEDULED_TASKS = 250;

    // Öffentlicher parameterloser Konstruktor erforderlich für ServiceLoader
    public MailSender() {
        // Initialisierung (falls erforderlich)
    }

    // Initialisierungsmethode
    @Override
    public void init(JSONObject options) throws Exception {
        this.host = options.getString("outboundMailServer");
        this.port = options.getInt("port");
        this.user = options.optString("user").equals("") ? null : options.optString("user");
        this.pw = options.optString("password").equals("") ? null : options.optString("password");
        this.mailName = options.optString("mailname").equals("") ? null : options.optString("mailname");
        this.debug = options.optBoolean("debug", false);
        this.debugMail = options.optString("debugMail");
        this.tlsActive = options.optBoolean("tls", false);
        this.mailDebugURL = options.optString("mailDebugURL", "");
    }

    // Typ des MailSenders
    @Override
    public String getType() {
        return type;
    }

    // Hauptmethode zum Versenden von E-Mails mit Batch-Verarbeitung und Retry-Mechanismus
    public String sendMail(String absender, String empfaenger, String betreff, String text)
            throws IOException, EmailException {

        // Überprüfe die Eingabeparameter
        if (absender == null || empfaenger == null || betreff == null || text == null) {
            throw new IllegalArgumentException("Einer der Parameter ist null.");
        }

        // Verwende debugMail, wenn im Debug-Modus
        if (debug) {
            empfaenger = this.debugMail;
        }

        // Maximal 50 Empfänger pro Batch
        String[] recipients = empfaenger.split(";");
        int batchSize = 50;
        StringBuilder res = new StringBuilder();

        // Verarbeite die Empfänger in Batches
        for (int i = 0; i < recipients.length; i += batchSize) {
            String[] batch = Arrays.copyOfRange(recipients, i, Math.min(i + batchSize, recipients.length));
            for (String recipient : batch) {
                try {
                    sendEmail(absender, recipient, betreff, text);
                    OpenWareInstance.getInstance().logMail("Mail erfolgreich gesendet an " + recipient + " mit Betreff: " + betreff);
                    res.append("Erfolg: ").append(recipient).append("\n");
                } catch (Exception e) {
                    OpenWareInstance.getInstance().logMail(
                            "Fehler beim Senden der Mail an " + recipient + ": " + e.getMessage());
                    OpenWareInstance.getInstance().logError("Fehler in der Mail: ", e);

                    // Versuche die E-Mail asynchron erneut zu senden
                    sendEmailAsync(absender, recipient, betreff, text);
                }
            }
        }
        return res.toString();
    }

    // Methode zum eigentlichen Senden der E-Mail
    private void sendEmail(String absender, String empfaenger, String betreff, String text) throws EmailException {
        HtmlEmail email = new HtmlEmail();
        // Setze Timeouts
        email.setSocketConnectionTimeout(10000); // 10 Sekunden Timeout
        email.setSocketTimeout(10000); // Timeout für die Antwort

        // Authentifizierung
        if (user != null && pw != null) {
            email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
            if(this.tlsActive) {
                email.setStartTLSEnabled(true);
            } else {
            	email.setSSL(true);
            }
            email.setSmtpPort(this.port);
        }
        email.setHostName(this.host);

        // Absender setzen
        if (absender == null) {
            absender = user;
        }
        email.setFrom(absender, this.mailName);
        email.addTo(empfaenger);
        email.setCharset(textCharSet);
        email.setSubject(betreff);
        email.setHtmlMsg(text);

        OpenWareInstance.getInstance().logMail("------------------------ Sende Mail an: " + empfaenger + " mit Betreff: " + betreff
                + " (" + this.host + ":" + this.port + ")");
        email.send();
    }

    // Asynchrone Methode zum erneuten Senden der E-Mail bei Fehlern
    private void sendEmailAsync(String absender, String empfaenger, String betreff, String text) {

        Runnable emailTask = new Runnable() {
            private int retryCount = 0;
            private final int maxRetries = 5;
            private boolean emailSent = false;

            @Override
            public void run() {
                if (retryCount >= maxRetries || emailSent) {
                    return;
                }
                try {
                    sendEmail(absender, empfaenger, betreff, text);
                    emailSent = true;
                    OpenWareInstance.getInstance().logMail("Mail erfolgreich erneut gesendet an " + empfaenger + " mit Betreff: " + betreff);
                } catch (Exception e) {
                    retryCount++;
                   
                    if (retryCount < maxRetries) {
                    	OpenWareInstance.getInstance().logMail("Fehler in Mail an " + empfaenger + " mit Betreff: "
                    		    + betreff + ": " + e.getMessage()
                    		    + "|| Erneuter Versuch in 10 Minuten... (" + retryCount + " von " + maxRetries + " Versuchen)");
                    } else {
                    	try {
	                    	if(!MailSender.this.mailDebugURL.isEmpty()) {
	                    		URL obj = new URL(MailSender.this.mailDebugURL);
	                            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
	                            connection.setRequestMethod("GET");
	                            connection.getResponseCode();
	                    	}
                    	} catch (Exception e1) {
                    		OpenWareInstance.getInstance().logError("Beim Ping des Mail Monitoring Servers ist ein Fehler aufgetreten", e1);
                    	}
                        OpenWareInstance.getInstance().logMail("--- Maximale Anzahl von Versuchen erreicht für " + empfaenger);
                    }
                }
            }
        };

        // Planen der Aufgabe
        executorService.scheduleAtFixedRate(emailTask, 0, 10, TimeUnit.MINUTES);
    }

    // Methode sendTempMail hinzugefügt
    public String sendTempMail(String absender, String mailNamer, String empfaenger, String betreff, String text,
            String anhangContentType, byte[] anhang, String anhangDateiName, String anhangBeschreibung)
            throws IOException, EmailException {

        // Überprüfe die Eingabeparameter
        if (absender == null || empfaenger == null || betreff == null || text == null) {
            throw new IllegalArgumentException("Einer der Parameter ist null.");
        }

        if (debug) {
            empfaenger = this.debugMail;
        }

        // Maximal 50 Empfänger pro Batch
        String[] recipients = empfaenger.split(";");
        int batchSize = 50;
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < recipients.length; i += batchSize) {
            String[] batch = Arrays.copyOfRange(recipients, i, Math.min(i + batchSize, recipients.length));
            for (String recipient : batch) {
                try {
                    sendEmailWithMailNameAndAttachment(absender, mailNamer, recipient, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                    OpenWareInstance.getInstance().logMail("Temporäre Mail erfolgreich gesendet an " + recipient);
                    res.append("Erfolg: ").append(recipient).append("\n");
                } catch (Exception e) {
                    OpenWareInstance.getInstance().logMail("Fehler beim Senden der temporären Mail an " + recipient + ": " + e.getMessage());
                    OpenWareInstance.getInstance().logError("Fehler in der Mail: ", e);

                    // Versuche die E-Mail asynchron erneut zu senden
                    sendEmailWithMailNameAndAttachmentAsync(absender, mailNamer, recipient, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                }
            }
        }
        return res.toString();
    }

    // Methode zum Senden einer E-Mail mit MailName und Anhang
    private void sendEmailWithMailNameAndAttachment(String absender, String mailNamer, String empfaenger,
            String betreff, String text, String anhangContentType, byte[] anhang, String anhangDateiName,
            String anhangBeschreibung) throws EmailException {

        HtmlEmail email = new HtmlEmail();
        // Setze Timeouts
        email.setSocketConnectionTimeout(10000);
        email.setSocketTimeout(10000);

        // Authentifizierung
        if (user != null && pw != null) {
            email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
            if(this.tlsActive) {
                email.setStartTLSEnabled(true);
            } else {
            	email.setSSL(true);
            }
            email.setSmtpPort(this.port);
        }
        email.setHostName(this.host);

        // Absender und MailName setzen
        if (absender == null) {
            absender = user;
        }
        if (mailNamer == null) {
            if (mailName == null) {
                mailNamer = absender;
            } else {
                mailNamer = mailName;
            }
        }

        email.setFrom(absender, mailNamer);
        email.addTo(empfaenger);
        email.setCharset(textCharSet);
        email.setSubject(betreff);
        email.setHtmlMsg(text);

        // Anhang hinzufügen
        if (anhang != null) {
            email.attach(new ByteArrayDataSource(anhang, anhangContentType), anhangDateiName, anhangBeschreibung,
                    EmailAttachment.ATTACHMENT);
        }

        OpenWareInstance.getInstance().logMail("Sende temporäre Mail an: " + empfaenger + " mit Betreff: " + betreff
                + " (" + this.host + ":" + this.port + ")");
        email.send();
    }

    // Asynchrone Methode zum erneuten Senden der temporären E-Mail bei Fehlern
    private void sendEmailWithMailNameAndAttachmentAsync(String absender, String mailNamer, String empfaenger,
            String betreff, String text, String anhangContentType, byte[] anhang, String anhangDateiName,
            String anhangBeschreibung) {

        Runnable emailTask = new Runnable() {
            private int retryCount = 0;
            private final int maxRetries = 5;
            private boolean emailSent = false;

            @Override
            public void run() {
                if (retryCount >= maxRetries || emailSent) {
                    return;
                }
                try {
                    sendEmailWithMailNameAndAttachment(absender, mailNamer, empfaenger, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                    emailSent = true;
                    OpenWareInstance.getInstance().logMail("Temporäre Mail erfolgreich erneut gesendet an " + empfaenger);
                } catch (Exception e) {
                    retryCount++;
                    OpenWareInstance.getInstance().logMail("Fehler in temporärer Mail an: " + empfaenger + " mit Betreff: "
                            + betreff + " (" + host + ":" + port + "): " + e.getMessage());
                    if (retryCount < maxRetries) {
                        OpenWareInstance.getInstance().logMail("Erneuter Versuch in 10 Minuten... (" + retryCount
                                + " von " + maxRetries + " Versuchen)");
                    } else {
                    	try {
	                    	if(!MailSender.this.mailDebugURL.isEmpty()) {
	                    		URL obj = new URL(MailSender.this.mailDebugURL);
	                            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
	                            connection.setRequestMethod("GET");
	                            connection.getResponseCode();
	                    	}
                    	} catch (Exception e1) {
                    		OpenWareInstance.getInstance().logError("Beim Ping des Mail Monitoring Servers ist ein Fehler aufgetreten", e1);
                    	}
                        OpenWareInstance.getInstance().logMail("--- Maximale Anzahl von Versuchen erreicht für " + empfaenger);
                    }
                }
            }
        };

        // Planen der Aufgabe
        executorService.scheduleAtFixedRate(emailTask, 0, 10, TimeUnit.MINUTES);
    }

    // Methode zum Versenden von E-Mails mit Anhang (byte[])
    public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
            byte[] anhang, String anhangDateiName, String anhangBeschreibung) throws IOException, EmailException {

        // Überprüfe die Eingabeparameter
        if (absender == null || empfaenger == null || betreff == null || text == null || anhang == null) {
            throw new IllegalArgumentException("Einer der Parameter ist null.");
        }

        if (debug) {
            empfaenger = this.debugMail;
        }

        // Maximal 50 Empfänger pro Batch
        String[] recipients = empfaenger.split(";");
        int batchSize = 50;
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < recipients.length; i += batchSize) {
            String[] batch = Arrays.copyOfRange(recipients, i, Math.min(i + batchSize, recipients.length));
            for (String recipient : batch) {
                try {
                    sendEmailWithAttachment(absender, recipient, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                    OpenWareInstance.getInstance().logMail("Mail mit Anhang erfolgreich gesendet an " + recipient);
                    res.append("Erfolg: ").append(recipient).append("\n");
                } catch (Exception e) {
                    OpenWareInstance.getInstance().logMail("Fehler beim Senden der Mail mit Anhang an " + recipient + ": " + e.getMessage());
                    OpenWareInstance.getInstance().logError("Fehler in der Mail: ", e);

                    // Versuche die E-Mail asynchron erneut zu senden
                    sendEmailWithAttachmentAsync(absender, recipient, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                }
            }
        }
        return res.toString();
    }

    // Methode zum Senden einer E-Mail mit Anhang
    private void sendEmailWithAttachment(String absender, String empfaenger, String betreff, String text,
            String anhangContentType, byte[] anhang, String anhangDateiName, String anhangBeschreibung) throws EmailException {

        HtmlEmail email = new HtmlEmail();
        // Setze Timeouts
        email.setSocketConnectionTimeout(10000);
        email.setSocketTimeout(10000);

        // Authentifizierung
        if (user != null && pw != null) {
            email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
            if(this.tlsActive) {
                email.setStartTLSEnabled(true);
            } else {
            	email.setSSL(true);
            }
            email.setSmtpPort(this.port);
        }
        email.setHostName(this.host);

        // Absender setzen
        if (absender == null) {
            absender = user;
        }
        email.setFrom(absender, this.mailName);
        email.addTo(empfaenger);
        email.setCharset(textCharSet);
        email.setSubject(betreff);
        email.setHtmlMsg(text);

        // Anhang hinzufügen
        if (anhang != null) {
            email.attach(new ByteArrayDataSource(anhang, anhangContentType), anhangDateiName, anhangBeschreibung,
                    EmailAttachment.ATTACHMENT);
        }

        OpenWareInstance.getInstance().logMail("Sende Mail an: " + empfaenger + " mit Betreff: " + betreff
                + " (" + this.host + ":" + this.port + ")");
        email.send();
    }

    // Asynchrone Methode zum erneuten Senden der E-Mail mit Anhang bei Fehlern
    private void sendEmailWithAttachmentAsync(String absender, String empfaenger, String betreff, String text,
            String anhangContentType, byte[] anhang, String anhangDateiName, String anhangBeschreibung) {

        Runnable emailTask = new Runnable() {
            private int retryCount = 0;
            private final int maxRetries = 5;
            private boolean emailSent = false;

            @Override
            public void run() {
                if (retryCount >= maxRetries || emailSent) {
                    return;
                }
                try {
                    sendEmailWithAttachment(absender, empfaenger, betreff, text, anhangContentType,
                            anhang, anhangDateiName, anhangBeschreibung);
                    emailSent = true;
                    OpenWareInstance.getInstance().logMail("Mail mit Anhang erfolgreich erneut gesendet an " + empfaenger);
                } catch (Exception e) {
                    retryCount++;
                    OpenWareInstance.getInstance().logMail("Fehler in Mail an: " + empfaenger + " mit Betreff: "
                            + betreff + " (" + host + ":" + port + "): " + e.getMessage());
                    if (retryCount < maxRetries) {
                        OpenWareInstance.getInstance().logMail("Erneuter Versuch in 10 Minuten... (" + retryCount
                                + " von " + maxRetries + " Versuchen)");
                    } else {
                    	try {
	                    	if(!MailSender.this.mailDebugURL.isEmpty()) {
	                    		URL obj = new URL(MailSender.this.mailDebugURL);
	                            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
	                            connection.setRequestMethod("GET");
	                            connection.getResponseCode();
	                    	}
                    	} catch (Exception e1) {
                    		OpenWareInstance.getInstance().logError("Beim Ping des Mail Monitoring Servers ist ein Fehler aufgetreten", e1);
                    	}
                        OpenWareInstance.getInstance().logMail("--- Maximale Anzahl von Versuchen erreicht für " + empfaenger);
                    }
                }
            }
        };

        // Planen der Aufgabe
        executorService.scheduleAtFixedRate(emailTask, 0, 10, TimeUnit.MINUTES);
    }

    // Methode zum Versenden von E-Mails mit Anhang (InputStream)
    public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
            InputStream anhangInputStream, String anhangDateiName, String anhangBeschreibung)
            throws IOException, EmailException {

        // InputStream in Byte-Array konvertieren
        byte[] anhang = anhangInputStream.readAllBytes();
        anhangInputStream.close();

        // Verwende die Methode mit Byte-Array-Anhang
        return sendMail(absender, empfaenger, betreff, text, anhangContentType,
                anhang, anhangDateiName, anhangBeschreibung);
    }

    // Prozessiert Aktionen aus dem System
    @Override
    public Object processAction(String target, String topic, String payload, User user, JSONObject options,
            List<OpenWareDataItem> optionalData, Object templateOptions) throws Exception {

        // Bestimme den Absender
        String absender = options.has("extra") && options.getJSONObject("extra").has("sender")
                ? options.getJSONObject("extra").getString("sender") : this.user;
        String betreff = topic;
        String text = payload;
        String empfaenger = debug ? this.debugMail : target;

        // Sende die E-Mail
        return sendMail(absender, empfaenger, betreff, text);
    }

    // Beenden des ExecutorService bei Bedarf
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}