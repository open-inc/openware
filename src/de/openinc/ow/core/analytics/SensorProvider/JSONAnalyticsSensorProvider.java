package de.openinc.ow.core.analytics.SensorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.AnalyticSensorProvider;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.core.helper.Config;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValueDimension;

public class JSONAnalyticsSensorProvider implements AnalyticSensorProvider {
	private File myFile;
	private JSONObject cachedSensorData;
	private static boolean fileOpen;

	public JSONAnalyticsSensorProvider(String filePath) {
		File file = new File(filePath);
		if (!file.exists()) {
			throw new IllegalArgumentException("AnalyticsSensorFile must exist");
		}
		this.myFile = file;
		fileOpen = false;
		this.cachedSensorData = readJSONFile();
	}

	@Override
	public Map<String, Map<String, OpenWareDataItem>> getAnalyticSensors() {
		//this.cachedSensorData = readJSONFile();
		JSONArray sensors = this.cachedSensorData.getJSONArray("sensors");
		Map<String, Map<String, OpenWareDataItem>> items = new HashMap<>();
		for (int i = 0; i < sensors.length(); i++) {

			JSONObject o = sensors.getJSONObject(i);
			JSONObject obj = new JSONObject();

			obj.put("id", o.getString("sensorid"));
			JSONObject meta = new JSONObject(o.getString("parameter"));
			meta.put("operation", o.getString("operation"));
			obj.put("meta", meta);
			obj.put("name", o.getString("name"));
			obj.put("user", o.getString("user"));
			obj.put("valueTypes", new JSONArray(o.getString("valueTypes")));

			String prefix = Config.analyticPrefix;
			if (obj.getString("id").startsWith(prefix)) {
				prefix = "";
			}

			ArrayList<OpenWareValueDimension> dims = new ArrayList<OpenWareValueDimension>();
			obj.getJSONArray("valueTypes").forEach((item) -> {
				try {
					dims.add(OpenWareValueDimension.createNewDimension(((JSONObject) item).getString("name"),
							((JSONObject) item).getString("unit"), ((JSONObject) item).getString("type")));
				} catch (SecurityException e) {
					OpenWareInstance.getInstance().logError("Could not create Valuetype for analytic sensor", e);
					e.printStackTrace();
				} catch (JSONException e) {
					OpenWareInstance.getInstance().logError("Could not create Valuetype for analytic sensor", e);
					e.printStackTrace();
				}
			});
			OpenWareDataItem owdi = new OpenWareDataItem(prefix + obj.getString("id"), obj.getString("user"),
					obj.getString("name"), obj.getJSONObject("meta"), dims);

			Map<String, OpenWareDataItem> userItems = items.getOrDefault(owdi.getUser(),
					new HashMap<String, OpenWareDataItem>());
			userItems.put(owdi.getId(), owdi);
			items.put(owdi.getUser(), userItems);
		}

		return items;
	}

	@Override
	public boolean deleteAnalyticSensor(String user, String sensorid) {
		JSONArray array = this.cachedSensorData.getJSONArray("sensors");
		int toDelete = -1;
		for (int i = 0; i < array.length(); i++) {
			JSONObject obj = array.getJSONObject(i);
			if (obj.optString("user").equals(user) && obj.optString("sensorid").equals(sensorid)) {
				toDelete = i;
				break;
			}
		}
		if (toDelete == -1)
			return true;
		array.remove(toDelete);
		this.cachedSensorData.put("sensors", array);
		return saveFile();
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
		obj.put("objectId", "ID" + new Date().getTime());
		this.cachedSensorData.getJSONArray("sensors").put(obj);
		return saveFile();
	}

	private JSONObject readJSONFile() {
		if (!myFile.canRead() || JSONAnalyticsSensorProvider.fileOpen) {
			return this.cachedSensorData;
		}
		FileReader fis;
		try {

			fis = new FileReader(myFile);
		} catch (FileNotFoundException e1) {
			OpenWareInstance.getInstance().logError("Error reading analytics file: " + myFile.getAbsolutePath(), e1);
			return null;
		}
		BufferedReader br = new BufferedReader(fis);
		String everything = null;
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
			}
			everything = sb.toString();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				OpenWareInstance.getInstance().logError("Error closing analytics file: " + myFile.getAbsolutePath(), e);
				return null;
			}
		}
		if (everything != null && !everything.equals("")) {

			try {
				JSONObject obj = new JSONObject(everything);
				if (!obj.has("sensors"))
					obj.put("sensors", new JSONArray());
				return obj;
			} catch (JSONException e) {
				OpenWareInstance.getInstance().logError("Error parsing analytics file: " + myFile.getAbsolutePath(), e);
				return null;
			}

		} else {
			// No Data read from File
			OpenWareInstance.getInstance()
					.logWarn("Tried reading empty analytics sensor file: " + myFile.getAbsolutePath());
			return null;
		}
	}

	synchronized private boolean saveFile() {
		JSONAnalyticsSensorProvider.fileOpen = true;
		PrintWriter writer;
		try {
			writer = new PrintWriter(myFile, "UTF-8");
			writer.print(this.cachedSensorData.toString(2));
			writer.flush();
			writer.close();

		} catch (FileNotFoundException e) {
			OpenWareInstance.getInstance().logError("Could not save analytics sensors file", e);
			e.printStackTrace();
			return false;
		} catch (UnsupportedEncodingException e) {
			OpenWareInstance.getInstance().logError("Could not save analytics sensors file", e);
			e.printStackTrace();
			return false;
		} finally {
			JSONAnalyticsSensorProvider.fileOpen = false;
		}
		JSONAnalyticsSensorProvider.fileOpen = false;
		return true;

	}
}
