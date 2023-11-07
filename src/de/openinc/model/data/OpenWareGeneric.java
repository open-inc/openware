package de.openinc.model.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenWareGeneric extends OpenWareValueDimension {

	public static final String TYPE = "Object";
	Object value;

	public OpenWareGeneric(String name, String unit, JSONObject value) {
		super(name, unit, TYPE);
		this.value = value;
	}

	public OpenWareGeneric(String name, String unit, JSONArray value) {
		super(name, unit, TYPE);
		this.value = value;
	}

	@Override
	public Object value() {
		// TODO Auto-generated method stub
		return this.value;
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public OpenWareValueDimension cloneDimension() {
		if (this.value instanceof JSONObject) {
			return new OpenWareGeneric(this.getName(), this.getUnit(), (JSONObject) this.value);
		} else {
			return new OpenWareGeneric(this.getName(), this.getUnit(), (JSONArray) this.value);
		}

	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) throws JSONException {
		try {
			if (value instanceof JSONObject) {
				return new OpenWareGeneric(getName(), getUnit(), (JSONObject) value);
			}
			if (value instanceof String) {
				try {
					JSONObject o = new JSONObject((String) value);
					return new OpenWareGeneric(getName(), getUnit(), o);
				} catch (JSONException e) {
					throw new IllegalArgumentException(
							"The provided value needs to be a JSON-Object but is " + value.toString());
				}

			}

			throw new IllegalArgumentException(
					"The provided value needs to be a JSON-Object but is " + value.toString());
		} catch (Exception e) {
			throw new JSONException(e);
		}
	}

	@Override
	public OpenWareValueDimension empty() {
		return new OpenWareGeneric(this.getName(), this.getUnit(), new JSONObject());
	}
}
