package de.openinc.ow.analytics.dataprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.openinc.ow.core.helper.DataConversion;
import de.openinc.ow.core.model.data.OpenWareDataItem;
import de.openinc.ow.core.model.data.OpenWareValue;

/**
 * Created by Martin on 15.08.2016.
 */
public class DataSplitter {

	public static HashMap<Long, List<OpenWareValue>> split(OpenWareDataItem data, long interval) {
		HashMap<Long, List<OpenWareValue>> sets = new HashMap<Long, List<OpenWareValue>>();
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (int i = 0; i < data.value().size(); i++) {
			long floor = DataConversion.floorDate(data.value().get(i).getDate(), interval);
			List<OpenWareValue> temp = sets.getOrDefault(floor, new ArrayList<OpenWareValue>());
			temp.add(data.value().get(i));
			sets.put(floor, temp);
			min = Math.min(floor, min);
			max = Math.max(floor, max);
		}
		for (long j = min; j <= max; j = j + interval) {
			if (sets.get(j) == null) {
				sets.put(j, new ArrayList<OpenWareValue>());
			}
		}
		return sets;
	}

	public static HashMap<Long, List<OpenWareValue>> splitHourly(OpenWareDataItem data, double hours) {
		return split(data, (long) (1000l * 3600l * hours));
	}

	public static HashMap<Long, List<OpenWareValue>> splitDaily(OpenWareDataItem data, double days) {
		return split(data, (long) (1000l * 3600l * 24l * days));
	}

	public static HashMap<Long, List<OpenWareValue>> splitWeekly(OpenWareDataItem data, double weeks) {
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
