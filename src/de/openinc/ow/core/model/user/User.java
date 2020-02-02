package de.openinc.ow.core.model.user;

import java.util.HashSet;

import org.json.JSONObject;

public class User {

	private String name;
	private String email;
	private String session;
	private String data;
	private HashSet<Role> roles;
	private boolean selfPermission;
	private boolean allAccess;
	private AccessPermission permissions;
	private String uid;

	public User(String name, String email, String session, String data, boolean selfPermission, boolean allAccess) {
		this.name = name;
		this.email = email;
		this.session = session;
		this.data = data;
		this.roles = new HashSet<>();
		this.selfPermission = selfPermission;
		this.permissions = new AccessPermission();
		initPermissions();
	}

	public HashSet<Role> getRoles() {
		return roles;
	}

	public void setRoles(HashSet<Role> roles) {
		this.roles = roles;
	}

	public User(String name, String email, String session, String data) {
		this(name, email, session, data, true, false);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (selfPermission) {
			permissions.changeOwnerOfPermission(getName(), name);
		}
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSession() {
		return session;
	}

	public void setSession(String session) {
		this.session = session;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public boolean canAccessRead(String owner, String sensor) {
		if (sensor == null) {
			permissions.serviceAccess(owner, "read");
		}
		return permissions.evaluateRead(owner, sensor);
	}

	public boolean canAccessWrite(String owner, String sensor) {
		if (sensor == null) {
			permissions.serviceAccess(owner, "write");
		}
		return permissions.evaluateWrite(owner, sensor);
	}

	public boolean canAccessDelete(String owner, String sensor) {
		if (sensor == null) {
			permissions.serviceAccess(owner, "delete");
		}
		return permissions.evaluateDelete(owner, sensor);
	}

	public void clearPermissions() {
		permissions = new AccessPermission();
		initPermissions();

	}

	private void initPermissions() {
		if (selfPermission)
			permissions.addPermission(name, new PermissionSet(".*", ".*", ".*"));
	}

	@Override
	public String toString() {
		JSONObject res = new JSONObject();
		return this.toJSON().toString(2);
	}

	public JSONObject toJSON() {
		JSONObject res = new JSONObject();
		res.put("name", this.name);
		res.put("session", this.session);
		res.put("data", this.data);
		res.put("email", this.email);
		res.put("roles", roles);
		res.put("permissions", permissions);
		return res;
	}

	public AccessPermission getPermissions() {
		return permissions;
	}

	public void setPermissions(AccessPermission permissions) {
		this.permissions = permissions;
	}

	public String getUID() {
		return uid;
	}

	public void setUID(String uid) {
		this.uid = uid;
	}

	public boolean addRole(Role role) {
		return this.roles.add(role);
	}

	public boolean removeRole(Role role) {
		return this.roles.remove(role);
	}

	public boolean isSelfPermission() {
		return selfPermission;
	}

	public void setSelfPermission(boolean selfPermission) {
		this.selfPermission = selfPermission;
	}

	public boolean isAllAccess() {
		return allAccess;
	}

	public void setAllAccess(boolean allAccess) {
		this.allAccess = allAccess;
	}

}
