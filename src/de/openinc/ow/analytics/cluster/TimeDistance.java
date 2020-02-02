package de.openinc.ow.analytics.cluster;


import org.joda.time.*;

import de.openinc.ow.analytics.model.Instance;

import java.util.Date;

/**
 * Created by Martin on 15.07.2015.
 */
public class TimeDistance implements DistanceMeasure {

    private TimeRepetitionInterval interval;
    private DateTimeFieldType intervalType;
    private double maxValue;
    public double maxTimeDifference;
    public double minClusterSize;
    private long cycle;

    public enum TimeRepetitionInterval{
        Yearly,
        Weekly,
        Daily,
        Hourly,
        None

    }
    public TimeDistance(TimeRepetitionInterval type){
        this.interval = type;
        if(type ==TimeRepetitionInterval.Yearly){
         cycle =1000l*3600l*24l*365l;
        }
        if(type ==TimeRepetitionInterval.Weekly){
            cycle =1000l*3600l*24l*7l;
        }
        if(type ==TimeRepetitionInterval.Daily){
            cycle =1000l*3600l*24l;
        }
        if(type ==TimeRepetitionInterval.Hourly){
            cycle =1000l*3600l;
        }
        if(type ==TimeRepetitionInterval.None){
            cycle =Long.MAX_VALUE;
        }

    }
    @Override
    public double measure(Instance instance, Instance instance1) {
        double time_distance=Math.abs((instance.getTime()-instance1.getTime())% cycle);
        //Distanz im Zyklus ist maximal halbe Zykklus länge, daher die anpassung unten
        double time_normed;
        if(interval !=TimeRepetitionInterval.None){
            if(time_distance>cycle/2){
                time_normed = ((cycle - time_distance)/cycle)*100;
            }else{
                time_normed = ((time_distance)/cycle)*100;
            }
        }else{
            time_normed = ((time_distance)/cycle)*100;
        }
        return time_normed;
    }
   
}
