package de.openinc.model.data;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.helper.DataConversion;

public class OpenWareValue extends AbstractList<OpenWareValueDimension> implements Comparable<OpenWareValue> {

	private long date;
	private List<OpenWareValueDimension> value;

	public OpenWareValue(long date) {
		this.date = date;
		this.value = new ArrayList<OpenWareValueDimension>();

	}

	//	public OpenWareValue(long date, int initialDimensions) {
	//		this.date = date;
	//		this.value = new ArrayList<OpenWareValueDimension>(initialDimensions);
	//
	//	}

	public boolean addValueDimension(OpenWareValueDimension element) {
		return this.value.add(element);

	}

	@Override
	public boolean add(OpenWareValueDimension element) {
		return this.addValueDimension(element);
	}

	/*
	public void addValueDimension(String value) {
		try {
			if (value.startsWith("0") && !value.equals("0") && !value.startsWith("0.")) {
				throw new NumberFormatException();
			}
			OpenWareNumber own = new OpenWareNumber(Double.parseDouble(value));
			this.value.add(own);
			return;
		} catch (NumberFormatException e) {
	
		}
		if (value.toLowerCase().equals("true") || value.toLowerCase().equals("false")) {
			OpenWareBoolValue owb = new OpenWareBoolValue(Boolean.parseBoolean(value));
			this.value.add(owb);
			return;
		}
		try {
			OpenWareValueDimension owg;
			JSONObject obj = new JSONObject(value);
			if (obj.has("type")) {
	
				boolean first = obj.has("coordinates"); // Geometry without Feature Frame
				boolean second = obj.has("features"); // Feature Collection
				boolean third = obj.has("geometries"); // GeometryCollection
				boolean forth = obj.has("geometry"); // Feature
				// Validate Geometry
				if (first || third) {
					if (checkGeometry(obj)) {
						JSONObject frame = new JSONObject();
						frame.put("type", "Feature");
						frame.put("geometry", obj);
						frame.put("properties", new JSONObject());
						owg = new OpenWareGeo(frame);
						this.value.add(owg);
						return;
					}
				}
				;
	
				// Validate Feature
				if (forth) {
					if (checkFeature(obj)) {
						owg = new OpenWareGeo(obj);
						this.value.add(owg);
						return;
					}
				}
				;
	
				// Validate FeatureCollection
				if (second) {
					if (obj.getString("type").toLowerCase().equals("featurecollection")) {
						obj.put("type", "FeatureCollection");
						JSONArray features = obj.getJSONArray("features");
						boolean validated = true;
						for (int i = 0; i < features.length(); i++) {
							if (!checkFeature(features.getJSONObject(i))) {
								validated = false;
								break;
							}
						}
						if (validated) {
							owg = new OpenWareGeo(obj);
							this.value.add(owg);
							return;
						}
					}
				}
				;
	
				if (first || second || third || forth) {
					// No valid GeoJSON even though format looked like geojson: Emitting Warning;
					OpenWareInstance.getInstance()
							.logError("Tried parsing JSONObject as GeoJSON but was not valid\n" + obj.toString(2));
				}
				owg = new OpenWareGeneric(obj);
				this.value.add(owg);
				return;
			} else {
				owg = new OpenWareGeneric(obj);
				this.value.add(owg);
				return;
			}
	
		} catch (JSONException e) {
	
		}
		OpenWareString ows = new OpenWareString(value);
		this.value.add(ows);
	}
	*/
	public void removeValueDimension(int index) {
		this.value.remove(index);
	}

	@Override
	public OpenWareValueDimension get(int arg0) {
		if (arg0 >= this.value.size()) {
			System.out.println("Fehler");
		}
		return this.value.get(arg0);
	}

	@Override
	public OpenWareValueDimension set(int index, OpenWareValueDimension element) throws IllegalArgumentException {
		if (value.get(index) == null)
			throw new IllegalArgumentException("No Element to set value of at index " + index);
		return value.set(index, element);
	}

	@Override
	public int size() {
		return value.size();
	}

	public long getDate() {
		return date;
	}

	public String toString() {
		StringBuffer valueString = new StringBuffer("[");
		boolean first = true;
		for (OpenWareValueDimension dim : this) {
			if (!first) {
				valueString.append(",");

			}
			if (dim instanceof OpenWareString) {
				valueString.append("\"" +	StringEscapeUtils.escapeJava((String) dim.value()) +
									"\"");
			}if(dim instanceof OpenWareNumber) {
				double test = ((OpenWareNumber)dim).value();
				if(Double.isNaN(test)) {
					valueString.append(JSONObject.NULL);
				}else {
					valueString.append(dim.value());
				}
			}else {
				valueString.append(dim.value());
			}

			first = false;
		}
		valueString.append("]");
		return "{" +	DataConversion.getJSONPartial("date", date, false, false) +
				DataConversion.getJSONPartial("value", valueString.toString(), true,
						false) +
				"}";
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		obj.put("date", this.date);
		JSONArray value = new JSONArray();
		for (OpenWareValueDimension val : this.value) {
			value.put(val.value());
		}
		obj.put("value", value);
		return obj;
	}

	@Override
	public int compareTo(OpenWareValue o) {
		Long thisDate = new Long(this.getDate());
		return thisDate.compareTo(o.date);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof OpenWareValue) {
			return ((OpenWareValue) o).compareTo(this) == 0;
		}
		return false;
	}

}
