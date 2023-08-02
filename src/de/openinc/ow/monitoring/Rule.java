package de.openinc.ow.monitoring;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareValue;
import de.openinc.ow.helper.Config;
import de.openinc.ow.middleware.services.DataService;

public class Rule {
	private Object condValue;

	private String type;
	private double min;
	private double max;

	private Rule(String type, Object value) {
		this.type = type;
		this.condValue = value;
		this.min = Double.MIN_VALUE;
		this.max = Double.MAX_VALUE;
	}

	private Rule(String type, Object value, Double min, Double max) {
		this.type = type;
		this.condValue = value;
		this.min = min != null ? min : Double.MIN_VALUE;
		this.max = max != null ? max : Double.MAX_VALUE;
	}

	public static Rule fromJSON(JSONObject o) {
		if (o.has("max") || o.has("min")) {
			return new Rule(o.getString("type"), o.optDouble("value", Double.NaN), o.optDouble("min", Double.MIN_VALUE),
					o.optDouble("max", Double.MAX_VALUE));
		}
		if (o.has("string")) {
			return new Rule(o.getString("type"), o.getString("string"));
		}
		if (o.has("geo")) {
			return new Rule(o.getString("type"), o.optJSONObject("geo"));
		}
		if (o.has("time")) {
			return new Rule(o.getString("type"), TimeRuleProperties.fromJSON(o.optJSONObject("time")));
		}
		return new Rule(o.getString("type"), o.optDouble("value"));
	}

	public boolean check(OpenWareDataItem currentValue, OpenWareDataItem lastObject, Object refValue, int dimension) {
		boolean triggered = false;
		if (type.equals("always") || type.endsWith("_always")) {
			triggered = true;
		} else if (type.startsWith("number") || type.equals("min") || type.equals("max") || type.equals("min-max")) {
			triggered = checkNumeric(currentValue.value().get(0), lastObject.value().get(0), refValue, dimension);
		} else if (type.startsWith("boolean")) {
			triggered = checkBool(currentValue.value().get(0), lastObject.value().get(0), refValue, dimension);
		} else if (type.startsWith("string")) {
			triggered = checkString(currentValue.value().get(0), lastObject.value().get(0), refValue, dimension);
		} else if (type.startsWith("ts")) {
			triggered = checkTimestamp(currentValue, lastObject, dimension);

		}
		return triggered;
	}

	private boolean checkNumeric(OpenWareValue currentValue, OpenWareValue lastValue, Object refValue, int dim) {
		double current = currentValue == null || currentValue.get(dim) == null ? Double.NaN
				: (double) currentValue.get(dim).value();
		if (Double.isNaN(current))
			return false;
		double last = lastValue == null || lastValue.get(dim) == null ? Double.NaN
				: (double) lastValue.get(dim).value();
		// double ref = refValue == null ? Double.NaN : refValue.doubleValue();
		switch (type) {
		case "number_change":
			return current != last;
		case "number_increased":
			return current > last;
		case "number_increased_by":
			return current > (last + (double) condValue);
		case "number_decreased":
			return current < last;
		case "number_decreased_by":
			return current < (last - (double) condValue);
		case "number_equals":
			return current == (double) condValue;
		case "number_equals_not":
			return current != (double) condValue;
		case "number_in_range":
			return current > min && current < max;
		case "number_out_of_range":
		case "min-max":
			return current < min || current > max;
		case "min":
		case "number_lt":
			return current < (double) condValue;
		case "max":
		case "number_gt":
			return current > (double) condValue;
		case "number_gt_ref":
			return (refValue instanceof Double && current > ((Double) refValue).doubleValue());
		case "number_lt_ref":
			return (refValue instanceof Double && current < ((Double) refValue).doubleValue());
		case "number_equals_ref":
			return (refValue instanceof Double && current == ((Double) refValue).doubleValue());
		case "number_equals_not_ref":
			return (refValue instanceof Double && current != ((Double) refValue).doubleValue());
		default:
			return false;
		}
	}

	private boolean checkString(OpenWareValue currentValueObj, OpenWareValue lastValueObj, Object refValue, int dim) {
		String match = (String) condValue;
		String currentValue = currentValueObj == null || currentValueObj.get(dim) == null ? null
				: (String) currentValueObj.get(dim).value();
		if (currentValue == null)
			return false;
		String lastValue = lastValueObj == null || lastValueObj.get(dim) == null ? null
				: (String) lastValueObj.get(dim).value();

		if (type.endsWith("_ref")) {
			match = refValue.toString();
			type.replace("_ref", "");
		}
		switch (type) {
		case "string_change":
		case "string-change":
			return !currentValue.equals(lastValue);
		case "string_equals":
		case "string-equals":
			return currentValue.equals(match);
		case "string-equals-not":
		case "string_equals_not":
			return !currentValue.equals(match);
		case "string-includes":
		case "string_includes":
			return currentValue.indexOf(match) > -1;
		case "string-includes-not":
		case "string_includes_not":
			return currentValue.indexOf(match) == -1;
		case "string-starts-with":
		case "string_starts_with":
			return currentValue.startsWith(match);
		case "string-starts-with-not":
		case "string_starts_with_not":
			return !currentValue.startsWith(match);
		case "string-ends-with":
		case "string_ends_with":
			return currentValue.endsWith(match);
		case "string-ends-with-not":
		case "string_ends_with_not":
			return !currentValue.endsWith(match);
		default:
			return false;
		}
	}

