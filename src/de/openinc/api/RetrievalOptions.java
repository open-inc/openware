package de.openinc.api;

public class RetrievalOptions {
	public String method;
	public int refDim;
	public long samplesize;
	public boolean updateAggregates;

	public RetrievalOptions(String method, int reference_dimension, long samplesize) {
		this.method = method;
		this.refDim = reference_dimension;
		this.samplesize = samplesize;
		this.updateAggregates = false;
	}

}
