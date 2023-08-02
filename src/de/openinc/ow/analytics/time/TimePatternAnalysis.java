package de.openinc.ow.analytics.time;

import java.util.HashMap;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;
import de.openinc.ow.helper.DataTools;

public class TimePatternAnalysis {

	public static Dataset frequencyForTimeSlot(Dataset raw, long timeSlotInMs) {
		Dataset res = new Dataset();
		HashMap<Long, Integer> counter = new HashMap<>();
		long min = Long.MAX_VALUE;
		long max = 0;
		for(Instance i:raw) {
			min = Math.min(min, i.getTime());
			max = Math.max(max, i.getTime());
			counter.put(DataTools.floorDate(i.getTime(),timeSlotInMs), (counter.getOrDefault(DataTools.floorDate(i.getTime(),timeSlotInMs),0))+1);
		}
		min = DataTools.floorDate(min, timeSlotInMs);
		max = DataTools.floorDate(max,timeSlotInMs)+timeSlotInMs;
		for(long i = min; i<=max; i=i+timeSlotInMs) {
			res.add(new Instance(i, new double[] {counter.getOrDefault(i, 0)}));
		}
		return res;
	}
}
