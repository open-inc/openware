package de.openinc.ow.middleware.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.commons.mail.HtmlEmail;
import org.json.JSONObject;

import de.openinc.api.ActuatorAdapter;
import de.openinc.api.UserAdapter;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;
import de.openinc.ow.middleware.services.DataService;
import de.openinc.ow.middleware.services.UserService;

/**
 * @author marti
 *
 */
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

	public static MailSender getInstance() {
		return (MailSender) DataService.getActuator(type);
	}

	public MailSender() {
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text)
			throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		String res = "";
		if(debug) {
			empfaenger = this.debugMail;
		}
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		if(absender == null) {
			absender = user;
		}
		email.setFrom(absender);
		String[] recipients = empfaenger.split(";");
		for (int i = 0; i < recipients.length; i++) {
			email.addTo(recipients[i]);
		}

		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);
		
		OpenWareInstance.getInstance().logMail("Sending Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+")");
		try {
			res = email.send();
			OpenWareInstance.getInstance().logMail("Mail successfully sent!");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logMail("Error in Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+"): "+ e);
		}
		
		return res;
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
			InputStream anhangInputStream, String anhangDateiName, String anhangBeschreibung)
			throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		String res = "";
		if(debug) {
			empfaenger = this.debugMail;
		}
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		email.setHostName(this.host);
		if(absender == null) {
			absender = user;
		}
		email.setFrom(absender);
		String[] recipients = empfaenger.split(";");
		for (int i = 0; i < recipients.length; i++) {
			email.addTo(recipients[i]);
		}

		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);
		if (anhangInputStream != null) {
			email.attach(new ByteArrayDataSource(anhangInputStream, anhangContentType), anhangDateiName,
					anhangBeschreibung, EmailAttachment.ATTACHMENT);
		}

		OpenWareInstance.getInstance().logMail("Sending Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+")");
		try {
			res = email.send();
			OpenWareInstance.getInstance().logMail("Mail successfully sent!");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logMail("Error in Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+"): "+ e);
		}
		
		return res;
	}

	public String sendMail(String absender, String empfaenger, String betreff, String text, String anhangContentType,
			byte[] anhang, String anhangDateiName, String anhangBeschreibung) throws IOException, EmailException {
		MultiPartEmail email = new MultiPartEmail();
		String res = "";
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		if (debug) {
			empfaenger = this.debugMail;
		}
		email.setHostName(this.host);
		if(absender == null) {
			absender = user;
		}
		email.setFrom(absender);
		String[] recipients = empfaenger.split(";");
		for (int i = 0; i < recipients.length; i++) {
			email.addTo(recipients[i]);
		}

		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setMsg(text);
		if (anhang != null) {
			email.attach(new ByteArrayDataSource(anhang, anhangContentType), anhangDateiName, anhangBeschreibung,
					EmailAttachment.ATTACHMENT);
		}

		OpenWareInstance.getInstance().logMail("Sending Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+")");
		try {
			res = email.send();
			OpenWareInstance.getInstance().logMail("Mail successfully sent!");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logMail("Error in Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+"): "+ e);
		}
		
		return res;
	}
	
	public String sendTempMail(String absender, String mailNamer, String empfaenger, String betreff, String text, String anhangContentType,
			byte[] anhang, String anhangDateiName, String anhangBeschreibung) throws IOException, EmailException {
		HtmlEmail email = new HtmlEmail();
		String res = "";
		if (user != null && pw != null) {
			email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
			email.setSSL(true);
			email.setSmtpPort(this.port);
		}
		if (debug) {
			empfaenger = this.debugMail;
		}
		email.setHostName(this.host);
		if(absender == null) {
			absender = user;
		}
		if(mailNamer == null) {
			if(mailName == null) {
				mailNamer = absender;
			} else {
				mailNamer = mailName;
			}
		}
		
		email.setFrom(absender, mailNamer);
		String[] recipients = empfaenger.split(";");
		for (int i = 0; i < recipients.length; i++) {
			email.addTo(recipients[i]);
		}

		email.setCharset(textCharSet);
		email.setSubject(betreff);
		email.setHtmlMsg(text + "<br />");
		if (anhang != null) {
			email.attach(new ByteArrayDataSource(anhang, anhangContentType), anhangDateiName, anhangBeschreibung,
					EmailAttachment.ATTACHMENT);
		}

		OpenWareInstance.getInstance().logMail("Sending Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+")");
		try {
			res = email.send();
			OpenWareInstance.getInstance().logMail("Mail successfully sent!");
		} catch (Exception e) {
			OpenWareInstance.getInstance().logMail("Error in Mail to: " + empfaenger + " with Subject: " + betreff + " ("+this.host+":"+this.port+"): "+ e);
		}
		return res;
	}

	@Override
	public Object processAction(String target, String topic, String payload, User user, JSONObject options,
			List<OpenWareDataItem> optionalData, Object templateOptions) throws Exception {
		
		if(payload.contains("<html>")) {
			HtmlEmail email = new HtmlEmail();
			String res = "";
			if (user != null && pw != null) {
				email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
				email.setSSL(true);
				email.setSmtpPort(this.port);
			}
			email.setHostName(this.host);
			
			String abesender = "noreply@openinc.de";
			if(this.mailName != null && this.mailName != "") {
				abesender = this.mailName;
			} 
			
			email.setFrom(abesender);
			if (options.has("extra")) {
				String from = options.getJSONObject("extra").optString("sender");
				if (!from.equals("")) {
					email.setFrom(from);
				}

			}

			if (debug) {
				target = this.debugMail;
			}
			String[] recipients = target.split(";");

			for (int i = 0; i < recipients.length; i++) {
				email.addTo(recipients[i]);
			}

			email.setCharset(textCharSet);
			email.setSubject(topic);
			
			email.setMsg(payload);

			OpenWareInstance.getInstance().logMail("Sending Mail to: " + target + " with Subject: " + topic + " ("+this.host+":"+this.port+")");
			try {
				res = email.send();
				OpenWareInstance.getInstance().logMail("Mail successfully sent!");
			} catch (Exception e) {
				OpenWareInstance.getInstance().logMail("Error in Mail to: " + target + " with Subject: " + topic + " ("+this.host+":"+this.port+"): "+ e);
			}
			return res;
		} else {
			MultiPartEmail email = new MultiPartEmail();
			String res = "";
			if (user != null && pw != null) {
				email.setAuthenticator(new DefaultAuthenticator(this.user, this.pw));
				email.setSSL(true);
				email.setSmtpPort(this.port);
			}
			email.setHostName(this.host);
			String abesender = "noreply@openinc.de";
			if(this.mailName != null && this.mailName != "") {
				abesender = this.mailName;
			} 
			
			email.setFrom(abesender);
			if (options.has("extra")) {
				String from = options.getJSONObject("extra").optString("sender");
				if (!from.equals("")) {
					email.setFrom(from);
				}

			}

			if (debug) {
				target = this.debugMail;
			}
			String[] recipients = target.split(";");

			for (int i = 0; i < recipients.length; i++) {
				email.addTo(recipients[i]);
			}

			email.setCharset(textCharSet);
			email.setSubject(topic);
			
			email.setMsg(payload);

			OpenWareInstance.getInstance().logMail("Sending Mail to: " + target + " with Subject: " + topic + " ("+this.host+":"+this.port+")");
			try {
				res = email.send();
				OpenWareInstance.getInstance().logMail("Mail successfully sent!");
			} catch (Exception e) {
				OpenWareInstance.getInstance().logMail("Error in Mail to: " + target + " with Subject: " + topic + " ("+this.host+":"+this.port+"): "+ e);
			}
			return res;
		}
	}

	/**
	 * Type of MailSender Actuator
	 * 
	 * @return Will return {@code "mail"}
	 */
	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return type;
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
		this.mailName = options.optString("mailname").equals("") ? null : options.optString("mailname");
		this.debug = options.getBoolean("debug");
		this.debugMail = options.optString("debugMail");
	}
}
