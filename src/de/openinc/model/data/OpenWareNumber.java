package de.openinc.model.data;

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
	public OpenWareValueDimension createValueForDimension(Object value) throws Exception{
		double val = Double.NaN;
		if (value instanceof Number) {
			val = ((Number) value).doubleValue();
		}
		if (Double.isNaN(val)) {
			try {
				val = Double.valueOf(("" + value));	
			}catch(Exception e) {
				String exceptionCause = "The provided value needs to be a Number but is " + value.toString();
				if (value instanceof Number) {
					exceptionCause = "The provided value is invalid: " + val;
				}
				throw new IllegalArgumentException(exceptionCause);
			}
			
		}
		return new OpenWareNumber(getName(), getUnit(), val);
	}

}
