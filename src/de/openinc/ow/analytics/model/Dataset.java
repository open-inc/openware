package de.openinc.ow.analytics.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.json.JSONArray;
import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.ow.OpenWareInstance;

public class Dataset extends AbstractList<Instance> {
	private List<Instance> data;
	public DescriptiveStatistics stats;

	public Dataset() {
		this.data = new ArrayList<>();
		this.stats = new DescriptiveStatistics();
	}

	public Dataset(List<Instance> instances) {
		this.data = instances;
	}

	public Dataset(double[][] instances) {
		for (double[] instance : instances) {
			this.data.add(new Instance(instance));
		}
	}

	public Dataset(OpenWareDataItem item) {
		this.data = new ArrayList<>();
		for (OpenWareValue val : item.value()) {
			data.add(new Instance(val));
		}
	}

	@Override
	public int size() {
		return this.data.size();
	}

	@Override
	public Instance get(int index) {
		return data.get(index);
	}

	@Override
	public boolean add(Instance e) {
		return data.add(e);
	}

	@Override
	public Instance set(int index, Instance value) {
		return this.data.set(index, value);
	}

	public Instance getDimensionalVector(int dimension) {
		Instance vector = new Instance();
		for (Instance i : this.data) {
			vector.push(i.value(dimension));
		}
		return vector;
	}

	public double getDimensionalAverage(int dimension) {
		double sum = 0;
		for (Instance i : this.data) {
			sum += i.value(dimension);
		}
		return sum / this.data.size();
	}

	public List<OpenWareValue> toValueList() {
		List<OpenWareValue> vals = new ArrayList<OpenWareValue>();
		for (Instance i : data) {
			OpenWareValue val = new OpenWareValue(i.ts);
			int y = 0;
			for (double dVal : i.values) {
				try {
					val.addValueDimension(OpenWareValueDimension
							.createNewDimension("val" + (y++), "", OpenWareNumber.TYPE).createValueForDimension(dVal));
				} catch (Exception e) {
					OpenWareInstance.getInstance()
							.logWarn(this.getClass().getCanonicalName() + ": could not create Number Dimension");
				}
			}
			vals.add(val);
		}
		return vals;
	}

	public JSONArray dataset2JSON() {
		JSONArray res = new JSONArray();
		for (Instance x : this) {
			JSONObject obj = new JSONObject();
			obj.put("time", x.getTime());
			obj.put("value", x.values);
			res.put(obj);
		}
		return res;
	}

}
