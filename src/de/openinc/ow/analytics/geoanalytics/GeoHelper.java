package de.openinc.ow.analytics.geoanalytics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareGeo;
import de.openinc.ow.core.model.data.OpenWareValue;
import de.openinc.ow.core.model.data.OpenWareValueDimension;

public class GeoHelper {

	public static Dataset fromGeoJSON(OpenWareDataItem data) throws IllegalArgumentException {
		Dataset res = new Dataset();
		HashMap<Long, List<JSONObject>> geojsons = new HashMap<>();
		List<OpenWareValue> values = data.value();
		if (values == null || values.size() == 0)
			return null;
		int dimension = -1;
		int i = 0;
		for (OpenWareValueDimension dim : values.get(0)) {
			if (dim instanceof OpenWareGeo) {
				dimension = i;
				break;
			} else {
				i++;
			}
		}
		if (dimension == -1) {
			throw new IllegalArgumentException("GeoHelper: Data does not contain any Geo Values ");
		}
		for (OpenWareValue val : values) {
			if (!(val.get(dimension) instanceof OpenWareGeo)) {
				throw new IllegalArgumentException("Data must contain OpenWareGeoValue at dimension " + dimension);
			}

			JSONObject current = (JSONObject) val.get(dimension).value();
			if (current.optString("type").toLowerCase().equals("featurecollection")) {
				List<JSONObject> temp = geojsons.getOrDefault(val.getDate(), new ArrayList<JSONObject>());
				temp.addAll(extractGeometriesFromFeatureCollection(current));
				geojsons.put(val.getDate(), temp);
				continue;
			}
			if (current.has("geometry")) {
				List<JSONObject> temp = geojsons.getOrDefault(val.getDate(), new ArrayList<JSONObject>());
				JSONObject toAdd = current.getJSONObject("geometry");
				if (toAdd != null)
					temp.add(toAdd);
				geojsons.put(val.getDate(), temp);
				continue;
			}
			if (current.has("type") && current.has("coordinates")) {
				List<JSONObject> temp = geojsons.getOrDefault(val.getDate(), new ArrayList<JSONObject>());
				temp.add(current);
				geojsons.put(val.getDate(), temp);
				continue;
			}
		}

		for (long ts : geojsons.keySet()) {
			//System.out.println(ts);
			for (JSONObject obj : geojsons.get(ts)) {

				switch (obj.getString("type").toLowerCase()) {
				case "point":
					res.addAll(extractPointsFromPoint(ts, obj));
					break;
				case "multipoint":
					res.addAll(extractPointsFromLineOrMultiPoint(ts, obj));
					break;
				case "linestring":
					res.addAll(extractPointsFromLineOrMultiPoint(ts, obj));
					break;
				case "multilinestring":
					res.addAll(extractPointsFromPolygonOrMultiLine(ts, obj));
					break;
				case "polygon":
					res.addAll(extractPointsFromPolygonOrMultiLine(ts, obj));
					break;
				case "multipolygon":
					res.addAll(extractPointsFromMultipolygon(ts, obj));
					break;
				case "geometrycollection":
					res.addAll(extractPointsFromGeometryCollection(ts, obj, null));
					break;
				default:
					throw new IllegalArgumentException("GeoJSON Type not recognized\n" + obj.toString());
				}
			}
		}

		return res;
	}

	private static List<JSONObject> extractGeometriesFromFeatureCollection(JSONObject feature) {
		List<JSONObject> extracted = new ArrayList<>();
		JSONArray features = feature.getJSONArray("features");
		for (int i = 0; i < features.length(); i++) {
			extracted.add(features.getJSONObject(i).getJSONObject("geometry"));
		}
		return extracted;
	}

	private static List<Instance> extractPointsFromPoint(long ts, JSONObject point) {
		ArrayList<Instance> data = new ArrayList<>();
		JSONArray points = point.getJSONArray("coordinates");
		Instance inst = new Instance(ts, new double[] { points.getDouble(1), points.getDouble(0) });
		data.add(inst);
		return data;
	}

	private static List<Instance> extractPointsFromLineOrMultiPoint(long ts, JSONObject lineOrMultiP) {
		ArrayList<Instance> data = new ArrayList<>();
		JSONArray points2 = lineOrMultiP.getJSONArray("coordinates");
		for (int i = 0; i < points2.length(); i++) {
			JSONArray points = points2.getJSONArray(i);
			Instance inst = new Instance(ts, new double[] { points.getDouble(1), points.getDouble(0) });
			data.add(inst);
		}
		return data;
	}

	private static List<Instance> extractPointsFromPolygonOrMultiLine(long ts, JSONObject Polygon) {
		ArrayList<Instance> data = new ArrayList<>();
		JSONArray points2 = Polygon.getJSONArray("coordinates");
		for (int i = 0; i < points2.length(); i++) {
			//OUTER RING AND HOLES OF POLYGON
			JSONArray points3 = points2.getJSONArray(i);
			for (int j = 0; j < points3.length(); j++) {
				//POINTS OF EACH RING/HOLE
				JSONArray points = points3.getJSONArray(j);
				Instance inst = new Instance(ts, new double[] { points.getDouble(1), points.getDouble(0) });
				data.add(inst);
			}

		}
		return data;
	}

