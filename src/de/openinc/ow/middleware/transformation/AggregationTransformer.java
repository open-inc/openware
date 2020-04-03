package de.openinc.ow.middleware.transformation;

import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONObject;

import de.openinc.ow.OpenWareInstance;
import de.openinc.ow.analytics.dataprocessing.DataSplitter;
import de.openinc.ow.core.api.transformation.TransformationOperation;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

public class AggregationTransformer implements TransformationOperation {
	private static final String id = "transformer.ow.aggregation";
	private OpenWareDataItem result;

	@Override
	public TransformationOperation apply(OpenWareDataItem data, JSONObject params) throws Exception {
		result = createStatistics(data, params);
		return this;
	}

	public OpenWareDataItem createStatistics(OpenWareDataItem data, JSONObject params) throws Exception {
		if (params.has("dimension") && params.has("start") && params.has("end") && params.has("splits")
				&& params.has("operation")) {
			return createStatistics(data, params.getInt("dimension"), params.getString("operation"),
					params.getLong("start"), params.getLong("end"), Math.max(1, params.getInt("splits")));
		} else {
			throw new IllegalArgumentException(
					"Missing parameters for descriptive aggregation(Required: dimension, interval, operation || dimension, start, end, splits, operation)! Provided:\n" +
												params.toString(2));
		}
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

	@Override
	public OpenWareDataItem getResult() {
		return result;
	}
}
