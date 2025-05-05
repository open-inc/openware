package de.openinc.model.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class AccessPermission {

	private HashMap<String, List<PermissionSet>> sources;
	private HashMap<String, List<PermissionSet>> regExpGroups;

	public AccessPermission() {
		this.sources = new HashMap<>();
		this.regExpGroups = new HashMap<>();
	}

	public void addPermission(String owner, PermissionSet permission, boolean group) {
		if (group) {
			String regex = owner;
			List<PermissionSet> list = regExpGroups.getOrDefault(owner, new ArrayList<PermissionSet>());
			if (regExpGroups.containsKey(regex) && list.contains(permission))
				return;
			list.add(permission);
			regExpGroups.put(regex, list);
		} else {
			List<PermissionSet> list = sources.getOrDefault(owner, new ArrayList<PermissionSet>());
			if (sources.containsKey(owner) && list.contains(permission))
				return;
			list.add(permission);
			sources.put(owner, list);
		}

	}

	public void changeSourceOfPermission(String oldOwner, String newOWner) {
		List toChange = sources.get(oldOwner);
		sources.remove(oldOwner);
		sources.put(newOWner, toChange);
	}

	public void clearPermissions() {
		this.sources = new HashMap<>();
	}

	public void clearPermissionsForOwner(String owner) {
		this.sources.put(owner, new ArrayList<PermissionSet>());
	}

	public boolean evaluateRead(String owner, String sensor) {
		for (PermissionSet permission : sources.getOrDefault(owner, new ArrayList<>())) {
			if (permission.evaluateRead(sensor))
				return true;
		}
		for (String sourceExpression : regExpGroups.keySet()) {
			if (owner.matches(sourceExpression)) {
				for (PermissionSet permission : regExpGroups.getOrDefault(sourceExpression, new ArrayList<>())) {
					if (permission.evaluateRead(sensor))
						return true;
				}
			}
		}

		return false;
	}

	public boolean evaluateWrite(String owner, String sensor) {
		for (PermissionSet permission : sources.getOrDefault(owner, new ArrayList<>())) {
			if (permission.evaluateWrite(sensor))
				return true;
		}
		for (String sourceExpression : regExpGroups.keySet()) {
			if (owner.matches(sourceExpression)) {
				for (PermissionSet permission : regExpGroups.getOrDefault(sourceExpression, new ArrayList<>())) {
					if (permission.evaluateWrite(sensor))
						return true;
				}
			}
		}
		return false;
	}

	public boolean evaluateDelete(String owner, String sensor) {
		for (PermissionSet permission : sources.getOrDefault(owner, new ArrayList<>())) {
			if (permission.evaluateDelete(sensor))
				return true;
		}
		for (String sourceExpression : regExpGroups.keySet()) {
			if (owner.matches(sourceExpression)) {
				for (PermissionSet permission : regExpGroups.getOrDefault(sourceExpression, new ArrayList<>())) {
					if (permission.evaluateDelete(sensor))
						return true;
				}
			}
		}
		return false;
	}

	public boolean serviceAccess(String owner, String type) {
		return sources.get(owner) != null;
	}

	public JSONArray toJSONArray() {
		JSONArray res_owners = new JSONArray();
		for (String source : this.sources.keySet()) {
			JSONObject cOwner = new JSONObject();
			cOwner.put("source", source);
			JSONArray sourcePermissions = new JSONArray();
			List<PermissionSet> list = this.sources.get(source);
			for (PermissionSet perms : list) {
				sourcePermissions.put(perms.toString());
			}
			cOwner.put("permissions", sourcePermissions);
			res_owners.put(cOwner);
		}
		return res_owners;

	}

	@Override
	public String toString() {

		return toJSONArray().toString(2);
	}

}