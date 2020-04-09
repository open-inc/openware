package de.openinc.ow.core.model.data;

import java.util.ArrayList;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;

public class OpenWareGeo extends OpenWareValueDimension {

	JSONObject value;
	public static final String TYPE = "Geo";

	public OpenWareGeo(String name, String unit, JSONObject value) {
		super(name, unit, "Geo");
		this.value = value;

	}

	@Override
	public JSONObject value() {
		// TODO Auto-generated method stub
		return this.value;
	}

	@Override
	public String type() {
		return "Geo";
	}

	@Override
	public OpenWareValueDimension cloneDimension() {
		return new OpenWareGeo(this.getName(), this.getUnit(), this.value);
	}

	@Override
	public OpenWareValueDimension createValueForDimension(Object value) {
		try {
			JSONObject obj = null;
			if (value instanceof String) {
				try {
					obj = new JSONObject((String) value);
				} catch (JSONException e) {
					return null;
				}
			}
			if (value instanceof JSONObject) {
				obj = (JSONObject) value;
			}
			OpenWareValueDimension owg;

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
						owg = new OpenWareGeo(getName(), getUnit(), frame);
						return owg;
					}
				}

				// Validate Feature
				if (forth) {
					if (checkFeature(obj)) {
						owg = new OpenWareGeo(getName(), getUnit(), obj);
						return owg;
					}
				}

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
							owg = new OpenWareGeo(getName(), getUnit(), obj);
							return owg;
						}
					}
				}

				if (first || second || third || forth) {
					// No valid GeoJSON even though format looked like geojson: Emitting Warning;
					OpenWareInstance.getInstance()
							.logError("Tried parsing JSONObject as GeoJSON but was not valid\n" + obj.toString(2));
				}
				return null;
			}
			//NON GeoJSON Json Data
			return null;
		} catch (JSONException e) {
			OpenWareInstance.getInstance().logError("Tried parsing GeoJSON but was not valid\n" + value.toString());
			return null;
		}

	}

	private boolean checkGeometry(JSONObject obj) {
		if (obj.getString("type").toLowerCase().equals("geometrycollection")) {
			obj.put("type", "GeometryCollection");
			JSONArray geometries = obj.getJSONArray("geometries");
			boolean validated = true;
			for (int i = 0; i < geometries.length(); i++) {
				if (!checkGeometry(geometries.getJSONObject(i))) {
					validated = false;
					break;
				}
			}
			return validated;
		}

		String[] types = new String[] { "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString",
				"MultiPolygon" };
		ArrayList<String> allowed = new ArrayList<String>(Arrays.asList(types));
		if (allowed.contains(obj.getString("type"))) {
			return true;
		}
		return false;
	}

	private boolean checkFeature(JSONObject obj) {
		if (obj.getString("type").toLowerCase().equals("feature")) {
			obj.put("type", "Feature");
			if (!obj.has("properties")) {
				obj.put("properties", new JSONObject());
			}
			return checkGeometry(obj.getJSONObject("geometry"));
		}
		return false;
	}
}
