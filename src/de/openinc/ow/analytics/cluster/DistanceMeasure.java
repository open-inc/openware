package de.openinc.ow.analytics.cluster;

import de.openinc.ow.analytics.model.Instance;

public interface DistanceMeasure {

	public double measure(Instance i1, Instance i2);
}
