package de.openinc.ow.analytics.geoanalytics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterer;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareGeo;
import de.openinc.ow.core.model.data.OpenWareNumber;
import de.openinc.ow.core.model.data.OpenWareValue;
import de.openinc.ow.core.model.data.OpenWareValueDimension;

public class GeoHelper {

	public static OpenWareDataItem fromGeoJSON(OpenWareDataItem data) throws IllegalArgumentException {
		Dataset res = geoToDataset(data);
		List<OpenWareValueDimension> vTypes = new ArrayList<>();
		vTypes.add(OpenWareValueDimension.createNewDimension("Latitude", "", OpenWareNumber.TYPE));
		vTypes.add(OpenWareValueDimension.createNewDimension("Longitude", "", OpenWareNumber.TYPE));
		OpenWareDataItem toReturn = data.cloneItem();
		toReturn.setValueTypes(vTypes);
		toReturn.value(res.toValueList());
		return toReturn;
	}

	public static Dataset geoToDataset(OpenWareDataItem data) {
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

			//FEATURE Collection
			JSONObject current = (JSONObject) val.get(dimension).value();
			if (current.optString("type").toLowerCase().equals("featurecollection")) {
				List<JSONObject> temp = geojsons.getOrDefault(val.getDate(), new ArrayList<JSONObject>());
				temp.addAll(extractGeometriesFromFeatureCollection(current));
				geojsons.put(val.getDate(), temp);
				continue;
			}
			//FEATURE
			if (current.has("geometry")) {
				List<JSONObject> temp = geojsons.getOrDefault(val.getDate(), new ArrayList<JSONObject>());
				JSONObject toAdd = current.getJSONObject("geometry");
				if (toAdd != null)
					temp.add(toAdd);
				geojsons.put(val.getDate(), temp);
				continue;
			}
			//GEOMETRY
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

				switch (obj.optString("type").toLowerCase()) {
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

	private static OpenWareDataItem cluster(OpenWareDataItem data, Clusterer<Instance> clusterer) {
		OpenWareDataItem res = data.cloneItem(false);
		List<OpenWareValueDimension> vTypes = new ArrayList<OpenWareValueDimension>();
		vTypes.add(OpenWareGeo.createNewDimension("cluster", "", OpenWareGeo.TYPE));
		res.setValueTypes(vTypes);

		List<Cluster<Instance>> clusters = (List<Cluster<Instance>>) clusterer.cluster(geoToDataset(data));
		List<JSONObject> geoClusters = new ArrayList<JSONObject>();
		JSONObject featureCollection = new JSONObject();
		featureCollection.put("type", "FeatureCollection");
		JSONArray features = new JSONArray();
		for (Cluster<Instance> cluster : clusters) {
			Point2D[] hullpoints = new Point2D[cluster.getPoints().size()];
			int j = 0;
			for (Instance i : cluster.getPoints()) {
				hullpoints[j] = new Point2D(cluster.getPoints().get(j).getPoint()[0],
						cluster.getPoints().get(j).getPoint()[1], i.ts);
				j++;
			}
			GrahamScan hull = new GrahamScan(hullpoints);
			JSONObject feature = new JSONObject();
			JSONObject props = new JSONObject();
			props.put("clusterSize", cluster.getPoints().size());
			feature.put("properties", props);
			feature.put("type", "Feature");
			JSONObject geo = new JSONObject();

			JSONArray coords = new JSONArray();
			JSONArray first = null;
			int count = 0;
			for (Point2D point : hull.hull()) {
				count++;
				JSONArray coord = new JSONArray();
				coord.put(point.y());
				coord.put(point.x());
				coords.put(coord);
				if (first == null) {
					first = coord;
				}
			}
			if (count >= 3) {
				geo.put("type", "Polygon");
				coords.put(first);
			} else {
				continue;
			}

			JSONArray coordWrapper = new JSONArray();
			coordWrapper.put(coords);
			if (cluster.getPoints().size() >= 4) {
				geo.put("coordinates", coordWrapper);
			} else {
				geo.put("coordinates", coords);
			}

			feature.put("geometry", geo);
			features.put(feature);
		}
		featureCollection.put("features", features);
		res.setId(res.getId() + ".clustered");
		res.setName("Clusters: " + res.getName());
		OpenWareValue resVal = new OpenWareValue(data.value().get(0).getDate());
		resVal.addValueDimension(res.getValueTypes().get(0).createValueForDimension(featureCollection));
		res.value().add(resVal);
		return res;

	}

	public static OpenWareDataItem clusterKMeans(OpenWareDataItem data, JSONObject params) {
		KMeansPlusPlusClusterer<Instance> clusterer = new KMeansPlusPlusClusterer<Instance>(
				params.getInt("clusters"));
		return cluster(data, clusterer);
	}

	public static OpenWareDataItem clusterDensity(OpenWareDataItem data, JSONObject params) {
		DBSCANClusterer<Instance> clusterer = new DBSCANClusterer<Instance>(params.getDouble("epsilon"),
				params.getInt("minpoints"), new GeoDistanceMeasure());

		return cluster(data, clusterer);
	}

	public static List<Instance> generateHull(List<Instance> data) {

		return null;
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

	private static List<Instance> extractPointsFromGeometry(long ts, JSONObject collection,
			List<Instance> instances) {
		List<Instance> data;
		if (instances == null) {
			data = new ArrayList<>();
		} else {
			data = instances;
		}
		JSONObject geometry = collection.getJSONObject("geometry");
		switch (geometry.getString("type").toLowerCase()) {
		case "point":
			data.addAll(extractPointsFromPoint(ts, geometry));
			break;
		case "multipoint":
			data.addAll(extractPointsFromLineOrMultiPoint(ts, geometry));
			break;
		case "linestring":
			data.addAll(extractPointsFromLineOrMultiPoint(ts, geometry));
			break;
		case "multilinestring":
			data.addAll(extractPointsFromPolygonOrMultiLine(ts, geometry));
			break;
		case "polygon":
			data.addAll(extractPointsFromPolygonOrMultiLine(ts, geometry));
			break;
		case "multipolygon":
			data.addAll(extractPointsFromMultipolygon(ts, geometry));
			break;
		case "geometrycollection":
			data.addAll(extractPointsFromGeometryCollection(ts, geometry, data));
			break;
		default:
			throw new IllegalArgumentException(
					"GeoJSON Type not recognized\n" + geometry.toString());
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

		double lambda = L;
		for (int iter = 0; iter < MAXITERS; iter++) {
			final double lambdaOrig = lambda;
			cosLambda = Math.cos(lambda);
			sinLambda = Math.sin(lambda);
			final double t1 = cosU2 * sinLambda;
			final double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
			final double sinSqSigma = t1 * t1 + t2 * t2;
			sinSigma = Math.sqrt(sinSqSigma);
			cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;
			sigma = Math.atan2(sinSigma, cosSigma);
			final double sinAlpha = (sinSigma == 0) ? 0.0 : cosU1cosU2 * sinLambda / sinSigma;
			cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
			cos2SM = (cosSqAlpha == 0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;

			final double uSquared = cosSqAlpha * aSqMinusBSqOverBSq;
			A = 1 + (uSquared / 16384.0) *
					(4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)));
			final double B = (uSquared / 1024.0) *
					(256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
			final double C = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
			final double cos2SMSq = cos2SM * cos2SM;
			deltaSigma = B
					* sinSigma
					*
					(cos2SM + (B / 4.0)
							* (cosSigma * (-1.0 + 2.0 * cos2SMSq) - (B / 6.0) * cos2SM
									* (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));

			lambda = L + (1.0 - C) * f * sinAlpha
					* (sigma + C * sinSigma * (cos2SM + C * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));

			final double delta = (lambda - lambdaOrig) / lambda;

			if (Math.abs(delta) < 1.0e-12)
				break;
		}

		return (float) (b * A * (sigma - deltaSigma));
	}

}

class GeoDistanceMeasure implements org.apache.commons.math3.ml.distance.DistanceMeasure {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8498916920576257518L;

	@Override
	public double compute(double[] a, double[] b) {
		return GeoHelper.computeDistance(a[0], a[1], b[0], b[1]);
	}

}

class GrahamScan {
	private final Stack<Point2D> hull = new Stack<>();

	public GrahamScan(Point2D[] pts) {

		// defensive copy
		int N = pts.length;
		Point2D[] points = new Point2D[N];
		for (int i = 0; i < N; i++)
			points[i] = pts[i];

		// preprocess so that points[0] has lowest y-coordinate; break ties by x-coordinate
		// points[0] is an extreme point of the convex hull
		// (alternatively, could do easily in linear time)
		Arrays.sort(points);

		// sort by polar angle with respect to base point points[0],
		// breaking ties by distance to points[0]
		Arrays.sort(points, 1, N, points[0].POLAR_ORDER);

		hull.push(points[0]); // p[0] is first extreme point

		// find index k1 of first point not equal to points[0]
		int k1;
		for (k1 = 1; k1 < N; k1++)
			if (!points[0].equals(points[k1]))
				break;
		if (k1 == N)
			return; // all points equal

		// find index k2 of first point not collinear with points[0] and points[k1]
		int k2;
		for (k2 = k1 + 1; k2 < N; k2++)
			if (Point2D.ccw(points[0], points[k1], points[k2]) != 0)
				break;
		hull.push(points[k2 - 1]); // points[k2-1] is second extreme point

		// Graham scan; note that points[N-1] is extreme point different from points[0]
		for (int i = k2; i < N; i++) {
			Point2D top = hull.pop();
			while (Point2D.ccw(hull.peek(), top, points[i]) <= 0) {
				top = hull.pop();
			}
			hull.push(top);
			hull.push(points[i]);
		}

		assert isConvex();
	}

	// return extreme points on convex hull in counterclockwise order as an Iterable
	public Iterable<Point2D> hull() {
		Stack<Point2D> s = new Stack<>();
		for (Point2D p : hull)
			s.push(p);
		return s;
	}

	// check that boundary of hull is strictly convex
	private boolean isConvex() {
		int N = hull.size();
		if (N <= 2)
			return true;

		Point2D[] points = new Point2D[N];
		int n = 0;
		for (Point2D p : hull()) {
			points[n++] = p;
		}

		for (int i = 0; i < N; i++) {
			if (Point2D.ccw(points[i], points[(i + 1) % N], points[(i + 2) % N]) <= 0) {
				return false;
			}
		}
		return true;
	}

}

class Point2D implements Comparable<Point2D> {
	public static final Comparator<Point2D> X_ORDER = new XOrder();
	public static final Comparator<Point2D> Y_ORDER = new YOrder();
	public static final Comparator<Point2D> R_ORDER = new ROrder();

	public final Comparator<Point2D> POLAR_ORDER = new PolarOrder();
	public final Comparator<Point2D> ATAN2_ORDER = new Atan2Order();
	public final Comparator<Point2D> DISTANCE_TO_ORDER = new DistanceToOrder();

	private final double x; // x coordinate
	private final double y; // y coordinate

	public long getId() {
		return id;
	}

	private final long id;

	// create a new point (x, y)
	public Point2D(double x, double y) {
		this.x = x;
		this.y = y;
		this.id = -1;

	}

	public Point2D(double x, double y, long id) {
		this.y = y;
		this.x = x;
		this.id = id;
	}

	// return the x-coorindate of this point
	public double x() {
		return x;
	}

	// return the y-coorindate of this point
	public double y() {
		return y;
	}

	// return the radius of this point in polar coordinates
	public double r() {
		return Math.sqrt(x * x + y * y);
	}

	// return the angle of this point in polar coordinates
	// (between -pi/2 and pi/2)
	public double theta() {
		return Math.atan2(y, x);
	}

	// return the polar angle between this point and that point (between -pi and pi);
	// (0 if two points are equal)
	private double angleTo(Point2D that) {
		double dx = that.x - this.x;
		double dy = that.y - this.y;
		return Math.atan2(dy, dx);
	}

	// is a->b->c a counter-clockwise turn?
	// -1 if clockwise, +1 if counter-clockwise, 0 if collinear
	public static int ccw(Point2D a, Point2D b, Point2D c) {
		double area2 = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
		if (area2 < 0)
			return -1;
		else if (area2 > 0)
			return +1;
		else
			return 0;
	}

	// twice signed area of a-b-c
	public static double area2(Point2D a, Point2D b, Point2D c) {
		return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
	}

	// return Euclidean distance between this point and that point
	public double distanceTo(Point2D that) {
		double dx = this.x - that.x;
		double dy = this.y - that.y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	// return square of Euclidean distance between this point and that point
	public double distanceSquaredTo(Point2D that) {
		double dx = this.x - that.x;
		double dy = this.y - that.y;
		return dx * dx + dy * dy;
	}

	// compare by y-coordinate, breaking ties by x-coordinate
	public int compareTo(Point2D that) {
		if (this.y < that.y)
			return -1;
		if (this.y > that.y)
			return +1;
		if (this.x < that.x)
			return -1;
		if (this.x > that.x)
			return +1;
		return 0;
	}

	// compare points according to their x-coordinate
	private static class XOrder implements Comparator<Point2D> {
		public int compare(Point2D p, Point2D q) {
			if (p.x < q.x)
				return -1;
			if (p.x > q.x)
				return +1;
			return 0;
		}
	}

	// compare points according to their y-coordinate
	private static class YOrder implements Comparator<Point2D> {
		public int compare(Point2D p, Point2D q) {
			if (p.y < q.y)
				return -1;
			if (p.y > q.y)
				return +1;
			return 0;
		}
	}

	// compare points according to their polar radius
	private static class ROrder implements Comparator<Point2D> {
		public int compare(Point2D p, Point2D q) {
			double delta = (p.x * p.x + p.y * p.y) - (q.x * q.x + q.y * q.y);
			if (delta < 0)
				return -1;
			if (delta > 0)
				return +1;
			return 0;
		}
	}

	// compare other points relative to atan2 angle (bewteen -pi/2 and pi/2) they make with this Point
	private class Atan2Order implements Comparator<Point2D> {
		public int compare(Point2D q1, Point2D q2) {
			double angle1 = angleTo(q1);
			double angle2 = angleTo(q2);
			if (angle1 < angle2)
				return -1;
			else if (angle1 > angle2)
				return +1;
			else
				return 0;
		}
	}

	// compare other points relative to polar angle (between 0 and 2pi) they make with this Point
	private class PolarOrder implements Comparator<Point2D> {
		public int compare(Point2D q1, Point2D q2) {
			double dx1 = q1.x - x;
			double dy1 = q1.y - y;
			double dx2 = q2.x - x;
			double dy2 = q2.y - y;

			if (dy1 >= 0 && dy2 < 0)
				return -1; // q1 above; q2 below
			else if (dy2 >= 0 && dy1 < 0)
				return +1; // q1 below; q2 above
			else if (dy1 == 0 && dy2 == 0) { // 3-collinear and horizontal
				if (dx1 >= 0 && dx2 < 0)
					return -1;
				else if (dx2 >= 0 && dx1 < 0)
					return +1;
				else
					return 0;
			} else
				return -ccw(Point2D.this, q1, q2); // both above or below

			// Note: ccw() recomputes dx1, dy1, dx2, and dy2
		}
	}

	// compare points according to their distance to this point
	private class DistanceToOrder implements Comparator<Point2D> {
		public int compare(Point2D p, Point2D q) {
			double dist1 = distanceSquaredTo(p);
			double dist2 = distanceSquaredTo(q);
			if (dist1 < dist2)
				return -1;
			else if (dist1 > dist2)
				return +1;
			else
				return 0;
		}
	}

	// does this point equal y?
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (other == null)
			return false;
		if (other.getClass() != this.getClass())
			return false;
		Point2D that = (Point2D) other;
		// Don't use == here if x or y could be NaN or -0
		if (Double.compare(this.x, that.x) != 0)
			return false;
		if (Double.compare(this.y, that.y) != 0)
			return false;
		return true;
	}

	// must override hashcode if you override equals
	// See Item 9 of Effective Java (2e) by Joshua Block
	/*
	private volatile int hashCode;
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = 17;
			result = 31*result + Double.hashCode(x);
			result = 31*result + Double.hashCode(y);
			hashCode = result;
		}
		return result;
	}
	*/
	// convert to string
	public String toString() {
		return "(" + x +
				"," +
				y +
				")";
	}

}
