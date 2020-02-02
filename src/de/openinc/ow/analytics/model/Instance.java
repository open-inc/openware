package de.openinc.ow.analytics.model;





import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.openinc.ow.core.model.data.OpenWareNumber;
import de.openinc.ow.core.model.data.OpenWareValue;
import de.openinc.ow.core.model.data.OpenWareValueDimension;

/**
 * Created by Martin on 15.07.2015.
 */
public class Instance{
    public  String name;
    public  List<Double> values;
    public  long ts;
    
    public Instance(){
    	this.ts = new Date().getTime();
    	this.values= new ArrayList<Double>();
    }

    public Instance(double[] att) {
    	this.ts = new Date().getTime();
    	this.values= new ArrayList<Double>();
    	for(double value:att){
        	values.add(value);
        }
    }
    
    public Instance(OpenWareValue val){
    	this.ts = val.getDate();
    	this.values = new ArrayList<>();
    	for(OpenWareValueDimension dim: val){
    		if(dim instanceof OpenWareNumber){
    			values.add(((OpenWareNumber)dim).value());
    		}
    	}
    }
    public Instance(long ts){
    	this.ts = ts;
    	this.values= new ArrayList<Double>();
    }

    public Instance(long ts,double[] att) {
    	this.ts = ts;
    	this.values= new ArrayList<Double>();
    	for(double value:att){
        	values.add(value);
        }
    }
    
    public String getName(){
        return name;
    };
    public void setName(String name){
        this.name = name;
    }
    public void setStart(long start) {
        this.ts = start;
    }

   
    public long getTime(){
        return ts;
    };
    

    /** Default getter, just insert key that stored the value.
     *
     * @param key
     * @return
     */
    public Double value(int index) {
        return values.get(index);
    }

    /** Default setter of key-value pair, Just add water.
     *
     * @param key
     * @param value
     */
    public void value(int index, Double value) {
        values.set(index, value);
    }
    
    public List<Double> values(){
    	return this.values;
    }

    /** Default deletion function for internal store. Returns the contained value.
     * Todo: Add exception for key unknown.
     *
     * @param key
     * @return
     */
    public double removeValue(int index){
        double val = values.remove(index);
        return val;
    }
    
    public void push(double value){
    	this.values.add(value);
    }
    public double pop(){
    	return this.values.remove(values.size()-1);
    }
    public int size(){
    	return this.values.size();
    }



}
