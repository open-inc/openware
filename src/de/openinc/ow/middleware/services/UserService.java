package de.openinc.ow.middleware.services;

import java.util.List;

import org.json.JSONObject;

import de.openinc.api.UserAdapter;
import de.openinc.ow.core.model.user.User;

public class UserService {

	private static UserService me;
	private UserAdapter adapter;

	private UserService() {
		me = this;
	}

	public static UserService getInstance() {
		if (me == null) {
			me = new UserService();

		}

		return me;
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

	public boolean storeData(String session, String key, String value) {
		if (adapter == null)
			return false;
		return adapter.storeData(session, key, value);
	}

	public String readData(String session, String key) {
		if (adapter == null)
			return null;
		return adapter.readData(session, key);
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

	public User getUser(String name) {
		for (User user : getActiveUsers()) {
			if (user.getName().equals(name))
				return user;
		}
		return null;
	}

	public void sendNotification(User user, JSONObject payload) {
		adapter.sendPush(user, payload);
	}

	public void refreshUsers() {
		adapter.refreshUsers();
	}
}
