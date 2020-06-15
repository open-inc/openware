package de.openinc.ow.core.analytics.SensorProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import de.openinc.api.AnalyticSensorProvider;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValueDimension;

public class ParseAnalyticSensorProvider implements AnalyticSensorProvider {
	private Properties properties;
	private String HOST;
	private String user;
	private String pw;
	private String adminSession;
	private HashMap<String, String> headers;
	private String appid;
	HashMap<String, Map<String, OpenWareDataItem>> items;

	public ParseAnalyticSensorProvider() throws FileNotFoundException {
		this.items = new HashMap<>();
		try {
			this.properties = new Properties();

			OpenWareInstance.getInstance().logInfo("Reading Config for Parse-Analytics-Apapter");
			properties.load(new FileInputStream("parse.properties"));
			this.HOST = properties.getProperty("host", null);
			this.user = properties.getProperty("user", null);
			this.pw = properties.getProperty("password", null);
			this.appid = properties.getProperty("applicationID", null);
			if (HOST == null || user == null || pw == null || appid == null) {
				throw new IllegalArgumentException(
						"Parse Property file incorrect. Please provide a host and credentials");
			}
			try {
				Files.lines(FileSystems.getDefault().getPath("adminSession")).forEach(line -> {
					String session = line;
					if (session.startsWith("r:")) {
						try {
							HttpResponse<JsonNode> res;
							res = Unirest.get(HOST + "/users/me").header("X-Parse-Application-Id", appid)
									.header("X-Parse-Session-Token", session).asJson();
							if (res.getStatus() == 200)
								this.adminSession = session;
						} catch (UnirestException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});
			} catch (IOException e1) {
				OpenWareInstance.getInstance().logInfo("No existing session found! Trying to login as Admin!");
			}
			if (this.adminSession == null) {
				OpenWareInstance.getInstance().logInfo("Logging into Admin User to access Data!");
				HttpResponse<JsonNode> res = Unirest.get(HOST + "/login").header("X-Parse-Application-Id", "1234567890")
						.header("X-Parse-Revocable-Session", "1").queryString("username", user)
						.queryString("password", pw).asJson();
				JSONObject response = res.getBody().getObject();
				OpenWareInstance.getInstance().logInfo("Successfully logged in:\n" + response.toString(2));

				this.adminSession = response.getString("sessionToken");
			}

			this.headers = new HashMap<>();
			headers.put("X-Parse-Application-Id", appid);
			headers.put("X-Parse-Session-Token", this.adminSession);

		} catch (IOException e) {
			OpenWareInstance.getInstance().logError("Parse-Analytic-Config file not found. " + e.getMessage());
			OpenWareInstance.getInstance()
					.logError("You should provide a parse.properties to configure the connection to your backend");
			throw new FileNotFoundException(
					"You should provide a parse.properties to configure the connection to your backend at the following location:" +
											new File("parse.properties").getAbsolutePath());
		} catch (UnirestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, Map<String, OpenWareDataItem>> getAnalyticSensors() {
		items = new HashMap<>();
		try {
			HttpResponse<JsonNode> res = Unirest.get(HOST + "/classes/" +
														Config.analyticSensors)
					.headers(headers)
					.asJson();
			JSONObject response = res.getBody().getObject();
			JSONArray results = response.getJSONArray("results");
			if (results != null && results.length() > 0) {
				for (int i = 0; i < results.length(); i++) {
					JSONObject o = results.getJSONObject(i);
					final JSONObject obj = new JSONObject();
					obj.put("id", o.getString("sensorid"));
					JSONObject meta = new JSONObject(o.getString("parameter"));
					meta.put("operation", o.getString("operation"));
					meta.put("POID", o.getString("objectId"));
					obj.put("meta", meta);
					obj.put("name", o.getString("name"));
					obj.put("user", o.getString("user"));
					obj.put("valueTypes", new JSONArray(o.getString("valueTypes")));

					String prefix = Config.analyticPrefix;
					if (obj.getString("id").startsWith(prefix)) {
						prefix = "";
					}
					ArrayList<OpenWareValueDimension> dims = new ArrayList<OpenWareValueDimension>();
					JSONArray valueTypes = obj.getJSONArray("valueTypes");
					for (int x = 0; x < valueTypes.length(); x++) {
						JSONObject item = valueTypes.getJSONObject(x);
						try {
							dims.add(OpenWareValueDimension.createNewDimension(((JSONObject) item).getString("name"),
									((JSONObject) item).optString("unit"), ((JSONObject) item).getString("type")));
						} catch (JSONException e) {
							OpenWareInstance.getInstance().logError("Could not create Valuetype for analytic sensor",
									e);
							e.printStackTrace();
						}

					}

					OpenWareDataItem owdi = new OpenWareDataItem(prefix + obj.getString("id"), obj.getString("user"),
							obj.getString("name"), obj.getJSONObject("meta"), dims);
					Map<String, OpenWareDataItem> userItems = items.getOrDefault(owdi.getUser(),
							new HashMap<String, OpenWareDataItem>());
					userItems.put(owdi.getId(), owdi);
					items.put(owdi.getUser(), userItems);
				}

			}
			return items;

		} catch (UnirestException e) {
			OpenWareInstance.getInstance()
					.logError("Could not retrieve AnalyticsSensors due to Parse-Server Error\n" + e.getMessage(), e);
			return items;
		}
	}

	@Override
	public boolean deleteAnalyticSensor(String user, String sensorid) {
		Map<String, OpenWareDataItem> userItems = items.get(user);
		if (userItems != null && userItems.size() > 0) {
			OpenWareDataItem item = userItems.get(sensorid);
			if (item == null)
				return true;
			try {
				HttpResponse<JsonNode> res = Unirest
						.delete(HOST + "/classes/" +
								Config.analyticSensors +
								"/" +
								item.getMeta().optString("POID"))
						.headers(headers).asJson();
				if (res.getStatus() == 200)
					return true;
				return false;
			} catch (Exception e) {
				OpenWareInstance.getInstance().logError("Could not delete sensor " + sensorid +
														" (" +
														user +
														")",
						e);
				return false;
			}
		}
		return false;
	}

	@Override
	public boolean addAnalyticSensor(JSONObject parameter) {
		JSONObject obj = new JSONObject();
		obj.put("valueTypes", parameter.getString("valueTypes"));
		obj.put("name", parameter.getString("name"));
		obj.put("parameter", parameter.getString("parameter"));
		obj.put("user", parameter.getString("user"));
		obj.put("sensorid", parameter.getString("sensorid"));
		obj.put("operation", parameter.getString("operation"));
		try {
			HttpResponse<JsonNode> res = Unirest.post(HOST + "/classes/" +
														Config.analyticSensors)
					.headers(headers)
					.header("Content-Type", "application/json").body(obj).asJson();
			if (res.getStatus() == 201)
				return true;
			return false;
		} catch (UnirestException e) {
			OpenWareInstance.getInstance().logError("Error saving analytic sensor", e);// TODO
																						// Auto-generated
																						// catch
																						// block
			return false;
		}

	}

}
