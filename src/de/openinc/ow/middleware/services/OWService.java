package de.openinc.ow.middleware.services;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;

import de.openinc.api.OWServiceActivator;
import de.openinc.ow.OpenWareInstance;

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
		try {
			String path = "conf" + File.separator +
							this.className +
							".json";
			options = new JSONObject(
					new String(Files.readAllBytes(Paths.get(path))));
		} catch (Exception e) {
			options = new JSONObject();
			OpenWareInstance.getInstance().logWarn("No options provided for " + this.className);
		}

	}

	public final Object load(JSONObject options)
			throws Exception {
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

	public JSONObject toJSONObject() {
		JSONObject x = new JSONObject();
		x.put("id", this.id);
		x.put("options", this.options);
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
