package de.openinc.ow.middleware.services;

import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.analytics.dataprocessing.DataSplitter;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

public class TransformationService {
	private static TransformationService me;

	private TransformationService() {
		me = this;
	}

	public static TransformationService getInstance() {
		if (me == null) {
			new TransformationService();
		}
		return me;
	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, JSONObject params) throws Exception {
		if (params.has("dimension") && params.has("interval") && params.has("operation")) {
			return createStatistics(data, params.getInt("dimension"), params.getString("operation"),
					params.getLong("interval"));
		}
		if (params.has("dimension") && params.has("start") && params.has("end") && params.has("splits")
				&& params.has("operation")) {
			return createStatistics(data, params.getInt("dimension"), params.getString("operation"),
					params.getLong("interval"));
		} else {
			throw new IllegalArgumentException(
					"Missing parameters for descriptive aggregation(Required: dimension, interval, operation || dimension, start, end, splits, operation)! Provided:\n" +
												params.toString(2));
		}
	}

	private double generateStatistic(List<OpenWareValue> data, String op, int dim) {
		DescriptiveStatistics stats = new DescriptiveStatistics();
		stats.clear();
		data.forEach(item -> {
			stats.addValue((double) item.get(dim).value());
		});
		double resVal = 0;
		if (stats.getN() > 0) {
			switch (op.toLowerCase()) {
			case "mean":
				resVal = stats.getMean();
				break;
			case "min":
				resVal = stats.getMin();
				break;
			case "max":
				resVal = stats.getMax();
				break;
			case "sum":
				resVal = stats.getSum();
				break;
			case "stdd":
				resVal = stats.getStandardDeviation();
				break;
			case "variance":
				resVal = stats.getVariance();
				break;
			case "count":
				resVal = stats.getN();
				break;
			default:
				OpenWareInstance.getInstance().logError("Analytics error. Could parse parameter of request");
			}
		}
		if (!Double.isFinite(resVal)) {
			OpenWareInstance.getInstance().logError("Got Infinite Value while " + op +
													" Databatch Size: " +
													stats.getValues().length);

		}
		return resVal;

	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, int dim, String op, long interval) {
		TreeMap<Long, OpenWareDataItem> splits = new TreeMap<>();
		splits.putAll(DataSplitter.split(data, interval));

		data.value().clear();
		data.reduceToSingleDimension(dim);

		for (long ts : splits.keySet()) {
			OpenWareValue val = new OpenWareValue(ts);
			val.addValueDimension(data.getValueTypes().get(0)
					.createValueForDimension(generateStatistic(splits.get(ts).value(), op, 0)));
			data.value().add(val);
		}
		return data;
	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, int dim, String op, long start, long end,
			int nrOfSplits) {
		TreeMap<Long, OpenWareDataItem> splits = new TreeMap<>();
		splits.putAll(DataSplitter.split(data, start, end, nrOfSplits));

		data.value().clear();
		data.reduceToSingleDimension(dim);

		for (long ts : splits.keySet()) {
			OpenWareValue val = new OpenWareValue(ts);
			val.addValueDimension(data.getValueTypes().get(0)
					.createValueForDimension(generateStatistic(splits.get(ts).value(), op, 0)));
			data.value().add(val);
		}
		return data;
	}

	public OpenWareDataItem movingAverage(OpenWareDataItem data, JSONObject params) throws Exception {
		if (params.has("dimension") && params.has("window")) {
			return movingAverage(data, params.getInt("dimension"), params.getInt("window"));
		} else {
			throw new IllegalArgumentException(
					"Missing parameters for smoothing(Required: dimension, window)! Provided:\n" +
												params.toString(2));
		}
	}

	public OpenWareDataItem movingAverage(OpenWareDataItem data, int dim, int windowSize) {
		List<OpenWareValue> toProcess = data.value();
		OpenWareDataItem toReturn = data.cloneItem();
		toReturn.value().clear();
		for (int i = 0; i < data.getValueTypes().size(); i++) {
			if (i != dim) {
				toReturn.getValueTypes().remove(i);
			}
		}
		if (data.value().size() < windowSize) {
			return data;
		}
		double sum = 0;
		double vals[] = new double[windowSize];
		int counter = 0;
		for (OpenWareValue val : toProcess) {
			int index = (counter++) % windowSize;
			double lastVal = vals[index];
			double newVal = (double) val.get(dim).value();
			vals[index] = newVal;
			sum = sum - lastVal + newVal;

			if (counter < windowSize) {
				continue;
			}
			OpenWareValue v = new OpenWareValue(val.getDate());
			v.addValueDimension(toReturn.getValueTypes().get(0).createValueForDimension(sum / (double) windowSize));
			toReturn.value().add(v);

		}
		return toReturn;
	}
}
