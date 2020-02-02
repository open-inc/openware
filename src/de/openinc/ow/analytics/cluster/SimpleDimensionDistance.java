package de.openinc.ow.analytics.cluster;

import de.openinc.ow.analytics.model.Instance;

/**
 * Created by Martin on 18.04.2016.
 */
public class SimpleDimensionDistance implements DistanceMeasure {

	private int index = 0;
    
	public SimpleDimensionDistance(int index){
    	this.index = index;
    }
	
	
	@Override
	public double measure(Instance i1, Instance i2) {
		 return Math.abs(i1.value(this.index)-i2.value(this.index));
	}


}
