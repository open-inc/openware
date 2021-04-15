package de.openinc.model.data;

public class OpenWareBoolValue extends OpenWareValueDimension {

	Boolean value;
	public static String TYPE = "Boolean";

	public OpenWareBoolValue(String name, String unit, Boolean value) {
		super(name, unit, TYPE);
		this.value = value;
	}

	@Override
	public Object value() {
		return this.value.booleanValue();
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public OpenWareValueDimension cloneDimension() {
		return new OpenWareBoolValue(this.getName(), this.getUnit(), this.value);
	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) throws Exception{

		Boolean val = null;
		if (value instanceof Boolean) {
			val = ((Boolean) value).booleanValue();
		}
		if (value instanceof String) {
			val = Boolean.valueOf((String) value);
		}
		if (value instanceof Integer) {
			val = ((Integer) value).intValue() >= 1;
		}
		if (value instanceof Double) {
			val = ((Double) value).doubleValue() >= 1.0;
		}
		if (val == null)
			throw new IllegalArgumentException("The provided value needs to be a boolean value,  a String (which will be parsed), or a number larger than 1 but is " + value);

		return new OpenWareBoolValue(this.getName(), this.getUnit(), val);

	}

}
