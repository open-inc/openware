package de.openinc.ow.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import com.google.common.base.CharMatcher;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;

public class DataTools {
	private static CharMatcher isoMatcher = CharMatcher.javaIsoControl();

	public static long floorDate(long dateInMillis) {
		return floorDate(dateInMillis, Config.getLong("baseTimeInterval", (60l * 60l * 1000l)));
	}

	public static long floorDate(long dateInMillis, long interval) {
		if (interval == 0) {
			return dateInMillis;
		}
		long temp = dateInMillis % interval;
		return (dateInMillis - temp);
	}

	public static Object listGetOrDefault(List<Object> list, int index, Object defaultVal) {

		if (list.size() <= index || list.get(index) == null) {
			return defaultVal;
		}
		return list.get(index);
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

	public static String getJSONPartial(String key, Object value, boolean last, boolean isString) {
		if (isString) {
			value = "\"" + value + "\"";
		}
		return String.format("\"%s\" : %s", key, value) + (last ? "" : ",");
	}

	public static OpenWareDataItem getNoiseData(String id, String source, String name, int nrOfVals) {
		List<OpenWareValueDimension> valueTypes = new ArrayList<>();
		OpenWareNumber nr = new OpenWareNumber("tste", "kmh", 5.0);
		valueTypes.add(nr);

		OpenWareDataItem item = new OpenWareDataItem(id, source, name, new JSONObject(), valueTypes);

		long now = System.currentTimeMillis();
		long initialNow = now;
		List<OpenWareValue> vals = new ArrayList<OpenWareValue>();
		while ((initialNow + nrOfVals) > now) {
			OpenWareValue value = new OpenWareValue(now++);
			value.addValueDimension(nr);
			vals.add(value);
		}
		item.value(vals);
		return item;

	}

	public static String cleanAndValidate(String s) {
		return isoMatcher.removeFrom(s);
	}
}
