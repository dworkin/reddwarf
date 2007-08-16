package com.sun.sgs.app.util;

import java.util.Map;

/**
 * A wrapper class around {@code DistributedHashMap} that exposes the
 * printing methods for examining the internal layout of the tree.
 *
 * @see DistributedHashMap
 */
public class TestableDistributedHashMap<K,V> extends DistributedHashMap<K,V> {

    private static final long serialVersionUID = 0xDL;

    /** 
     * Constructs an empty {@code TestableDistributedHashMap} at the
     * provided depth, with the specified minimum concurrency, split
     * factor and load factor.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations to support
     * @param splitThreshold the number of entries at a leaf node that
     *        will cause the leaf to split
     * @param mergeTheshold the numer of entries at a leaf node that
     *        will cause the leaf to attempt merging with its sibling
     * @param maxCollapse the maximum number of tree levels when
     *        compressing the backing tree
     *
     * @throws IllegalArgumentException if minConcurrency is non positive
     * @throws IllegalArgumentException if the split threshold is non
     *         positive
     * @throws IllegalArgumentException if the merge threshold is
     *         greater than or equal to the split threshold
     * @throws IllegalArgumentException if {@code maxCollapse} is less
     *         than zero
     */
    public TestableDistributedHashMap(int minConcurrency, int splitThreshold, 
				      int mergeThreshold, int maxCollapse) {
	super(0, minConcurrency, splitThreshold, mergeThreshold, maxCollapse);
    }

    /**
     * {@inheritDoc}
     */
    public TestableDistributedHashMap() {
	super();
    }

    /**
     * {@inheritDoc}
     */
    public TestableDistributedHashMap(int minConcurrency) {
	super(minConcurrency);
    }

    /**
     * {@inheritDoc}
     */
    public TestableDistributedHashMap(Map<? extends K, ? extends V> m) {
	super(m);
    }


    /**
     * Returns the minimum depth for any leaf node in the map's
     * backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})
    public int getMinTreeDepth() {	
 	DistributedHashMap<K,V> cur = leftMost();
	int minDepth = cur.depth;
  	while(cur.rightLeaf != null) {
	    minDepth = Math.min(minDepth, 
				(cur = cur.rightLeaf.
				 get(DistributedHashMap.class)).depth);
	}
	return minDepth+1;
    }


    /**
     * Returns the maximum depth for any leaf node in the map's
     * backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})
    public int getMaxTreeDepth() {	
 	DistributedHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
  	while(cur.rightLeaf != null) {
	    maxDepth = Math.max(maxDepth, 
				(cur = cur.rightLeaf.
				 get(DistributedHashMap.class)).depth);
	}
	return maxDepth+1;
    }

    /**
     * Returns the average of all depth for the leaf nodes in the
     * map's backing tree.  The root node has a depth of 1.
     */
    @SuppressWarnings({"unchecked"})    
    public double getAvgTreeDepth() {
 	DistributedHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
	double leaves = 1;
  	while(cur.rightLeaf != null) {
	    maxDepth = Math.max(maxDepth, 
				(cur = cur.rightLeaf.
				 get(DistributedHashMap.class)).depth);
	    leaves++;
	}
	return (maxDepth / leaves) + 1;
    }

    /**
     * {@inheritDoc}
     */
    public String treeString() {
	return super.treeString();
    }

     /**
      * {@inheritDoc}
      */
     public String treeDiagram() {
	 return super.treeDiagram();
     }
     

}