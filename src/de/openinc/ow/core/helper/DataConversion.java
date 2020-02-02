package de.openinc.ow.core.helper;

import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;

public class DataConversion {

	public static JSONArray dataset2JSON(Dataset dataset) {
		JSONArray res = new JSONArray();
		for (Instance x : dataset) {
			JSONObject obj = new JSONObject();
			obj.put("time", x.getTime());
			obj.put("value", x.values);
			res.put(obj);
		}
		return res;
	}

	public static long floorDate(long dateInMillis) {
		return floorDate(dateInMillis, Config.baseTimeInterval);
	}

	public static long floorDate(long dateInMillis, long interval) {
		if (interval == 0) {
			return dateInMillis;
		}
		long temp = dateInMillis % interval;
		return (dateInMillis - temp);
	}

	public static String mapSPSDataType(String type) {
		HashMap<String, String> mapping = new HashMap<>();
		mapping.put("WORD", "number");
		mapping.put("BYTE", "number");
		mapping.put("DWORD", "number");
		mapping.put("LWORD", "number");
		mapping.put("SINT", "number");
		mapping.put("USINT", "number");
		mapping.put("INT", "number");
		mapping.put("UINT", "number");
		mapping.put("DINT", "number");
		mapping.put("UDINT", "number");
		mapping.put("LINT", "number");
		mapping.put("ULINT", "number");
		mapping.put("REAL", "number");
		mapping.put("LREAL", "number");
		mapping.put("STRING", "string");
		mapping.put("WSTRING", "string");
		mapping.put("TIME", "number");
		mapping.put("TIME_OF_DAY", "number");
		mapping.put("DATE", "number");
		mapping.put("DATE_AND_TIME", "number");
		mapping.put("LTIME", "number");
		mapping.put("BOOL", "boolean");

		return mapping.get(type.toUpperCase());
	}
}
