package de.openinc.ow.analytics.dataprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.openinc.ow.core.helper.DataConversion;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

/**
 * Created by Martin on 15.08.2016.
 */
public class DataSplitter {

	public static HashMap<Long, OpenWareDataItem> split(OpenWareDataItem data, long start, long end, int buckets) {
		HashMap<Long, OpenWareDataItem> sets = new HashMap<Long, OpenWareDataItem>();
		HashMap<Long, List<OpenWareValue>> bucketData = new HashMap<Long, List<OpenWareValue>>();
		OpenWareDataItem tempItem = data.cloneItem();
		tempItem.value().clear();
		long interval = end - start;
		long bucketInterval = interval / buckets;

		for (OpenWareValue val : data.value()) {
			long bucketIndex = (val.getDate() - start) / bucketInterval;
			List<OpenWareValue> cBucket = bucketData.getOrDefault(bucketIndex,
					new ArrayList<OpenWareValue>());
			bucketData.put(bucketIndex, cBucket);
			cBucket.add(val);
		}
		for (long index : bucketData.keySet()) {
			OpenWareDataItem item = tempItem.cloneItem();
			item.value(bucketData.get(index));
			sets.put(start + (index * bucketInterval), item);
		}
		return sets;
	}

	public static Map<Long, OpenWareDataItem> split(OpenWareDataItem data, long interval) {
		OpenWareDataItem templateItem = data.cloneItem();
		templateItem.value().clear();
		HashMap<Long, OpenWareDataItem> sets = new HashMap<Long, OpenWareDataItem>();
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (int i = 0; i < data.value().size(); i++) {
			long floor = DataConversion.floorDate(data.value().get(i).getDate(), interval);
			OpenWareDataItem temp = sets.getOrDefault(floor, templateItem.cloneItem());
			temp.value().add(data.value().get(i));
			sets.put(floor, temp);
			min = Math.min(floor, min);
			max = Math.max(floor, max);
		}
		for (long j = min; j <= max; j = j + interval) {
			if (sets.get(j) == null) {
				sets.put(j, templateItem.cloneItem());
			}
		}
		return sets;
	}

	public static Map<Long, OpenWareDataItem> splitHourly(OpenWareDataItem data, double hours) {
		return split(data, (long) (1000l * 3600l * hours));
	}

	public static Map<Long, OpenWareDataItem> splitDaily(OpenWareDataItem data, double days) {
		return split(data, (long) (1000l * 3600l * 24l * days));
	}

	public static Map<Long, OpenWareDataItem> splitWeekly(OpenWareDataItem data, double weeks) {
		return split(data, (long) (1000l * 3600l * 24l * 7l * weeks));
	}
	/*
		public static Dataset convertToEpochInstances(Dataset data, long interval, int index) {
			Dataset res = new Dataset();
			ArrayList<Double> temp = new ArrayList<Double>();
			long lastIndex = -1;
			for (int i = 0; i < data.size(); i++) {
				if (lastIndex == -1) {
					lastIndex = data.get(i).getTime() / interval;
					temp.add(data.get(i).value(index));
					continue;
				}
				Instance current = data.get(i);
				long newIndex = current.getTime() / interval;
				if (lastIndex == newIndex) {
					temp.add(current.value(index));
					continue;
				}
				lastIndex = newIndex;
				double[] resArray = new double[temp.size()];
				for (int j = 0; j < temp.size(); j++) {
					resArray[j] = temp.get(j);
				}
				Instance instance = new Instance(resArray);
				res.add(instance);
				temp = new ArrayList<Double>();
				temp.add(current.value(index));
			}
			return res;
		}
	*/
}
