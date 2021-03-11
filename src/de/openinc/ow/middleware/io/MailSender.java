package de.openinc.ow.middleware.io;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.user.User;
import de.openinc.ow.middleware.services.DataService;

/**
 * @author marti
 *
 */
public class MailSender extends ActuatorAdapter {

	private String host;
	private int port;
	private String user;
	private String pw;
	private String textCharSet = "UTF-8";

	public static MailSender getInstance(String outboundMailServer, int port, String user, String pw) {
		return (MailSender) DataService.getActuator("mail");
	}

	public MailSender() {
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

	@Override
	public Object processAction(String target, String topic, String payload, User user, JSONObject options,
			OpenWareDataItem optionalData, Object templateOptions) throws Exception {
		MultiPartEmail email = new MultiPartEmail();
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		email.setFrom("noreply@openinc.de");
		if (options.has("extra")) {
			String from = options.getJSONObject("extra").optString("sender");
			if (!from.equals("")) {
				email.setFrom(from);
			}

		}

		String[] recipients = target.split(";");

		for (int i = 0; i < recipients.length; i++) {
			email.addTo(recipients[i]);
		}

		email.setCharset(textCharSet);
		email.setSubject(topic);
		email.setMsg(payload);

		return email.send();
	}

	/**
	 * Type of MailSender Actuator
	 * 
	 * @return Will return {@code "mail"}
	 */
	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return "email";
	}

	/**
	 * Initializes the Mailsender with Options
	 * 
	 * @param options
	 *            {@link JSONObject} containing at least keys
	 *            {@code outboundMailServer, port} and optionally
	 *            {@code user, password}
	 */
	@Override
	public void init(JSONObject options) throws Exception {
		this.host = options.getString("outboundMailServer");
		this.port = options.getInt("port");
		this.user = options.optString("user").equals("") ? null : options.optString("user");
		this.pw = options.optString("password").equals("") ? null : options.optString("password");

	}
}
