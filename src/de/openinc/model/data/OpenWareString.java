package de.openinc.model.data;

public class OpenWareString extends OpenWareValueDimension {

	public static final String TYPE = "String";
	private String value;

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

	@Override
	public OpenWareValueDimension empty() {
		return new OpenWareString(this.getName(), this.getUnit(), "");
	}
}
