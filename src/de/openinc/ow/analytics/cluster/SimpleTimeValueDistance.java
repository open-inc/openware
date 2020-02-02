package de.openinc.ow.analytics.cluster;



import java.util.Calendar;

import de.openinc.ow.analytics.model.Dataset;
import de.openinc.ow.analytics.model.Instance;

/**
 * Created by Martin on 18.04.2016.
 */
public class SimpleTimeValueDistance implements DistanceMeasure {
    public static final long DAILY =(1000l*60l*60l*24l);
    public static final long WEEKLY =(24l*1000l*60l*60l*7l);
    public static final long NO_CYCLE=-1;
    private long cycle=DAILY;
    private long mode;
    long maxTS=0l;
    long minTs = Long.MAX_VALUE;
    long interval;
    double energyRange;
    double maxEnergy=0;
    double minEnergy=Double.MAX_VALUE;
    private int index;

    public SimpleTimeValueDistance(Dataset data, long cycle, int index){
        this.index = index;
    	this.mode = cycle;
        for(Instance x : data){
            if(cycle==NO_CYCLE){
                maxTS= (long)Math.max(maxTS, (x.getTime()));
                minTs= (long)Math.min(minTs, (x.getTime()));
            }else{
                maxTS= (long)Math.max(maxTS, (x.getTime()%cycle));
                minTs=0l;
            }

            maxEnergy = Math.max(maxEnergy,x.value(index));
            minEnergy = Math.min(minEnergy,x.value(index));
        }
        interval = maxTS-minTs;
        energyRange = maxEnergy-minEnergy;
        if(cycle == NO_CYCLE){
            this.cycle = maxTS+1l;
        }else{
            this.cycle = cycle;
        }
   /*
    System.out.println("MaxTs:" + maxTS
    +"\nMaxEnergy:" + maxEnergy
    +"\nCycle:" + this.cycle);
    */
    }

    public double measure(Instance instance, Instance instance1) {
        double energy_normed = Math.abs(((instance.value(index)-instance1.value(index))/maxEnergy))*100;// Energie normalisiert auf 0-100;
        double time_normed= 0;
        double time_distance=Math.abs((instance.getTime()-instance1.getTime())%cycle);
        //Distanz im Zyklus ist maximal halbe Zykklus länge, daher die anpassung unten
        if(mode !=NO_CYCLE){
            if(time_distance>cycle/2){
                time_normed = ((cycle - time_distance)/cycle)*100;
            }else{
                time_normed = ((time_distance)/cycle)*100;
            }
        }else{
            time_normed = ((time_distance)/interval)*100;
        }

        double distance = Math.sqrt(Math.pow(energy_normed,2)+Math.pow(time_normed,2));
     //   System.out.println("TS1: " + (((long)instance.value(1))%cycle)+"\nStd2: "+ (((long)instance1.value(1))%cycle) + "\nDiff: "+ time_distance + "("+time_normed+")");
     //   System.out.println("Energy: " + energy_normed+"\nTS: "+ time_normed + "\nDistance: "+ distance);
        return distance;

    }
}
