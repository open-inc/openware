package de.openinc.api;

import org.json.JSONObject;

public interface OWServiceActivator {

	public Object load(Object prevInstance, JSONObject options) throws Exception;

	public boolean unload() throws Exception;
}
