package de.openinc.ow.middleware.handler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.api.DataHandler;
import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareValue;
import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.helper.Config;

public class TemplateDataHandler extends DataHandler {
	public static final String CSV = "csv";
	public static final String JSON_TIME_VALUE_PAIR = "jsontv";
	public static final String JSON_TIME_VALUEARRAY_PAIR = "jsontva";
	public static final String SINGLE_VALUE = "sv";

	// GENERAL OPTIONS
	public static final String TYPE = "type";
	public static final String JSON_TIME_FIELD = "time";
	public static final String JSON_VALUE_FIELD = "value";

	// TIME OPTIONS
	public static final String DATE_FORMAT = "dtformat";
	public static final String DATE_FORMAT_TS_MILLISECONDS = "TS_MS";
	public static final String DATE_FORMAT_TS_SECONDS = "TS_S";

	// CSV
	public static final String CSV_TIME_INDEX = "csv_time_index";
	public static final String CSV_SEPERATOR = "csv_seperator";
	public static final String CSV_VALUE_INDEXES = "csv_value_index";
	public static final String CSV_INCLUDES_HEADER = "csv_header";

	private DataHandler internalDH;

	private Map<String, String> options;
	private String extId;

	public TemplateDataHandler(DataHandler handlerForTemplateFormat, Map<String, String> options,
			String externalID) throws IllegalArgumentException {
		this.internalDH = handlerForTemplateFormat;
		if (options == null || options.size() == 0 || !options.containsKey("type")) {
			throw new IllegalArgumentException(
					"Options Map must not be emtpy and must at least contain 'type'-entry!");
		}
		this.options = options;
		this.extId = externalID;
	}

	@Override
	public List<OpenWareDataItem> handleData(String id, String data) {
		if (extId != null && !id.startsWith(extId))
			return null;
		JSONObject template = Config.mapId(id);

		try {
			OpenWareDataItem item = internalDH.handleData(id, template.toString()).get(0);
			switch (options.get(TYPE)) {
				case JSON_TIME_VALUEARRAY_PAIR: {
					item.value(parseJSONTVArrayPair(item, data));
					ArrayList<OpenWareDataItem> list = new ArrayList<>();
					list.add(item);
					return list;
				}
				case JSON_TIME_VALUE_PAIR: {
					item.value(parseJSONTVPair(item, data));
					ArrayList<OpenWareDataItem> list = new ArrayList<>();
					list.add(item);
					return list;
				}
				case SINGLE_VALUE: {
					parseSingleValue(item, data);
					ArrayList<OpenWareDataItem> list = new ArrayList<>();
					list.add(item);
					return list;
				}
				case CSV: {
					List<OpenWareValue> vals = parseCSV(item, data);
					item.value(vals);
					ArrayList<OpenWareDataItem> list = new ArrayList<>();
					list.add(item);
					return list;
				}

				default:
					return null;
			}

		} catch (Exception e) {

			OpenWareInstance.getInstance()
					.logDebug("TEMPLATE_DATA_HANDLER " + e.getLocalizedMessage(), e);
			return null;
		}

	}

	private List<OpenWareValue> parseSingleValue(OpenWareDataItem item, String data)
			throws ParseException, NumberFormatException, JSONException {
		ArrayList<OpenWareValue> res = new ArrayList<>();
		OpenWareValue value = new OpenWareValue(new Date().getTime());
		try {
			value.addValueDimension(item.getValueTypes().get(0).createValueForDimension(data));
			res.add(value);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			OpenWareInstance.getInstance().logTrace(e.getMessage());
		}

		return res;
	}

