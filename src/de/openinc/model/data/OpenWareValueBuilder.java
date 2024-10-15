package de.openinc.model.data;

import org.json.JSONObject;

public class OpenWareValueBuilder {

	private OpenWareValue value;

	protected OpenWareValueBuilder() {
		value = new OpenWareValue(System.currentTimeMillis());
	}

	public OpenWareValueBuilder date(long ts) {
		value.setDate(ts);
		return this;
	}

	public OpenWareValueBuilder dim(OpenWareValueDimension dim) {
		value.add(dim);
		return this;
	}

	public OpenWareValueBuilder number(double number, String name, String unit) {
		value.add(new OpenWareNumber(name, unit, number));
		return this;
	}

	public OpenWareValueBuilder bool(boolean boolVal, String name) {
		value.add(new OpenWareBoolValue(name, "", boolVal));
		return this;
	}

	public OpenWareValueBuilder string(String string, String name) {
		value.add(new OpenWareString(name, "", string));
		return this;
	}

	public OpenWareValueBuilder geo(JSONObject geo, String name) {
		value.add(new OpenWareGeo(name, "", geo));
		return this;
	}

	public OpenWareValueBuilder generic(JSONObject generic, String name) {
		value.add(new OpenWareGeneric(name, "", generic));
		return this;
	}

	public OpenWareValue get() {
		return value;
	}

}
