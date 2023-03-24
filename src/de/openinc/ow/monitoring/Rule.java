package de.openinc.ow.monitoring;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;

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
		return new Rule(o.getString("type"), o.optDouble("value"));
	}

	public boolean check(Object currentValue, Object lastObject, Object refValue) {
		boolean triggered = false;
		if (type.equals("always") || type.endsWith("_always")) {
			triggered = true;
		} else if (type.startsWith("number") || type.equals("min") || type.equals("max") || type.equals("min-max")) {
			triggered = checkNumeric(currentValue == null ? null : (Double) currentValue,
					lastObject == null ? null : (Double) lastObject, refValue == null ? null : refValue);
		} else if (type.startsWith("boolean")) {
			triggered = checkBool((Boolean) currentValue, (Boolean) lastObject, refValue);
		} else if (type.startsWith("string")) {
			triggered = checkString((String) currentValue, (String) lastObject, refValue);
		} else if (type.startsWith("timestamp")) {
			// triggered = checkTimestamp((Long) currentValue, (Long) lastObject, (Long)
			// refValue);
		}
		return triggered;
	}

	private boolean checkNumeric(Double currentValue, Double lastValue, Object refValue) {
		double current = currentValue == null ? Double.NaN : currentValue.doubleValue();
		double last = lastValue == null ? Double.NaN : lastValue.doubleValue();
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
			return currentValue == (double) condValue;
		case "number_equals_not":
			return currentValue != (double) condValue;
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

	private boolean checkString(String currentValue, String lastValue, Object refValue) {
		String match = (String) condValue;
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

	private boolean checkBool(Boolean currentValue, Boolean lastValue, Object refItem) {
		boolean current = currentValue == null ? false : currentValue.booleanValue();
		boolean last = lastValue == null ? false : lastValue.booleanValue();

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

	private boolean checkTimestamp(JSONObject currentObj, OpenWareDataItem currentItem, OpenWareDataItem lastItem) {
		Instant currTS = Instant.ofEpochMilli(currentItem.value().get(0).getDate());
		ZonedDateTime zdt = ZonedDateTime.ofInstant(currTS, ZoneId.of(currentObj.getString("timezone")));

		// TODO Auto-generated method stub
		return false;
	}

	public String getType() {
		return type;
	}

	public Object getCondValue() {
		return condValue;
	}
}
