package de.openinc.model.user;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class FeaturePermission {

	private List<FeaturePermissionEntry> whiteListFeatures;
	private List<FeaturePermissionEntry> blackListFeatures;

	public FeaturePermission() {
		this.whiteListFeatures = new ArrayList<FeaturePermissionEntry>();
		this.blackListFeatures = new ArrayList<FeaturePermissionEntry>();
	}

	public void addPermission(FeaturePermissionEntry permission) {
		if (permission	.getMode()
						.equals(FeaturePermissionEntry.FEATURE_MODE_ALLOW)) {

			whiteListFeatures.add(permission);
		} else {

			blackListFeatures.add(permission);

		}

	}

	public void addPermission(String permissionString) {
		FeaturePermissionEntry permission = FeaturePermissionEntry.parse(permissionString);
		addPermission(permission);
	}

	public void clearPermissions() {
		whiteListFeatures.clear();
		blackListFeatures.clear();
	}

	public boolean evaluateAccess(String method, String subject) {
		if (whiteListFeatures == null || whiteListFeatures.size() == 0)
			return false;
		boolean allowed = false;
		boolean blocked = false;

		for (FeaturePermissionEntry entry : whiteListFeatures) {
			if (entry.isGroup()) {
				if (method.matches(entry.getMethod()) && subject.matches(entry.getSubject())) {
					allowed = true;
					break;
				}
				;
			} else {
				if (method	.toLowerCase()
							.equals(entry	.getMethod()
											.toLowerCase())
						&& subject.matches(entry.getSubject())) {
					allowed = true;
					break;
				}
			}
		}

		for (FeaturePermissionEntry entry : blackListFeatures) {
			if (entry.isGroup()) {
				if (method.matches(entry.getMethod()) && subject.matches(entry.getSubject())) {
					blocked = true;
					break;
				}
				;
			} else {
				if (method	.toLowerCase()
							.equals(entry	.getMethod()
											.toLowerCase())
						&& subject.matches(entry.getSubject())) {
					blocked = true;
					break;
				}
			}
		}

		return allowed && !blocked;
	}

	public JSONObject toJSON() {
		JSONArray white = new JSONArray();
		JSONArray black = new JSONArray();
		for (FeaturePermissionEntry entry : whiteListFeatures) {
			white.put(entry.toString());
		}
		for (FeaturePermissionEntry entry : blackListFeatures) {
			black.put(entry.toString());
		}
		JSONObject perms = new JSONObject();
		perms.put("featuresAllowed", white);
		perms.put("featuresBlocked", black);
		return perms;

	}

	@Override
	public String toString() {
		return toJSON().toString(2);
	}

}
