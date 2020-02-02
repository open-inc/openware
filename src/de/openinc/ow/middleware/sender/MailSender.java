package de.openinc.ow.middleware.sender;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;

public class MailSender {

	private String host;
	private int port;
	private String user;
	private String pw;
	private String textCharSet = "UTF-8";
	private static MailSender singleton;

	public static MailSender getInstance(String outboundMailServer, int port, String user, String pw) {
		if (singleton == null) {
			singleton = new MailSender(outboundMailServer, port, user, pw);
		}
		return singleton;
	}

	private MailSender(String outboundMailServer, int port, String user, String pw) {
		this.host = outboundMailServer;
		this.port = port;
		this.user = user;
		this.pw = pw;
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text)
			throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		email.setFrom(absender);
		email.addTo(empfaenger);
		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);

		return email.send();
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
			InputStream anhangInputStream, String anhangDateiName, String anhangBeschreibung)
			throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		email.setFrom(absender);
		email.addTo(empfaenger);
		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);
		if (anhangInputStream != null) {
			email.attach(new ByteArrayDataSource(anhangInputStream, anhangContentType), anhangDateiName,
					anhangBeschreibung, EmailAttachment.ATTACHMENT);
		}

		return email.send();
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
			byte[] anhang, String anhangDateiName, String anhangBeschreibung) throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		email.setFrom(absender);
		email.addTo(empfaenger);
		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);
		if (anhang != null) {
			email.attach(new ByteArrayDataSource(anhang, anhangContentType), anhangDateiName, anhangBeschreibung,
					EmailAttachment.ATTACHMENT);
		}

		return email.send();
	}
}
