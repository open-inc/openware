package de.openinc.ow.middleware.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import de.openinc.api.UserAdapter;
import de.openinc.model.user.User;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;

public class UserService {

	private static UserService me;
	private UserAdapter adapter;
	private JWTVerifier jwtVerifier;
	private String issuer;
	private Algorithm algorithm;
	private HashMap<User, List<Session>> userSessions;

	private UserService() {
		me = this;
		this.userSessions = new HashMap<User, List<Session>>();
		try {
			issuer = Config.get("jwt_issuer", "");
			String secret = Config.get("jwt_secret", "");
			if (secret == null || issuer == null || issuer.equals("") || secret.equals("")) {
				throw new IllegalArgumentException("Missing JWT Config");
			}
			algorithm = Algorithm.HMAC256(secret);
			jwtVerifier = JWT	.require(algorithm)
								// .withIssuer(issuer)
								.build(); // Reusable verifier instance
		} catch (Exception e) {
			OpenWareInstance.getInstance()
							.logError(
									"JWT Configuration missing. Please Provide an Issuer (ENV JWT_ISSUER) and secret (ENV JWT_SECRET)");
			return;
		}
	}

	public static UserService getInstance() {
		if (me == null) {
			me = new UserService();

		}

		return me;
	}

	/**
	 * Removes the websocket session from the active user sessions.
	 * 
	 * @param session The session that should be removed
	 * @return True if the session was removed, false if it was not found
	 */
	public boolean removeUserSession(Session session) {
		HashMap<User, List<Session>> tempUserSession = new HashMap<>();
		tempUserSession.putAll(userSessions);
		for (User key : tempUserSession.keySet()) {
			if (tempUserSession	.getOrDefault(key, new ArrayList<Session>())
								.remove(session)) {
				userSessions = tempUserSession;
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds the websocket session from the active user sessions.
	 * 
	 * @param session The session that should be added
	 * @param user    The user who initiated the session
	 * 
	 */
	public void addUserSession(User user, Session session) {
		HashMap<User, List<Session>> tempUserSession = new HashMap<>();
		tempUserSession.putAll(userSessions);
		List<Session> uSessions = userSessions.getOrDefault(user, new ArrayList<Session>());
		uSessions.add(session);
		userSessions.put(user, uSessions);
	}

	public User jwtToUser(String token) {
		if (jwtVerifier == null)
			return null;
		try {
			DecodedJWT userJWT = jwtVerifier.verify(token);
			Claim userid = userJWT.getClaim("uid");
			User usr = null;

			Claim username = userJWT.getClaim("username");
			if (!username.isNull() && !username.isMissing())
				usr = getUserByUsername(username.asString());
			Claim usermail = userJWT.getClaim("usermail");
			if (!usermail.isNull() && !usermail.isMissing())
				usr = getActiveUsers()	.stream()
										.filter(new Predicate<User>() {
											@Override
											public boolean test(User t) {
												return t.getEmail()
														.toLowerCase()
														.equals(usermail.asString()
																		.toLowerCase());
											}
										})
										.findFirst()
										.get();
			if (!userid.isNull() && !userid.isMissing())
				usr = getUserByUID(userid.asString());
			if (usr != null) {
				adapter.refreshPermissions(usr);
			}
			return usr;
		} catch (JWTVerificationException e) {
			return null;
		}

	}

	public String userToJWT(String id) {
		if (jwtVerifier == null)
			return null;
		User user = getUserByUID(id);
		if (user == null)
			return null;
		String token = JWT	.create()
							.withClaim("uid", user.getUID())
							.withClaim("username", user.getName())
							.withClaim("usermail", user	.getEmail()
														.toLowerCase())
							.sign(algorithm);
		return token;
	}

	public UserAdapter getAdapter() {
		return adapter;
	}

	public User checkAuth(String session) {
		if (adapter == null)
			return null;
		return adapter.checkAuth(session);
	}

	public User login(String user, String pw) {
		if (adapter == null)
			return null;
		return adapter.login(user, pw);
	}

	public boolean storeData(String session, JSONObject data) {
		if (adapter == null)
			return false;
		return adapter.storeOptionalData(session, data);
	}

	public JSONObject readData(String session) {
		if (adapter == null)
			return null;
		return adapter.readOptionalData(session);
	}

	public boolean logout(String session) {
		if (adapter == null)
			return false;
		return adapter.logout(session);
	}

	public boolean resetPW(String session) {
		if (adapter == null)
			return false;
		return adapter.resetPW(session);
	}

	public void setAdapter(UserAdapter adap) {
		this.adapter = adap;
		this.adapter.init();
	}

	public List<User> getActiveUsers() {
		return adapter.getAllUsers();
	}

	public User getUserByUsername(String name) {
		for (User user : getActiveUsers()) {
			if (user.getName()
					.equals(name))
				return user;
		}
		return null;
	}

	public User getUserByUID(String uid) {
		for (User user : getActiveUsers()) {
			if (user.getUID()
					.equals(uid))
				return user;
		}
		return null;
	}

	public boolean notifyActiveUser(User user, JSONObject payload) {
		List<Session> sessions = userSessions.get(user);
		if (sessions == null || sessions.size() == 0)
			return false;
		for (Session cSession : sessions) {
			JSONObject msg = new JSONObject();
			msg.put("type", "notification");
			msg.put("payload", payload);
			try {
				cSession.getRemote()
						.sendString(msg.toString());
			} catch (IOException e) {
				OpenWareInstance.getInstance()
								.logError("Could not notify user via websocket", e);
				return false;
			}
		}
		return true;
	}

	public void sendNotification(User user, JSONObject payload) {
		adapter.sendPush(user, payload);
	}

	public void refreshUsers() {
		adapter.refreshUsers();
	}
}
