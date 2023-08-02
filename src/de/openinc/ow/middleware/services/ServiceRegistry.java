package de.openinc.ow.middleware.services;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;

public class ServiceRegistry {
	private HashMap<String, OWService> activeServices;
	private HashMap<String, OWService> inActiveServices;
	private static ServiceRegistry me;
	private static JSONObject state;

	private ServiceRegistry() {
		activeServices = new HashMap<String, OWService>();
		inActiveServices = new HashMap<String, OWService>();
	}

	public static ServiceRegistry getInstance() {

		if (me == null) {
			try {
				String path = "state.json";
				state = new JSONObject(new String(Files.readAllBytes(Paths.get(path))));
			} catch (Exception e) {
				state = new JSONObject();
				state.put("inactiveServices", new JSONArray());
				OpenWareInstance.getInstance()
						.logWarn("No current state found for instance! All Plugins will be active");
			}
			me = new ServiceRegistry();
		}
		return me;
	}

	public List<OWService> getActiveServices() {
		ArrayList<OWService> services = new ArrayList<OWService>();
		services.addAll(activeServices.values());
		return services;
	}

	public List<OWService> getInactiveServices() {
		ArrayList<OWService> services = new ArrayList<OWService>();
		services.addAll(inActiveServices.values());
		return services;
	}

	public boolean isDeactived(OWService service) {
		return inActiveServices.containsKey(service.id);
	}

	public OWService getService(String id) {
		OWService candidate = activeServices.get(id);
		if (candidate == null) {
			candidate = inActiveServices.get(id);
		}
		return candidate;
	}

	protected OWService addService(OWService service) {
		this.inActiveServices.remove(service.id);
		updateInactiveServiceState();
		return this.activeServices.put(service.id, service);
	}

	protected OWService removeService(String id) {
		OWService removed = this.activeServices.remove(id);
		if (removed != null) {
			this.inActiveServices.put(id, removed);
			updateInactiveServiceState();
		}
		return removed;

	}

	public boolean updateInactiveServiceState() {
		BufferedWriter writer;
		JSONArray iaservices = new JSONArray();
		for (OWService service : inActiveServices.values()) {
			iaservices.put(service.toJSONObject());
		}
		state.put("inactiveServices", iaservices);
		try {
			writer = new BufferedWriter(new FileWriter("state.json"));
			writer.write(state.toString(2));
			writer.flush();
			writer.close();
			return true;
		} catch (IOException e) {
			OpenWareInstance.getInstance().logError("Could not persist state", e);
			return false;
		}
	}

}