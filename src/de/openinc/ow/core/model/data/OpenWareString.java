package de.openinc.ow.core.model.data;

public class OpenWareString extends OpenWareValueDimension {

	private static final String TYPE = "String";
	String value;

	public OpenWareString(String name, String unit, String value) {
		super(name, unit, TYPE);
		this.value = value;

	}

	@Override
	public String value() {
		// TODO Auto-generated method stub
		return this.value;
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public OpenWareValueDimension cloneDimension() {
		return new OpenWareString(this.getName(), this.getUnit(), this.value);
	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) {
		return new OpenWareString(getName(), getUnit(), value.toString());
	}

}