	private boolean checkBool(OpenWareValue currentValue, OpenWareValue lastValue, Object refItem, int dim) {
		Boolean current = currentValue == null || currentValue.get(dim) == null ? null
				: (boolean) currentValue.get(dim).value();
		if (current == null) {
			return false;
		}
		Boolean last = lastValue == null || lastValue.get(dim) == null ? false : (boolean) lastValue.get(dim).value();

		switch (type) {
		case "boolean_true":
			return current;
		case "boolean_false":
			return !current;
		case "boolean-rising-edge":
		case "boolean_rising_edge":
			return !last && current;
		case "boolean-falling-edge":
		case "boolean_falling_edge":
			return last && !current;
		case "boolean_change":
		case "boolean-rising-falling-edge":
			return last != current;
		case "boolean_equals_ref":
			return (refItem instanceof Boolean && current == (Boolean) refItem);
		case "boolean_equals_not_ref":
			return (refItem instanceof Boolean && current != (Boolean) refItem);
		default:
			return false;
		}
	}

	private boolean checkTimestamp(OpenWareDataItem current, OpenWareDataItem lastReceived, int dim) {

		TimeRuleProperties tProps = (TimeRuleProperties) this.condValue;
		if (type.startsWith("ts_last")) {
			switch (type) {
			case "ts_last_value_changed":
				return System.currentTimeMillis() - DataService.getLastChangeOfItemDimension(current.getSource(),
						current.getId(), dim) > tProps.value;
			case "ts_last_value_received":
				OpenWareDataItem lastKnownItem = DataService.getLiveSensorData(current.getId(), current.getSource());
				if (lastKnownItem == null)
					return true;
				return System.currentTimeMillis() - lastKnownItem.value().get(0).getDate() > tProps.value;
			default:
				return false;
			}
		}
		if (current == null || current.value().get(0) == null)
			return false;
		Instant currTS = Instant.ofEpochMilli(current.value().get(0).getDate());
		ZonedDateTime zdt = ZonedDateTime.ofInstant(currTS, ZoneId.of(tProps.tz));

		switch (type) {
		case "ts_minute_of_day": {
			int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
			return minuteOfDay >= tProps.minuteMin && minuteOfDay <= tProps.minuteMax;
		}
		case "ts_day_of_week":
			return zdt.getDayOfWeek().getValue() >= tProps.dateMin && zdt.getDayOfWeek().getValue() <= tProps.dateMax;

		case "ts_day_of_month":
			return zdt.getDayOfMonth() >= tProps.dateMin && zdt.getDayOfMonth() <= tProps.dateMax;

		case "ts_day_of_year":
			return zdt.getDayOfYear() >= tProps.dateMin && zdt.getDayOfYear() <= tProps.dateMax;

		case "ts_minute_day_of_week": {
			int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
			return minuteOfDay >= tProps.minuteMin && minuteOfDay <= tProps.minuteMax
					&& zdt.getDayOfWeek().getValue() >= tProps.dateMin
					&& zdt.getDayOfWeek().getValue() <= tProps.dateMax;
		}

		case "ts_minute_day_of_month ": {
			int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
			return minuteOfDay >= tProps.minuteMin && minuteOfDay <= tProps.minuteMax
					&& zdt.getDayOfMonth() >= tProps.dateMin && zdt.getDayOfMonth() <= tProps.dateMax;
		}

		case "ts_minute_day_of_year": {
			int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
			return minuteOfDay >= tProps.minuteMin && minuteOfDay <= tProps.minuteMax
					&& zdt.getDayOfYear() >= tProps.dateMin && zdt.getDayOfYear() <= tProps.dateMax;
		}

		default:
			return false;
		}

	}

	public String getType() {
		return type;
	}

	public Object getCondValue() {
		return condValue;
	}

	static class TimeRuleProperties {
		public int minuteMin;
		public int minuteMax;
		public int dateMin;
		public int dateMax;
		public long value;
		public String tz;

		public static TimeRuleProperties fromJSON(JSONObject o) {
			TimeRuleProperties t = new TimeRuleProperties();
			t.dateMax = o.optInt("dateMax", 366);
			t.dateMin = o.optInt("dateMin", 0);
			t.minuteMin = o.optInt("minuteMin", 0);
			t.minuteMax = o.optInt("dateMax", 59);
			t.value = o.optLong("value", 0l);
			t.tz = o.optString("tz", Config.get("timezone", "Europe/Berlin"));
			return t;
		}
	}
}
