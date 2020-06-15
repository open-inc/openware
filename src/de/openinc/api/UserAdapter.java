package de.openinc.api;

import java.util.List;

import org.json.JSONObject;

import de.openinc.ow.core.model.user.User;

public interface UserAdapter {
	public void init();

	public User checkAuth(String session);

	public User login(String user, String pw);

	public boolean storeData(String session, String key, String value);

	public String readData(String session, String key);

	public boolean logout(String session);

	public boolean resetPW(String session);

	public void refreshUsers();

	public List<User> getAllUsers();

	public void sendPush(User user, JSONObject payload);

}
