package de.openinc.ow.core.model.data;

public class OpenWareNumber extends OpenWareValueDimension {

	public static final String TYPE = "Number";
	double value;

	public OpenWareNumber(String name, String unit, Double value) {
		super(name, unit, TYPE);
		if (value == null) {
			this.value = Double.NaN;
		} else {
			this.value = value;
		}
	}

	@Override
	public Double value() {
		// TODO Auto-generated method stub
		return this.value;
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public OpenWareValueDimension cloneDimension() {
		return new OpenWareNumber(this.getName(), this.getUnit(), this.value);
	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) {
		double val = Double.NaN;
		if (value instanceof Double) {
			val = ((Double) value).doubleValue();
		}
		if (value instanceof Float) {
			val = ((Float) value).doubleValue();
		}
		if (value instanceof Integer) {
			val = ((Integer) value).doubleValue();
		}

		if (Double.isNaN(val)) {
			try {
				val = Double.valueOf(("" + value));
			} catch (NumberFormatException e) {
				return null;
			}

		}
		return new OpenWareNumber(getName(), getUnit(), val);
	}

}
