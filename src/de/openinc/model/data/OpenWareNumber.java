package de.openinc.model.data;

import org.json.JSONException;

public class OpenWareNumber extends OpenWareValueDimension {

	public static final String TYPE = "Number";
	private double value;

	public OpenWareNumber(String name, String unit, Double value) {
		super(name, unit, TYPE);
		if (value != null) {
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
	public OpenWareValueDimension createValueForDimension(Object value) throws JSONException {
		double val = Double.NaN;
		if (value instanceof Number) {
			val = ((Number) value).doubleValue();
		}
		if (Double.isNaN(val)) {
			try {
				val = Double.valueOf(("" + value));
			} catch (Exception e) {
				String exceptionCause = "The provided value needs to be a Number but is " + value.toString();
				if (value instanceof Number) {
					exceptionCause = "The provided value is invalid: " + val;
				}
				throw new JSONException(exceptionCause);
			}

		}
		return new OpenWareNumber(getName(), getUnit(), val);
	}

}