	private List<OpenWareValue> parseCSV(OpenWareDataItem item, String data) throws Exception {
		ArrayList<OpenWareValue> res = new ArrayList<>();
		String sep = options.get(CSV_SEPERATOR);
		String valIndexString = options.get(CSV_VALUE_INDEXES);
		String[] valIndex;

		boolean header = Boolean.valueOf(options.get(CSV_INCLUDES_HEADER));
		String[] datapoints = data.split("\n");
		if (header) {
			datapoints = Arrays.copyOfRange(datapoints, 1, datapoints.length);
		}

		int timeIndex = Integer.parseInt(options.get(CSV_TIME_INDEX));
		if (datapoints.length > 0) {
			String[] temp = datapoints[0].split(sep);
			// TimeIndex is OutOfBounds!!
			if (timeIndex >= temp.length)
				throw new IllegalArgumentException(
						"The provided index for the time value is larger than the number of columns in the CSV-dataset");
			// Create String Indices
			ArrayList<String> tempIndex = new ArrayList<>();
			valIndex = new String[temp.length - 1];
			for (int i = 0; i < temp.length; i++) {
				if (i != timeIndex)
					tempIndex.add("" + i);
			}
			valIndex = tempIndex.toArray(valIndex);
		} else {
			return res;
		}

		if (valIndexString != null) {
			valIndex = valIndexString.split(",");
		}

		if (sep == null)
			throw new IllegalArgumentException("CSV-Seperator must be provided in options!");

		for (String str : datapoints) {
			if (str != null && !str.equals("")) {
				String[] vals = str.split(sep);
				String time = vals[timeIndex];
				long ts = parseDate(time);
				OpenWareValue value = new OpenWareValue(ts);
				for (int i = 0; i < valIndex.length; i++) {

					value.addValueDimension(item.getValueTypes().get(i)
							.createValueForDimension(vals[Integer.parseInt(valIndex[i])]));
					res.add(value);

				}
			}
		}

		return res;
	}

	private List<OpenWareValue> parseJSONTVPair(OpenWareDataItem item, String data)
			throws Exception {
		ArrayList<OpenWareValue> res = new ArrayList<>();
		JSONArray values = new JSONArray();
		if (!data.startsWith("[")) {
			JSONObject obj = new JSONObject(data);
			values.put(obj);
		} else {
			values = new JSONArray(data);
		}
		for (int i = 0; i < values.length(); i++) {
			String time = values.getJSONObject(i).optString(options.get(JSON_TIME_FIELD));
			long ts = parseDate(time);
			OpenWareValue value = new OpenWareValue(ts);
			value.addValueDimension(item.getValueTypes().get(i)
					.createValueForDimension(values.getJSONObject(i).optString(JSON_VALUE_FIELD)));
			res.add(value);
		}
		return res;
	}

	private List<OpenWareValue> parseJSONTVArrayPair(OpenWareDataItem item, String data)
			throws Exception {
		ArrayList<OpenWareValue> res = new ArrayList<>();
		JSONArray values = new JSONArray();
		if (!data.startsWith("[")) {
			JSONObject obj = new JSONObject(data);
			values.put(obj);
		} else {
			values = new JSONArray(data);
		}
		for (int i = 0; i < values.length(); i++) {
			String time = values.getJSONObject(i).optString(options.get(JSON_TIME_FIELD));
			long ts = parseDate(time);
			OpenWareValue value = new OpenWareValue(ts);
			JSONArray vals = values.getJSONObject(i).getJSONArray(JSON_VALUE_FIELD);
			for (int j = 0; j < vals.length(); j++) {

				value.addValueDimension(
						item.getValueTypes().get(j).createValueForDimension(vals.optString(j)));
			}
			res.add(value);
		}

		return res;
	}

	private long parseDate(String time) throws ParseException, NumberFormatException {
		long res;
		if (options.get(DATE_FORMAT).equals(DATE_FORMAT_TS_MILLISECONDS)) {
			res = Long.valueOf(time);
		} else if (options.get(DATE_FORMAT).equals(DATE_FORMAT_TS_SECONDS)) {
			res = Long.valueOf(time) * 1000l;
		} else {
			SimpleDateFormat format = new SimpleDateFormat(options.get(DATE_FORMAT));
			res = format.parse(time).getTime();
		}
		return res;
	}

	public static Map<String, String> getDefaultOptions(String type) {
		HashMap<String, String> res = new HashMap<>();
		res.put(TYPE, type);
		res.put(DATE_FORMAT, DATE_FORMAT_TS_MILLISECONDS);
		if (type.equals(JSON_TIME_VALUE_PAIR) || type.equals(JSON_TIME_VALUEARRAY_PAIR)) {
			res.put(JSON_TIME_FIELD, "date");
			res.put(JSON_VALUE_FIELD, "value");
		}
		if (type.equals(CSV)) {
			res.put(CSV_TIME_INDEX, "" + 0);
			res.put(CSV_SEPERATOR, ";");
			res.put(CSV_INCLUDES_HEADER, "false");
		}

		return res;
	}

	@Override
	public boolean setOptions(JSONObject options) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

}
