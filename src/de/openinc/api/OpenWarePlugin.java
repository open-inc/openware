package de.openinc.api;

import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;

public interface OpenWarePlugin {

	public boolean init(OpenWareInstance instance, JSONObject options) throws Exception;

	public boolean disable() throws Exception;

}
