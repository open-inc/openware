package de.openinc.ow.analytics.dataprocessing;



import javax.xml.crypto.Data;

import de.openinc.ow.analytics.aggregation.Descriptives;
import de.openinc.ow.analytics.model.Dataset;

import java.util.*;

/**
 * Created by Martin on 15.08.2016.
 */
public class DataMerger {

    public static Dataset[] mergeData(Dataset[] data, double threshold, int index){
        Dataset all = new Dataset();
        for(Dataset x: data){
            all.addAll(x);
        }
        all = Descriptives.removeOutlier(all,0,1);
        double DATASET_MAX = Descriptives.getMaxValue(all,index).value(index);
        ArrayList<ArrayList<Dataset>> processed= new ArrayList<ArrayList<Dataset>>();
        testLoop:
        for(Dataset toTest:data){
            for(ArrayList<Dataset> currentList:processed){
                for(Dataset toCompareTo: currentList){
                    double maxToTest=Descriptives.getMaxValue(toTest,index).value(index);
                    double maxToCompare=Descriptives.getMaxValue(toCompareTo,index).value(index);
                    double minToTest=Descriptives.getMinValue(toTest,index).value(index);
                    double minToCompare=Descriptives.getMinValue(toCompareTo,index).value(index);
                    

                    
                    boolean isCovered =minToTest>minToCompare && maxToTest<maxToCompare;
                    boolean quiteSimliar = 1-(Math.min(maxToTest,maxToCompare)/Math.max(maxToTest,maxToCompare))<threshold &&
                            1-(Math.min(minToTest,minToCompare)/Math.max(minToTest,minToCompare))<threshold;
                    boolean topEdge = (Math.abs(maxToCompare-maxToTest)/DATASET_MAX)<threshold && minToTest>minToCompare;
                    boolean bottomEdge = Math.abs(minToCompare-minToTest)/DATASET_MAX<threshold && maxToTest<maxToCompare;

                    if( quiteSimliar||topEdge||bottomEdge|| isCovered){
                        currentList.add(toTest);
                        continue testLoop;
                    }
                }
            }
            ArrayList<Dataset> newListToAdd = new ArrayList<Dataset>();
            newListToAdd.add(toTest);
            processed.add(newListToAdd);
        }
        Dataset[] result = new Dataset[processed.size()];
        int i=0;
        for(ArrayList<Dataset> candidate: processed){
            Dataset merged = new Dataset();
            for(Dataset currentInCandidate:candidate){
                merged.addAll(currentInCandidate);
            }
            result[i++] = merged;
        }
        return result;
    }
    private static Dataset[] mergeRecursive(Dataset[] asd, double threshold,int index,int round){
        Dataset[] cleaned;

            cleaned = new Dataset[asd.length];
            for(int i=0; i<asd.length;i++){
                cleaned[i] = Descriptives.removeOutlier(asd[i],0,2);
            }

        Dataset [] test = DataMerger.mergeData(cleaned,threshold,index);
        if(test.length == asd.length){
            return asd;
        }else{
            //System.out.println("After round "+round+": " +test.length );
            return mergeRecursive(test,threshold,index,(round+1));
        }
    }
    public static Dataset[] mergeRecursive(Dataset[] asd, double threshold, int index){
        return mergeRecursive(asd, threshold,index,0);
    }
}