	private static List<Instance> extractPointsFromMultipolygon(long ts, JSONObject mPolygon) {
		ArrayList<Instance> data = new ArrayList<>();
		JSONArray points2 = mPolygon.getJSONArray("coordinates");
		for (int i = 0; i < points2.length(); i++) {
			//EACH POLYGON
			JSONArray points3 = points2.getJSONArray(i);
			for (int j = 0; j < points3.length(); j++) {
				//EACH RING/HOLE
				JSONArray points4 = points3.getJSONArray(j);
				for (int k = 0; k < points4.length(); k++) {
					JSONArray points = points4.getJSONArray(j);
					Instance inst = new Instance(ts, new double[] { points.getDouble(1), points.getDouble(0) });
					data.add(inst);
				}

			}

		}
		return data;
	}

	private static List<Instance> extractPointsFromGeometryCollection(long ts, JSONObject collection,
			List<Instance> instances) {
		List<Instance> data;
		if (instances == null) {
			data = new ArrayList<>();
		} else {
			data = instances;
		}
		JSONArray geometries = collection.getJSONArray("geometries");
		for (int i = 0; i < geometries.length(); i++) {
			switch (geometries.getJSONObject(i).getString("type").toLowerCase()) {
			case "point":
				data.addAll(extractPointsFromPoint(ts, geometries.getJSONObject(i)));
				break;
			case "multipoint":
				data.addAll(extractPointsFromLineOrMultiPoint(ts, geometries.getJSONObject(i)));
				break;
			case "linestring":
				data.addAll(extractPointsFromLineOrMultiPoint(ts, geometries.getJSONObject(i)));
				break;
			case "multilinestring":
				data.addAll(extractPointsFromPolygonOrMultiLine(ts, geometries.getJSONObject(i)));
				break;
			case "polygon":
				data.addAll(extractPointsFromPolygonOrMultiLine(ts, geometries.getJSONObject(i)));
				break;
			case "multipolygon":
				data.addAll(extractPointsFromMultipolygon(ts, geometries.getJSONObject(i)));
				break;
			case "geometrycollection":
				data.addAll(extractPointsFromGeometryCollection(ts, geometries.getJSONObject(i), data));
				break;
			default:
				throw new IllegalArgumentException(
						"GeoJSON Type not recognized\n" + geometries.getJSONObject(i).toString());
			}
		}

		return data;
	}

	public static float computeDistance(double lat1, double lon1, double lat2, double lon2) {
		// Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
		// using the "Inverse Formula" (section 4)

		final int MAXITERS = 20;
		// Convert lat/long to radians
		lat1 *= Math.PI / 180.0;
		lat2 *= Math.PI / 180.0;
		lon1 *= Math.PI / 180.0;
		lon2 *= Math.PI / 180.0;

		final double a = 6378137.0; // WGS84 major axis
		final double b = 6356752.3142; // WGS84 semi-major axis
		final double f = (a - b) / a;
		final double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

		final double L = lon2 - lon1;
		double A = 0.0;
		final double U1 = Math.atan((1.0 - f) * Math.tan(lat1));
		final double U2 = Math.atan((1.0 - f) * Math.tan(lat2));

		final double cosU1 = Math.cos(U1);
		final double cosU2 = Math.cos(U2);
		final double sinU1 = Math.sin(U1);
		final double sinU2 = Math.sin(U2);
		final double cosU1cosU2 = cosU1 * cosU2;
		final double sinU1sinU2 = sinU1 * sinU2;

		double sigma = 0.0;
		double deltaSigma = 0.0;
		double cosSqAlpha = 0.0;
		double cos2SM = 0.0;
		double cosSigma = 0.0;
		double sinSigma = 0.0;
		double cosLambda = 0.0;
		double sinLambda = 0.0;

		double lambda = L; // initial guess
		for (int iter = 0; iter < MAXITERS; iter++) {
			final double lambdaOrig = lambda;
			cosLambda = Math.cos(lambda);
			sinLambda = Math.sin(lambda);
			final double t1 = cosU2 * sinLambda;
			final double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
			final double sinSqSigma = t1 * t1 + t2 * t2; // (14)
			sinSigma = Math.sqrt(sinSqSigma);
			cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda; // (15)
			sigma = Math.atan2(sinSigma, cosSigma); // (16)
			final double sinAlpha = (sinSigma == 0) ? 0.0 : cosU1cosU2 * sinLambda / sinSigma; // (17)
			cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
			cos2SM = (cosSqAlpha == 0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha; // (18)

			final double uSquared = cosSqAlpha * aSqMinusBSqOverBSq; // defn
			A = 1 + (uSquared / 16384.0) * // (3)
					(4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)));
			final double B = (uSquared / 1024.0) * // (4)
					(256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
			final double C = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha)); // (10)
			final double cos2SMSq = cos2SM * cos2SM;
			deltaSigma = B
					* sinSigma
					* // (6)
					(cos2SM + (B / 4.0)
							* (cosSigma * (-1.0 + 2.0 * cos2SMSq) - (B / 6.0) * cos2SM
									* (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));

			lambda = L + (1.0 - C) * f * sinAlpha
					* (sigma + C * sinSigma * (cos2SM + C * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM))); // (11)

			final double delta = (lambda - lambdaOrig) / lambda;

			if (Math.abs(delta) < 1.0e-12)
				break;
		}

		return (float) (b * A * (sigma - deltaSigma));
	}

}
