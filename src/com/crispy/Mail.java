package com.crispy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.json.JSONObject;

/**
 * Mail component.
 * 
 * @author harsh
 */
public class Mail implements Runnable {
	private static Mail INSTANCE = new Mail();
	private Properties props;
	private String username;
	private String password;
	private ScheduledExecutorService background;
	private ConcurrentHashMap<String, LinkedBlockingQueue<String>> queue;

	public Mail() {
	}

	public void start(String host, String username, String password) {
		props = new Properties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.auth", "true");

		this.username = username;
		this.password = password;

		background = Executors.newSingleThreadScheduledExecutor();
		queue = new ConcurrentHashMap<String, LinkedBlockingQueue<String>>();
		background.scheduleAtFixedRate(this, 5, 5, TimeUnit.MINUTES);
	}

	public static Mail getInstance() {
		return INSTANCE;
	}

	public void sendMail(String to, String subject, String body) {
		LinkedBlockingQueue<String> q = queue.get(to);
		if (q == null) {
			q = new LinkedBlockingQueue<String>();
			queue.put(to, q);
		}
		try {
			q.add(new JSONObject().put("to", to).put("subject", subject)
					.put("body", body).toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			
			Authenticator auth = new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username,
							password);
				}
			};
			
			Session session = Session.getDefaultInstance(props, auth);
			
			for (Map.Entry<String, LinkedBlockingQueue<String>> entry : queue
					.entrySet()) {
				String to = entry.getKey();
				LinkedBlockingQueue<String> q = entry.getValue();
				
				ArrayList<String> t = new ArrayList<String>();
				q.drainTo(t);

				HashMap<String, StringBuilder> bySubjects = new HashMap<String, StringBuilder>();
				
				for (String mail : t) {
					JSONObject o = new JSONObject(mail);
					StringBuilder body = bySubjects.get(o.getString("subject"));
					if (body == null) {
						body = new StringBuilder();
						bySubjects.put(o.getString("subject"), body);
					}
					body.append("\n\n");
					body.append(o.getString("body"));
				}
					
				for (Map.Entry<String, StringBuilder> entry2 : bySubjects.entrySet()) {
					MimeMessage message = new MimeMessage(session);
					message.setFrom(new InternetAddress("harsh@zopte.com"));
					message.addRecipient(Message.RecipientType.TO,
							new InternetAddress(to));
					message.setSubject(entry2.getKey());
					message.setText(entry2.getValue().toString());
					Transport.send(message);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public void shutdown() {
		if (background != null) {
			background.shutdownNow();
		}
	}

}
