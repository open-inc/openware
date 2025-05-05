package de.openinc.ow.analytics.aggregation;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;

/**
 * Created by Martin on 17.08.2016.
 */
public class Descriptives {

	public static Instance getMaxValue(Dataset list, int index) {
		Instance max = null;
		if (list != null && list.size() > 0) {
			max = list.get(0);
			for (Instance am : list) {
				if (am.value(index) > max.value(index))
					max = am;
			}
		}
		return max;
	}

	public static Instance getMinValue(Dataset list, int index) {
		Instance min = null;
		if (list != null && list.size() > 0) {
			min = list.get(0);
			for (Instance am : list) {
				if (am.value(index) < min.value(index))
					min = am;
			}
		}
		return min;
	}

	public static Dataset removeOutlier(Dataset data, int index, double threshold) {
		if (data.size() == 0) {
			return data;
		} else {
			double sum = 0;
			double finalsumX = 0;

			int indexCounter = 0;
			for (Instance i : data) {
				double value = i.value(index);
				sum += value;
			}
			double mean = sum / data.size();

			for (int i = 0; i < data.size(); i++) {
				double fvalue = (Math.pow((data.get(i).value(index) - mean), 2));
				finalsumX += fvalue;
			}

			double averageX = finalsumX / data.size();
			double standardDeviation = Math.sqrt(averageX);
			Iterator<Instance> it = data.iterator();
			Dataset erg = new Dataset();
			while (it.hasNext()) {
				Instance curr = it.next();
				if (threshold * standardDeviation >= Math.sqrt(Math.pow((curr.value(index) - mean), 2))) {
					erg.add(curr);
				}
			}

			return erg;
		}
	}

	public static Dataset smoothDataMovingAverage(Dataset data, int windowSize, int index)
			throws IllegalArgumentException {
		Dataset smoothed = new Dataset();
		if (windowSize > data.size()) {
			throw new IllegalArgumentException("Window Size must not be larger than dataset");
		}
		for (int i = 0; i < data.size() - 1; i++) {
			double sum = 0;
			int size = 1;
			long time = 0;
			if (i < windowSize - 1) {
				size = i + 1;
				for (int j = 0; j <= i; j++) {
					sum += data.get(j).value(index);
				}
			} else {
				size = windowSize;
				for (int j = 0; j < windowSize - 1; j++) {
					sum += data.get(i - j).value(index);
				}
			}
			smoothed.add(new Instance(data.get(i).getTime(), new double[] { (double) sum / (double) size }));
		}
		return smoothed;
	}

	public static Dataset movingKNearest1D(Dataset data, double distance, int index) {
		Dataset histogram = new Dataset();
		Vector<Instance> temp = new Vector<Instance>();
		Comparator<Instance> comp = new Comparator<Instance>() {
			@Override
			public int compare(Instance o1, Instance o2) {
				if (o1.value(index) - o2.value(index) > 0) {
					return 1;
				}
				if (o1.value(index) - o2.value(index) < 0) {
					return -1;
				}
				return 0;
			}
		};

		temp.addAll(data);
		temp.sort(comp);
		Vector<Instance> temp2 = (Vector<Instance>) temp.clone();
		Iterator<Instance> it = temp.iterator();

		int i = 0;
		double last = -1;
		while (it.hasNext()) {

			Instance current = it.next();
			if (last == current.value(index)) {
				continue;
			}
			Iterator<Instance> itinner = temp2.iterator();
			int j = 0;
			while (itinner.hasNext()) {
				Instance inner = itinner.next();
				if ((inner.value(index) + distance) - current.value(index) < 0) {
					itinner.remove();
					continue;
				}
				if (inner.value(index) - current.value(index) > distance) {
					break;
				}
				j++;
			}
			histogram.add(new Instance(new double[] { current.value(index), (double) j }));
			last = current.value(index);
		}
		return histogram;
	}
}
