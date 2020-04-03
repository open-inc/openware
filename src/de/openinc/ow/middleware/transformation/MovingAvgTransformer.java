package de.openinc.ow.middleware.transformation;

import java.util.List;

import org.json.JSONObject;

import de.openinc.ow.core.api.transformation.TransformationOperation;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

public class MovingAvgTransformer implements TransformationOperation {
	private OpenWareDataItem res;

	@Override
	public TransformationOperation apply(OpenWareDataItem old, JSONObject params) throws Exception {
		if (params.has("dimension") && params.has("window")) {
			res = movingAverage(old, params.getInt("dimension"), params.getInt("window"));
		} else {
			throw new IllegalArgumentException(
					"Missing parameters for smoothing(Required: dimension, window)! Provided:\n" +
												params.toString(2));
		}
		return this;
	}

	@Override
	public OpenWareDataItem getResult() {
		// TODO Auto-generated method stub
		return res;
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
