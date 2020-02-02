package de.openinc.ow.core.model.data;

import org.json.JSONObject;

import fr.opensagres.xdocreport.document.json.JSONException;

public class OpenWareGeneric extends OpenWareValueDimension {

	private static final String TYPE = "Object";
	JSONObject value;

	public OpenWareGeneric(String name, String unit, JSONObject value) {
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
		return new OpenWareGeneric(this.getName(), this.getUnit(), this.value);
	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) {
		if (value instanceof JSONObject) {
			return new OpenWareGeneric(getName(), getUnit(), (JSONObject) value);
		}
		if (value instanceof String) {
			try {
				JSONObject o = new JSONObject((String) value);
				return new OpenWareGeneric(getName(), getUnit(), o);
			} catch (JSONException e) {
				return null;
			}

		}
		return null;
	}

}
