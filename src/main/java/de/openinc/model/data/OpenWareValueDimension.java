package de.openinc.model.data;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.helper.DataTools;

public abstract class OpenWareValueDimension {

	abstract public Object value();

	abstract public String type();

	private String name;
	private String unit;
	private String type;

	public OpenWareValueDimension(String name, String unit, String type) {
		this.name = name;
		this.unit = unit;
		this.type = type;

	}

	abstract public OpenWareValueDimension createValueForDimension(Object value) throws JSONException;

	abstract public OpenWareValueDimension cloneDimension();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	/**
	 * Use this method to infer a {@link OpenWareValueDimension} based on a given
	 * value. <b>Use with care</b> since it, e.g. will try to parse booleans/numbers
	 * out of strings, etc. If all parsing fails a {@link OpenWareString} will be
	 * returned
	 * 
	 * @param value The value which will be used to infer the valuetype
	 * @param unit  Optional unit which will be assigned. Can be {@code NULL}
	 * @param name  Optional Name which will be assigned. Can be {@code NULL}. If
	 *              not provided, empty string will be used
	 * @throws {@link IllegalArgumentException} if the provided value is
	 *                {@code NULL}
	 * @return The educated guessed {@link OpenWareValueDimension}
	 */
	public static OpenWareValueDimension inferDimension(Object value, String unit, String name)
			throws IllegalArgumentException {
		if (value == null)
			throw new IllegalArgumentException("Value cannot be null when infering type");
		if (value instanceof Boolean) {
			return createNewDimension(name == null ? "" : name, unit == null ? "" : unit, OpenWareBoolValue.TYPE,
					value);
		}

		if (value instanceof Number || (unit != null && !unit.equals(""))) {
			return createNewDimension(name == null ? "" : name, unit == null ? "" : unit, OpenWareNumber.TYPE,
					((Number) value).doubleValue());
		}

		if (value instanceof JSONObject) {
			try {
				OpenWareGeo cGeo = new OpenWareGeo(name == null ? "" : name, unit == null ? "" : unit,
						(JSONObject) value);
				if (cGeo.value() != null) {
					return cGeo;
				}

			} catch (Exception e) {
				return new OpenWareGeneric(name == null ? "" : name, unit == null ? "" : unit, (JSONObject) value);
			}
		}

		String toTest = value.toString();

		if (toTest	.toLowerCase()
					.equals("true")
				|| toTest	.toLowerCase()
							.equals("false")) {
			return createNewDimension(name == null ? "" : name, unit == null ? "" : unit, OpenWareBoolValue.TYPE,
					Boolean.valueOf(value	.toString()
											.toLowerCase()));
		}

		try {
			double d = Double.parseDouble(toTest);
			return createNewDimension(name == null ? "" : name, unit == null ? "" : unit, OpenWareNumber.TYPE, d);
		} catch (Exception e) {
			// NOTHING TO DO
		}

		try {
			if (toTest.startsWith("{")) {
				JSONObject o = new JSONObject(toTest);
				try {
					OpenWareGeo cGeo = new OpenWareGeo(name == null ? "" : name, unit == null ? "" : unit, o);
					if (cGeo.value() != null) {
						return cGeo;
					}

				} catch (Exception e) {
					return new OpenWareGeneric(name == null ? "" : name, unit == null ? "" : unit, o);
				}
			}
		} catch (JSONException e) {
			// NOTHING TO DO
		}
		return new OpenWareString(name == null ? "" : name, unit == null ? "" : unit, toTest);

	}

	public static OpenWareValueDimension createNewDimension(String name, String unit, String odType, Object value) {
		switch (odType.toLowerCase()) {
		case "number":
			return new OpenWareNumber(name, unit, value != null ? (double) value : null);
		case "string":
			return new OpenWareString(name, unit, value != null ? (String) value : null);
		case "boolean":
			return new OpenWareBoolValue(name, unit, value != null ? (boolean) value : null);
		case "geo":
			return new OpenWareGeo(name, unit, value != null ? (JSONObject) value : null);
		default:
			return new OpenWareGeneric(name, unit, value != null ? (JSONObject) value : null);
		}
	}

	public abstract OpenWareValueDimension empty();

	public static OpenWareValueDimension createNewDimension(String name, String unit, String odType) {
		return createNewDimension(name, unit, odType, null);
	}

	@Override
	public String toString() {
		return "{" + DataTools.getJSONPartial("name", StringEscapeUtils.escapeJava(name), false, true)
				+ DataTools.getJSONPartial("unit", StringEscapeUtils.escapeJava(unit), false, true)
				+ DataTools.getJSONPartial("type", type, true, true) + "}";
	}
}
