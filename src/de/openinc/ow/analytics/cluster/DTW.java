
package de.openinc.ow.analytics.cluster;

import de.openinc.ow.analytics.model.Instance;

public class DTW {

    protected Instance seq1;
    protected Instance seq2;
    protected int[][] warpingPath;

    protected int n;
    protected int m;
    protected int K;

    protected double warpingDistance;


    public DTW(Instance sample, Instance templete) {
        seq1 = sample;
        seq2 = templete;

        n = seq1.values().size();
        m = seq2.values().size();
        K = 1;

        warpingPath = new int[n + m][2];	// max(n, m) <= K < n + m
        warpingDistance = 0.0;

        this.compute();
    }

    public void compute() {
        double accumulatedDistance = 0.0;

        double[][] d = new double[n][m];	// local distances
        double[][] D = new double[n][m];	// global distances

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                d[i][j] = distanceBetween(seq1.value(i), seq2.value(j));
            }
        }

        D[0][0] = d[0][0];

        for (int i = 1; i < n; i++) {
            D[i][0] = d[i][0] + D[i - 1][0];
        }

        for (int j = 1; j < m; j++) {
            D[0][j] = d[0][j] + D[0][j - 1];
        }

        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                accumulatedDistance = Math.min(Math.min(D[i-1][j], D[i-1][j-1]), D[i][j-1]);
                accumulatedDistance += d[i][j];
                D[i][j] = accumulatedDistance;
            }
        }
        accumulatedDistance = D[n - 1][m - 1];

        int i = n - 1;
        int j = m - 1;
        int minIndex = 1;

        warpingPath[K - 1][0] = i;
        warpingPath[K - 1][1] = j;

        while ((i + j) != 0) {
            if (i == 0) {
                j -= 1;
            } else if (j == 0) {
                i -= 1;
            } else {	// i != 0 && j != 0
                double[] array = { D[i - 1][j], D[i][j - 1], D[i - 1][j - 1] };
                minIndex = this.getIndexOfMinimum(array);

                if (minIndex == 0) {
                    i -= 1;
                } else if (minIndex == 1) {
                    j -= 1;
                } else if (minIndex == 2) {
                    i -= 1;
                    j -= 1;
                }
            } // end else
            K++;
            warpingPath[K - 1][0] = i;
            warpingPath[K - 1][1] = j;
        } // end while
        warpingDistance = accumulatedDistance / K;

        this.reversePath(warpingPath);
    }

    /**
     * Changes the order of the warping path (increasing order)
     *
     * @param path	the warping path in reverse order
     */
    protected void reversePath(int[][] path) {
        int[][] newPath = new int[K][2];
        for (int i = 0; i < K; i++) {
            for (int j = 0; j < 2; j++) {
                newPath[i][j] = path[K - i - 1][j];
            }
        }
        warpingPath = newPath;
    }
    /**
     * Returns the warping distance
     *
     * @return
     */
    public double getDistance() {
        return warpingDistance;
    }

    /**
     * Computes a distance between two points
     *
     * @param p1	the point 1
     * @param p2	the point 2
     * @return		the distance between two points
     */
    protected double distanceBetween(double p1, double p2) {
        return (p1 - p2) * (p1 - p2);
    }

    /**
     * Finds the index of the minimum element from the given array
     *
     * @param array		the array containing numeric values
     * @return				the min value among elements
     */
    protected int getIndexOfMinimum(double[] array) {
        int index = 0;
        double val = array[0];

        for (int i = 1; i < array.length; i++) {
            if (array[i] < val) {
                val = array[i];
                index = i;
            }
        }
        return index;
    }

    /**
     *	Returns a string that displays the warping distance and path
     */
    public String toString() {
        String retVal = "Warping Distance: " + warpingDistance + "\n";
        retVal += "Warping Path: {";
        for (int i = 0; i < K; i++) {
            retVal += "(" + warpingPath[i][0] + ", " +warpingPath[i][1] + ")";
            retVal += (i == K - 1) ? "}" : ", ";

        }
        return retVal;
    }



}
