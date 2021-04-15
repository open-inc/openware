package de.openinc.model.data;

import org.apache.commons.text.StringEscapeUtils;

import de.openinc.ow.helper.DataConversion;

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

	abstract public OpenWareValueDimension createValueForDimension(Object value) throws Exception;

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

	public static OpenWareValueDimension createNewDimension(String name, String unit, String odType) {
		switch (odType.toLowerCase()) {
		case "number":
			return new OpenWareNumber(name, unit, null);
		case "string":
			return new OpenWareString(name, unit, null);
		case "boolean":
			return new OpenWareBoolValue(name, unit, null);
		case "geo":
			return new OpenWareGeo(name, unit, null);
		default:
			return new OpenWareGeneric(name, unit, null);
		}
	}

	@Override
	public String toString() {
		return "{" + DataConversion.getJSONPartial("name", StringEscapeUtils.escapeJava(name), false, true) +
				DataConversion.getJSONPartial("unit", StringEscapeUtils.escapeJava(unit), false, true) +
				DataConversion.getJSONPartial("type", type, true, true) +
				"}";
	}
}
