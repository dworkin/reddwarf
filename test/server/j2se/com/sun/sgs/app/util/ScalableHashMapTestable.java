package com.sun.sgs.app.util;

import java.util.Map;

/**
 * A wrapper class around {@code ScalableHashMap} that exposes more
 * parameters of the constructor as well as probes for testing the
 * depth of the backing trie.
 *
 * @see ScalableHashMap
 */
public class ScalableHashMapTestable<K,V> extends ScalableHashMap<K,V> {

    private static final long serialVersionUID = 0xDL;

    /** 
     * Constructs an empty {@code ScalableHashMapTestable} with the
     * specified minimum concurrency, split and merge thresholds, and
     * directory size.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations to support
     * @param splitThreshold the number of entries at a leaf node that
     *        will cause the leaf to split
     * @param directorySize the maximum number of entries in the
     *        directory.  This is equivalent to the maximum number of
     *        leaves under this node when all children have been added
     *        to it.
     *
     * @throws IllegalArgumentException if: <ul>
     *         <li> {@code depth} is out of the range of valid prefix lengths
     *	       <li> {@code minConcurrency} is non-positive
     *	       <li> {@code splitThreshold} is non-positive
     */
    public ScalableHashMapTestable(int minConcurrency, int splitThreshold, 
				   int directorySize) {
	super(0, minConcurrency, splitThreshold, directorySize);
    }


    /** 
     * Constructs an empty {@code ScalableHashMapTestable} with the
     * default minimum concurrency.
     */
    public ScalableHashMapTestable() {
	super();
    }


    /** 
     * Constructs an empty {@code ScalableHashMapTestable} with the
     * specified minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations supported
     *
     * @throws IllegalArgumentException if minConcurrency is non positive
     */
    public ScalableHashMapTestable(int minConcurrency) {
	super(minConcurrency);
    }


    /**
     * Constructs a new {@code ScalableHashMapTestable} with the
     * same mappings as the specified {@code Map}, and the default
     * minimum concurrency (32).
     *
     * @param m the mappings to include
     */
    public ScalableHashMapTestable(Map<? extends K, ? extends V> m) {
	super(m);
    }


    /**
     * Returns the minimum depth for any leaf node in the map's
     * backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})
    public int getMinTreeDepth() {	
 	ScalableHashMap<K,V> cur = leftMost();
	int minDepth = cur.depth;
  	while(cur.rightLeafRef != null) {
	    minDepth = Math.min(minDepth, 
				(cur = cur.rightLeafRef.
				 get(ScalableHashMap.class)).depth);
	}
	return minDepth+1;
    }


    /**
     * Returns the maximum depth for any leaf node in the map's
     * backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})
    public int getMaxTreeDepth() {	
 	ScalableHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
  	while(cur.rightLeafRef != null) {
	    maxDepth = Math.max(maxDepth, 
				(cur = cur.rightLeafRef.
				 get(ScalableHashMap.class)).depth);
	}
	return maxDepth+1;
    }

    /**
     * Returns the average of all depth for the leaf nodes in the
     * map's backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})    
    public double getAvgTreeDepth() {
 	ScalableHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
	double leaves = 1;
  	while(cur.rightLeafRef != null) {
	    maxDepth = Math.max(maxDepth, 
				(cur = cur.rightLeafRef.
				 get(ScalableHashMap.class)).depth);
	    leaves++;
	}
	return (maxDepth / leaves) + 1;
    }     

}
