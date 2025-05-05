package de.openinc.ow.analytics.cluster;

import java.util.Collections;

import de.openinc.ow.analytics.model.Instance;

/**
 * Created by Martin on 18.04.2016.
 */
public class DTWDistance implements DistanceMeasure{

    public double measure(Instance instance, Instance instance1) {
            DTW dtw = new DTW(instance, instance1);
            return dtw.getDistance();
    }


}
