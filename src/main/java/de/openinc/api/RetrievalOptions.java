/**
 * A class representing options for retrieving historical data with additional parameters.
 */
package de.openinc.api;

/**
 * The RetrievalOptions class provides options for customizing the retrieval of
 * historical data.
 */
public class RetrievalOptions {

	/**
	 * The method for data retrieval (e.g., aggregation method).
	 */
	public String method;

	/**
	 * The reference dimension for data retrieval.
	 */
	public int refDim;

	/**
	 * The sample size for data retrieval.
	 */
	public long samplesize;

	/**
	 * Flag indicating whether to update aggregates during retrieval.
	 */
	public boolean updateAggregates;

	/**
	 * Constructs a RetrievalOptions object with specified parameters.
	 *
	 * @param method              The method for data retrieval.
	 * @param reference_dimension The reference dimension for data retrieval.
	 * @param samplesize          The sample size for data retrieval.
	 */
	public RetrievalOptions(String method, int reference_dimension, long samplesize) {
		this.method = method;
		this.refDim = reference_dimension;
		this.samplesize = samplesize;
		this.updateAggregates = false;
	}
}
