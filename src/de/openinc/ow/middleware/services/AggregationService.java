package de.openinc.ow.middleware.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.analytics.dataprocessing.DataSplitter;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

public class AggregationService {
	private static AggregationService me;

	private AggregationService() {
		me = this;
	}

	public static AggregationService getInstance() {
		if (me == null) {
			new AggregationService();
		}
		return me;
	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, JSONObject params) throws Exception {
		if (params.has("dimension") && params.has("interval") && params.has("operation")) {
			return createStatistics(data, params.getInt("dimension"), params.getString("operation"),
					params.getLong("interval"));
		} else {
			throw new IllegalArgumentException(
					"Missing parameters for descriptive aggregation(Required: dimension, interval, operation)! Provided:\n" +
												params.toString(2));
		}
	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, int dim, String op, long interval) {

		DescriptiveStatistics stats = new DescriptiveStatistics();
		HashMap<Long, List<OpenWareValue>> splits = DataSplitter.split(data, interval);
		List<OpenWareValue> vals = new ArrayList<OpenWareValue>();

		for (long current : splits.keySet()) {
			stats.clear();
			OpenWareValue val = new OpenWareValue(current);
			splits.get(current).forEach(item -> {
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
					return null;
				}
			}
			try {
				if (Double.isFinite(resVal)) {
					val.addValueDimension(data.getValueTypes().get(dim).createValueForDimension(resVal));
					vals.add(val);
				} else {
					val.addValueDimension(data.getValueTypes().get(dim).createValueForDimension(0));
					vals.add(val);
					OpenWareInstance.getInstance().logError("Got Infinite Value while " + op +
															" at date " +
															new Date(current).toLocaleString() +
															" Databatch Size: " +
															stats.getValues().length);
				}
			} catch (IllegalArgumentException e) {
				OpenWareInstance.getInstance().logError("Analytics error. Could parse parameter of request", e);
				return null;
			}
		}
		data.value(vals);
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
