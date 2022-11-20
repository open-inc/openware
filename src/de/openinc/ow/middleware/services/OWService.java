package de.openinc.ow.middleware.services;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.OWServiceActivator;
import de.openinc.ow.helper.Config;

public class OWService {
	public String id;
	public JSONObject options;
	public Object instance;
	public String className;
	public OWServiceActivator activator;

	public OWService(String id, Object service, OWServiceActivator activator) {
		this.id = id;
		this.className = service.getClass().getCanonicalName();
		this.instance = service;
		this.activator = activator;
		loadDefaultOptions();
	}

	private void loadDefaultOptions() {
		options = Config.readConfig(this.className);
	}

	public final Object load(JSONObject options) throws Exception {
		if (options != null) {
			this.options = options;
		}
		instance = activator.load(null, this.options);
		ServiceRegistry.getInstance().addService(this);
		return instance;
	}

	public final void unload() throws Exception {
		if (this.activator.unload()) {
			ServiceRegistry.getInstance().removeService(this.id);
		} else {
			throw new Exception("Could not unload Service");
		}
	}

	private JSONObject sanitizeJSONObject(JSONObject safeOptions) {
		Iterator<String> it = safeOptions.keys();
		while (it.hasNext()) {
			String key = it.next();
			if (key.toLowerCase().contains("password") || key.toLowerCase().contains("passwort")
					|| key.toLowerCase().contains("secret") || key.toLowerCase().contains("masterkey")
					|| key.toLowerCase().contains("passwort") || key.toLowerCase().equals("pw")) {
				it.remove();
			}
			try {
				JSONObject child = safeOptions.getJSONObject(key);
				safeOptions.put(key, sanitizeJSONObject(child));
			} catch (JSONException e) {
				// nothing to do
			}
			try {
				JSONArray childArray = safeOptions.getJSONArray(key);
				for (int i = 0; i < childArray.length(); i++) {
					try {
						JSONObject child = childArray.getJSONObject(i);
						childArray.put(i, sanitizeJSONObject(child));
					} catch (JSONException e) {
						continue;
						// nothing to do
					}
				}
				safeOptions.put(key, childArray);
			} catch (JSONException e) {
				// nothing to do
			}

		}

		return safeOptions;
	}

	public JSONObject toJSONObject() {
		JSONObject x = new JSONObject();
		x.put("id", this.id);
		JSONObject safeOptions = new JSONObject(this.options.toString());

		x.put("options", sanitizeJSONObject(safeOptions));
		x.put("class", this.className);
		return x;
	}

	public OWService fromJSON(JSONObject json) {
		this.id = json.getString("id");
		this.options = json.getJSONObject("options");
		this.className = json.getString("class");
		return this;
	}

	public boolean isDeactivated() {
		return ServiceRegistry.getInstance().isDeactived(this);
	}
}
