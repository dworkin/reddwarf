/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.Serializable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;


/**
 * A concurrent, distributed {@code Map} implementation that
 * automatically manages the mapping storage within the datstore, and
 * supports concurrent writes.  This class is intended as a drop-in
 * replacement for the {@link java.util.HashMap} class as needed.
 * Developers are encouraged to use this class when the size of a
 * {@link java.util.HashMap} causes sufficient contention due to
 * serialization overhead.
 *
 * <p>
 *
 * As the number of mappings increases, the mappings are distributed
 * through multiple objects in the datastore, thereby mitigating the
 * cost of serializing the map.  Furthermore, map operations have been
 * implemented to minimize the locality of change.  As the map grows
 * in size, mutable operations change only a small number of managed
 * objects, thereby increasing the concurrency for multiple writes.
 *
 * <p>
 *
 * This implementation supports the contract that all keys and values
 * must be {@link Serializable}.  If a developer provides a {@code
 * Serializable} key or value that is <i>not</i> a {@code
 * ManagedObject}, this implementation will take responsibility for
 * the lifetime of that object in the datastore.  The developer will
 * be responsible for the lifetime of all {@link ManagedObject} stored
 * in this map.
 *
 * <p>
 *
 * The {@code DistributedHashMap} is implemented as a prefix tree of hash
 * maps, which provides {@code O(log(n))} performance for the
 * following operations: {@code get}, {@code put}, {@code remove},
 * {@code containsKey}, where {@code n} is the number of leaves in the
 * tree (<i>not</i> the number of elements).  Note that unlike most
 * collections, the {@code size} operation is <u>not</u> a constant
 * time operation.  Because of the concurrent nature of the map,
 * determining the size requires accessing all of the leaf nodes in
 * the tree, which takes {@code O(n + log(n))}, where {@code n} is the
 * number of leaves.  The {@code isEmpty} operation, however, is still
 * {@code O(1)}.
 *
 * <p>
 *
 * An instance of {@code DistributedHashMap} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  As the
 * map grows, the number of supported parallel operations will also
 * grow beyond the specified minimum, but this factor ensures that it
 * will never drop below the provided number.  Setting this value too
 * high will waste space and time, while setting it too low will cause
 * conflicts until the map grows sufficiently to support more
 * concurrent operations.  Furthermore, the efficacy of the
 * concurrency depends on the distribution of hash values; keys with
 * poor hashing will minimize the actual number of possible concurrent
 * writes, regardless of the {@code minConcurrency} value.
 *
 * <p>
 *
 * This map marks itself for update as necessary.  Developers
 * <i>should not call {@code markForUpdate} or {@code getForUpdate} on
 * this map ever</i>.  Doing so will eliminate all the concurrency
 * benefits of this class.
 *
 * <p>
 *
 * This class implements all of the optional {@code Map} operations
 * and supports both {@code null} keys and values.  This map provides
 * no guarantees on the order of elements when iterating over the key
 * set, values or entry set.
 * 
 * <p>
 *
 * The iterator for this implemenation will never throw a {@link
 * java.util.ConcurrentModificationException}, unlike many of the
 * other {@code Map} implementations.
 *
 * @since 0.9.4
 * @version 1.5
 *
 * @see Object#hashCode()
 * @see Map
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class DistributedHashMap<K,V> 
    extends AbstractMap<K,V>
    implements Map<K,V>, Serializable, ManagedObject {

    private static final long serialVersionUID = 0x1337L;

    /**
     * The default number of parallel write operations used when none
     * is specified in the constructor.
     */
    private static final int DEFAULT_MINIMUM_CONCURRENCY = 1;    

    /**
     * The split threshold used when none is specified in the
     * constructor.
     */
    // NOTE: through emprical testing, it has been shown that 96
    // elements is the maximum number of PrefixEntries that will fit
    // onto a 4K page.  As the datastore page size changes (or if
    // object level locking becomes used), this should be adjusted to
    // minimize page-level contention from writes the leaf nodes
    private static final int DEFAULT_SPLIT_THRESHOLD = 98;

    /**
     * The merge threshold used when none is specified in the
     * constructor.
     */
    private static final int DEFAULT_MERGE_THRESHOLD = 
	DEFAULT_SPLIT_THRESHOLD / 16;

    /**
     * The default size of the leaf directory when none is specified
     * in the constructor.
     */
    private static final int DEFAULT_DIRECTORY_SIZE = 32;
    
    /**
     * The default number of {@code PrefixEntry} entries per
     * array for a leaf table.
     */
    // NOTE: *must* be a power of 2.
    private static final int DEFAULT_LEAF_CAPACITY = 1 << 8; // 256

    /**
     * The maximum depth of this tree
     */
    // NOTE: this is limited by the number of bits in the prefix,
    //       which currently is implemented as a 32-bit int
    private static final int MAX_DEPTH = 32;
    
    /**
     * The parent node directly above this.  For the root node, this
     * should always be null.
     */
    private ManagedReference parent;

    // NOTE: the leftLeaf and rightLeaf references for a doubly-linked
    //       list for the leaf nodes of the tree, which allows us to
    //       quickly iterate over them without needing to access all
    //       of the intermedate nodes.

    /**
     * The leaf table immediately to the left of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    ManagedReference leftLeaf;

    /**
     * The leaf table immediately to the right of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    ManagedReference rightLeaf;

    /**
     * The lookup directory for deciding which leaf node to access
     * based on a provided prefix.  If this instance is a leaf node,
     * the directory will be {@code null}
     */
    private ManagedReference[] leafDirectory;


    /**
     * The fixed-size table for storing all Map entries.  This table
     * will be {@code null} if this instance is a directory node.
     */
    // NOTE: this is actually an array of type PrefixEntry<K,V> but
    //       generic arrays are not allowed, so we cast the elements
    //       as necessary
    private transient PrefixEntry[] table;    
    
    /**
     * The number of elements in this table.  Note that this is
     * <i>not</i> the total number of elements in the entire tree.
     */
    private int size;

    /**
     * The maximum number of {@code PrefixEntry} entries in this table
     * before it will split this table into two leaf tables.
     *
     * @see #split()
     */
    private int splitThreshold;

    /**
     * The minimum number of {@code PrefixEntry} entries in this table
     * before it will attempt to merge itself with its sibling.
     *
     * @see #merge()
     */
    private int mergeThreshold;

    /**
     * The capacity of the {@code PrefixEntry} table.
     */
    private final int leafCapacity;

    /**
     * The minimum number of concurrent write operations to support.
     * This directly affects the minimum depth of the tree.
     */
    private final int minConcurrency;

    /**
     * The minimum depth of the tree, which is controlled by the
     * minimum concurrency factor
     */
    private final int minDepth;

    /**
     * The depth of this node in the tree.  
     */
    final int depth;

    /**
     * The number of bits used in the leaf directory.  This is
     * calculated based on the {@code directorySize} provided in the
     * constructor.
     *
     * @see #addLeavesToDirectory()
     */
    private final int dirBits;
    
    /** 
     * Constructs an empty {@code DistributedHashMap} at the provided
     * depth, with the specified minimum concurrency, split threshold,
     * merge threshold, and the maximum number of tree levels to
     * collapse.
     *
     * @param depth the depth of this table in the tree
     * @param minConcurrency the minimum number of concurrent write
     *        operations to support
     * @param splitThreshold the number of entries at a leaf node that
     *        will cause the leaf to split
     * @param mergeTheshold the numer of entries at a leaf node that
     *        will cause the leaf to attempt merging with its sibling
     * @param directorySize the maximum number of entries in the
     *        directory.  This is equivalent to the maximum number of
     *        leaves under this node when all children have been added
     *        to it.
     *
     * @throws IllegalArgumentException if: <ul>
     *         <li> {@code depth} is out of the range of valid prefix lengths
     *	       <li> {@code minConcurrency} is non-positive
     *	       <li> {@code splitThreshold} is non-positive
     *	       <li> {@code mergeThreshold} is greater than or equal to
     *	            {@code splitThreshold}
     *         <li> {@code directorySize} is less than two </ul>
     */
    // NOTE: this constructor is currently left package private but
    // future implementations could expose some of these parameters
    // for performance optimization.  At no point should depth be
    // exposed as a public parameter.  directorySize should also not
    // be directly explosed.
    DistributedHashMap(int depth, int minConcurrency, int splitThreshold,
		       int mergeThreshold, int directorySize) {
	if (depth < 0 || depth > MAX_DEPTH) {
	    throw new IllegalArgumentException("Illegal tree depth: " + 
					       depth);	    
	}
	if (minConcurrency <= 0) {
	    throw new IllegalArgumentException("Illegal minimum concurrency: " 
					       + minConcurrency);	    
	}
	if (splitThreshold <= 0) {
	    throw new IllegalArgumentException("Illegal split threshold: " + 
					      splitThreshold);	    
	}
	if (mergeThreshold >= splitThreshold) {
	    throw new IllegalArgumentException("Illegal merge threshold: " + 
					       mergeThreshold);	    
	}
	if (directorySize < 2) {
	    throw new IllegalArgumentException("Illegal directory size: " + 
					       directorySize);	    
	}

	this.depth = depth;
	this.minConcurrency = minConcurrency;
	int tmp;
	for (tmp = 0; (1 << tmp) < minConcurrency; tmp++)
	    ;
	minDepth = tmp;

	size = 0;
	parent = null;
 	leftLeaf = null;
 	rightLeaf = null;

	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];
	leafDirectory = null;

	for (tmp = 1; (1 << tmp) < directorySize; tmp++)
	    ;
	dirBits = tmp;

	this.splitThreshold = splitThreshold;
	this.mergeThreshold = mergeThreshold;

	// Only the root note should ensure depth, otherwise this call
	// causes the children to be created in depth-first fashion,
	// which prevents the leaf references from being correctly
	// established
	if (depth == 0) 
	    ensureDepth(minDepth);
    }

    /** 
     * Constructs an empty {@code DistributedHashMap} with the provided
     * minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations supported
     *
     * @throws IllegalArgumentException if minConcurrency is non positive
     */
    public DistributedHashMap(int minConcurrency) {
	this(0, minConcurrency, DEFAULT_SPLIT_THRESHOLD, 
	     DEFAULT_MERGE_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
    }


    /** 
     * Constructs an empty {@code DistributedHashMap} with the default
     * minimum concurrency (1).
     */
    public DistributedHashMap() {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_MERGE_THRESHOLD, 
	     DEFAULT_DIRECTORY_SIZE);
    }

    /**
     * Constructs a new {@code DistributedHashMap} with the same mappings
     * as the specified {@code Map}, and the default 
     * minimum concurrency (1).
     *
     * @param m the mappings to include
     *
     * @throws NullPointerException if the provided map is null
     */
    public DistributedHashMap(Map<? extends K, ? extends V> m) {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_MERGE_THRESHOLD,
	     DEFAULT_DIRECTORY_SIZE);
	if (m == null)
	    throw new NullPointerException("The provided map is null");
	putAll(m);
    }

    /**
     * Ensures that this node has children of at least the provided
     * minimum depth.
     *
     * @param minDepth the minimum depth of the leaf nodes under this
     *        node
     *
     * @see #split()
     */
    private void ensureDepth(int minDepth) {
	
 	if (depth >= minDepth)
 	    return;
 	else {
	    // rather than split repeatedly, this method inlines all
	    // splits at once and links the children together.  This
	    // is much more efficient

	    table = null; // this node is no longer a leaf
	    leafDirectory = 
		new ManagedReference[1 << Math.min(MAX_DEPTH - depth, dirBits)];
	    
	    // decide how many leaves to make based on the required
	    // depth.  Note that we never create more than the maximum
	    // number of leaves here.  If we need more depth, we will
	    // use a recursive call to the ensure depth on the leaves.
	    int leafDepthOffset = Math.min(minDepth - depth, dirBits);
	    int numLeaves = 1 << leafDepthOffset;

	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);
	    ManagedReference thisRef = dm.createReference(this);

	    DistributedHashMap[] leaves = new DistributedHashMap[numLeaves];
	    for (int i = 0; i < numLeaves; ++i) {
		leaves[i] = new DistributedHashMap(depth + leafDepthOffset,
						   minConcurrency,
						   splitThreshold,
						   mergeThreshold,
						   1 << dirBits);
		leaves[i].parent = thisRef;
	    }
	    
	    // link them together
	    for (int i = 1; i < numLeaves-1; ++i) {
		leaves[i].leftLeaf = dm.createReference(leaves[i-1]);
		leaves[i].rightLeaf = dm.createReference(leaves[i+1]);
	    }

	    // edge updating - Note that since there are guaranteed to
	    // be at least two leaves, these absolute offset calls are safe
	    leaves[0].leftLeaf = leftLeaf;
	    leaves[0].rightLeaf = dm.createReference(leaves[1]);
	    leaves[numLeaves-1].leftLeaf = 
		dm.createReference(leaves[numLeaves-2]);
	    leaves[numLeaves-1].rightLeaf = rightLeaf;

	    // since this node is now a directory, invalidate its
	    // leaf-list references
	    leftLeaf = null;
	    rightLeaf = null;
   
	    int entriesPerLeaf = leafDirectory.length / leaves.length;

	    // lastly, fill the directory with the references
	    for (int i = 0, j = 0; i < leafDirectory.length; ) {
		leafDirectory[i] = dm.createReference(leaves[j]);
		if (++i % entriesPerLeaf == 0)
		    j++;
	    }

	    StringBuffer s = new StringBuffer();
	    for (ManagedReference r : leafDirectory)
		s.append(r.getId().longValue()).append(" ");	    
	    
	    // if the maximum depth of any leaf node under this is
	    // still smaller than the minimum depth, call ensure depth
	    // on the directory nodes under this
	    if (depth + leafDepthOffset < minDepth) {
		for (ManagedReference dirNode : leafDirectory) 
		    dirNode.get(DistributedHashMap.class).ensureDepth(minDepth);
	    }
 	}
    }

    /**
     * Clears the map of all entries in {@code O(n log(n))} time.
     * When clearing, all values managed by this map will be removed
     * from the persistence mechanism.
     */
    public void clear() {
	
	if (table != null) { // this is a leaf node
	    for (PrefixEntry e : table)
		if (e != null)
		    e.unmanage();
	}
	else { // this is a directory node
	    DataManager dm = AppContext.getDataManager();
	    ManagedReference leafRef = null;
	    DistributedHashMap leaf = null; 
	    for (ManagedReference r : leafDirectory) {
		if (leafRef == r) 
		    continue;
		(leaf = ((leafRef = r).get(DistributedHashMap.class))).clear();
		dm.removeObject(leaf);
	    }
	}
	
	if (depth == 0) { // special case for root node;
	    leafDirectory = null;
	    table = new PrefixEntry[leafCapacity];
	    size = 0;
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
	return getEntry(key) != null;
    }

    /**
     * Returns the {@code PrefixEntry} associated with this key.
     *
     * @param key the key that is associated with an {@code Entry}
     *
     * @return the entry associated with the key or {@code null} if no
     *         such entry exists
     */ 
    private PrefixEntry<K,V> getEntry(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);
	for (PrefixEntry<K,V> e = leaf.table[indexFor(hash, leaf.table.length)];
	     e != null; e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && ((k = e.getKey()) == key || 
				   (k != null && k.equals(key)))) {
		return e;
	    }
	}
	return null;
    } 

    /**
     * {@inheritDoc}
     *
     * Note that the execution time of this method grows substatially
     * as the map size increases due to the cost of accessing the data
     * store.
     */
    public boolean containsValue(Object value) {
	for (V v : values()) {
	    if (v == value || (v != null && v.equals(value)))
		return true;
	}
	return false;
    }

    /**
     * Divides the entires in this node into two leaf nodes on the
     * basis of prefix, and then marks this node as an intermediate
     * node.  This method should only be called when the entries
     * contained within this node have valid prefix bits remaining
     * (i.e. they have not already been shifted to the maximum
     * possible precision).
     *
     * @see #addEntry(PrefixEntry, int)
     * @see #collapse(int, int)
     */
    private void split() {
	    
	if (table == null) { // can't split an intermediate node!
	    System.out.println("trying to split directory node!");
	    return;
	}
	
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	DistributedHashMap<K,V> leftChild = 
	    new DistributedHashMap<K,V>(depth+1, minConcurrency, splitThreshold,
					mergeThreshold, 1 << dirBits);
	DistributedHashMap<K,V> rightChild = 
	    new DistributedHashMap<K,V>(depth+1, minConcurrency, splitThreshold,
					mergeThreshold, 1 << dirBits);

	// for the collapse, we to determine what prefix will lead to
	// this node.  Grabbing this from one of our nodes will suffice.
	int prefix = 0x0; // this should never stay at its initial value    

	// iterate over all the entries in this table and assign
	// them to either the right child or left child
	for (int i = 0; i < table.length; ++i) {

	    // go over each entry in the bucket since each might
	    // have a different prefix next
	    for (PrefixEntry<K,V> e = table[i], next = null; e != null; ) {

		prefix = e.hash;
		next = e.next;		
		((((e.hash << depth) >>> 31) == 0) ? leftChild : rightChild).
		    addEntry(e, i);
		e = next;
	    }
	}

	// null out the intermediate node's table as an optimization
	// to reduce serialization time.
	table = null;
	size = 0;
		
	// create the references to the new children
	ManagedReference leftChildRef = dataManager.createReference(leftChild);
	ManagedReference rightChildRef = 
	    dataManager.createReference(rightChild);
	    
	if (leftLeaf != null) {
	    DistributedHashMap leftLeaf_ = 
		leftLeaf.get(DistributedHashMap.class);
	    leftLeaf_.rightLeaf = leftChildRef;
	    leftChild.leftLeaf = leftLeaf;
	    leftLeaf = null;
	}
	
	if (rightLeaf != null) {
	    DistributedHashMap rightLeaf_ = 
		rightLeaf.get(DistributedHashMap.class);
	    rightLeaf_.leftLeaf = rightChildRef;
	    rightChild.rightLeaf = rightLeaf;
	    rightLeaf = null;
	}

	// update the family links
	leftChild.rightLeaf = rightChildRef;
	rightChild.leftLeaf = leftChildRef;
	
	// two cases:
	// 1. this node is the start of a new directory entry
	// 2. this node needs to be subsumed by the directory above it	    
	if (parent == null ||
	    depth % dirBits == 0 ||
	    depth <= minDepth) {
	    // this leaf node is now a directory
	    
	    ManagedReference thisRef = dataManager.createReference(this);
	    rightChild.parent = thisRef;			  
	    leftChild.parent = thisRef;
	    
	    table = null;
	    leafDirectory = 
		new ManagedReference[1 << Math.min(MAX_DEPTH - depth, dirBits)];

	    int right = leafDirectory.length / 2;

	    for (int i = 0; i < right; ++i) {
		leafDirectory[i] = leftChildRef;
		leafDirectory[i | right] = rightChildRef;
	    }
	}
	else {
	    // notify the parent to remove this leaf by following
	    // the provided prefix and then replace it with
	    // references to the right and left children
	    parent.get(DistributedHashMap.class).
		addLeavesToDirectory(prefix, rightChildRef, leftChildRef);
	}	        
    }

    /**
     * Locates the leaf node that is associated with the provided
     * prefix.  
     *
     * @param prefix the initial prefix for which to search 
     *
     * @return the leaf table responsible for storing all entries with
     *         the specified prefix
     */
    private DistributedHashMap<K,V> lookup(int prefix) {

	DistributedHashMap<K,V> leaf = this;
	int original = prefix;

	while (leaf.table == null) {
	    leaf = leaf.directoryLookup(prefix);
	    prefix = original << leaf.depth;
	}

	return leaf;
    }

    /**
     * Uses the provide prefix to look up the appropriate leaf node in
     * the directory.
     *
     * @param prefix the current prefix of the lookup at this
     *        directory node
     *
     * @return the leaf node that is associated with the prefix
     */
    private DistributedHashMap<K,V> directoryLookup(int prefix) {
	
	// first, identify the number of bits in the prefix that will
	// be valid for a directory at this depth, then shift only the
	// significant bits down from the prefix and use those as an
	// index into the directory.
	int index = (prefix >>> (32 - Math.min(dirBits, MAX_DEPTH - depth)));
	return leafDirectory[index].get(DistributedHashMap.class);	
    }		       

    /**
     * Replaces the leaf node pointed to by the provided prefix with
     * directory entries for its children {@code rightChildRef} and
     * {@code leftChildRef}.  This method should only be called by
     * {@link #split()} under the proper conditions.
     *
     * @param prefix the prefix that leads to the node that will be
     *        replaced
     * @param rightChildRef the right child of the node that will be
     *        replaced
     * @param leftChildRef the left child of the node that will be
     *        replaced
     */
    private void addLeavesToDirectory(int prefix, 
				      ManagedReference rightChildRef,
				      ManagedReference leftChildRef) {		
	prefix <<= this.depth;

	int maxBits = Math.min(dirBits, MAX_DEPTH - depth);
	int index = prefix >>> (32 - maxBits);

	// the leaf is under this node, so just look it up using the
	// directory
	DistributedHashMap<K,V> leaf = leafDirectory[index].get(DistributedHashMap.class);

	DataManager dm = AppContext.getDataManager();
	dm.removeObject(leaf);

	// update the leaf node to point to this directory node as
	// their parent
	ManagedReference thisRef = dm.createReference(this);
	rightChildRef.get(DistributedHashMap.class).parent = thisRef;
	leftChildRef.get(DistributedHashMap.class).parent = thisRef;
	
	// how many bits in the prefix are significant for looking up
	// the child
	int sigBits = (leaf.depth - depth);

	// create a bit mask for the parent's signficant bits
	int mask = ((1 << sigBits) - 1) << (maxBits - sigBits);

	// directory index where the left child starts
	int left = index & mask;

	// bit offset between the left and right children in the
	// directory array.
	int off = 1 << ((maxBits - sigBits) - 1);

	// exclusive upper bound for where the left child ends
	int right = left | off;
	
	// update all the directory entires for the prefix
	for (int i = left; i < right; ++i) {
	    leafDirectory[i] = leftChildRef;
	    leafDirectory[i | off] = rightChildRef;
	}

	dm.markForUpdate(this);			
    }

    /**
     * Returns the value to which this key is mapped or {@code null}
     * if the map contains no mapping for this key.  Note that the
     * return value of {@code null} does not necessarily imply that no
     * mapping for this key existed since this implementation supports
     * {@code null} values.  The {@link #containsKey(Object)} method
     * can be used to determine whether a mapping exists.
     *
     * @param key the key whose mapped value is to be returned
     * @return the value mapped to the provided key or {@code null} if
     *         no such mapping exists
     */
    public V get(Object key) {

 	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);
	for (PrefixEntry<K,V> e = leaf.table[indexFor(hash, leaf.table.length)];
	     e != null; 
	     e = e.next) {	    
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		return e.getValue();
	    }
	}
	return null;	
    }

    /**
     * A secondary hash function for better distributing the keys.
     *
     * @param h the initial hash value
     * @return a re-hashed version of the provided hash value
     */
    static int hash(int h) {
	
	/*
	 * This hash function is based on a fixed 4-byte version of
	 * lookup3.c by Bob Jenkins.  See
	 * http://burtleburtle.net/bob/c/lookup3.c for details.  This
	 * is supposed a superior hash function but testing reveals
	 * that it performs slightly worse than the current version
	 * from the JDK 1.6 HashMap.  It is being left in for future
	 * consideration once a more realistic key set can be tested
	 *  
	 * int a, b, c;
	 * a = b = 0x9e3779b9; // golden ratio, (arbitrary initial value)
	 * c = h + 4;
	 * 	
	 * a += h;
	 * 
	 * // mix, with rotations on the original values
	 * c ^= b; c -= ((b << 14) | (b >>> -14));
	 * a ^= c; a -= ((c << 11) | (c >>> -11));
	 * b ^= a; b -= ((a << 25) | (a >>> -25));
	 * c ^= b; c -= ((b << 16) | (b >>> -16));
	 * a ^= c; a -= ((c <<  4) | (c >>>  -4));
	 * b ^= a; b -= ((a << 14) | (a >>> -14));
	 * c ^= b; c -= ((b << 24) | (b >>> -24));
	 * 
	 * return c;
	 */
	
	// the HashMap.hash() function from JDK 1.6
	h ^= (h >>> 20) ^ (h >>> 12);
	return h ^ (h >>> 7) ^ (h >>> 4);

    }

    /**
     * Associates the specified key with the provided value and
     * returns the previous value if the key was previous mapped.
     * This map supports both {@code null} keys and values.
     *
     * @param key the key
     * @param value the value to be mapped to the key
     * @return the previous value mapped to the provided key, if any
     */
    public V put(K key, V value) {

	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);
	AppContext.getDataManager().markForUpdate(leaf);

	int i = indexFor(hash, leaf.table.length);
	for (PrefixEntry<K,V> e = leaf.table[i]; e != null; e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		
		// if the keys and hash match, swap the values
		// and return the old value
		return e.setValue(value);
	    }
	}
	    
	// we found no key match, so add an entry
	leaf.addEntry(hash, key, value, i);

	return null;	
    }

    /**
     * Copies all of the mappings from the provided map into this map.
     * This operation will replace any mappings for keys currently in
     * the map if they occur in both this map and the provided map.
     *
     * @param m the map to be copied
     */
    public void putAll(Map<? extends K, ? extends V> m) {
	for (Map.Entry<? extends K,? extends V> e : m.entrySet())
	    put(e.getKey(), e.getValue());
    }

    /**
     * Adds a new entry at the specified index and determines if a
     * {@link #split()} operation is necessary.
     *
     * @param hash the hash code the of the key
     * @param key the key to be stored
     * @param value the value to be mapped to the key
     * @param the index in the table at which the mapping should be
     *        stored.
     */
    private void addEntry(int hash, K key, V value, int index) {
	PrefixEntry<K,V> prev = table[index];
	int c = 0; PrefixEntry<K,V> t;
	table[index] = new PrefixEntry<K,V>(hash, key, value, prev);

	// ensure that the prefix has enough precision to support
	// another split operation	    
	if ((size++) >= splitThreshold && depth < MAX_DEPTH)
	    split();
    }
    
    /**
     * Adds the provided entry to the the current leaf, chaining as
     * necessary, but does <i>not</i> perform the size check for
     * splitting.  This should only be called from {@link #split()} or
     * {@link #merge()} when adding children entries.
     *
     * @param e the entry that should be added as a new entry to this
     *        leaf
     * @param index the index where the new entry should be put
     */
    private void addEntry(PrefixEntry e, int index) {
 	PrefixEntry<K,V> prev = table[index];
	e.next = prev;
	table[index] = e;
 	size++;
    }
    
    /**
     * Returns whether this map has no mappings.  
     *
     * @return {@code true} if this map contains no mappings
     */
    public boolean isEmpty() {
	return (table != null && size == 0) || (minDepth > 0 && size() == 0);
    }
     
     /**
     * Returns the size of the tree.  Note that this implementation
     * runs in {@code O(n + n*log(n))} time, where {@code n} is the
     * number of nodes in the tree (<i>not</i> the number of elements).
     * Developers should
     *
     * @return the size of the tree
     */
    public int size() {
	// root is leaf node, short-circuit case
  	if (table != null)
 	    return size;

 	int totalSize = 0;
 	DistributedHashMap<K,V> cur = leftMost();
  	totalSize += cur.size;
  	while(cur.rightLeaf != null) {
	    totalSize += 
		(cur = cur.rightLeaf.get(DistributedHashMap.class)).size;
	}
	
  	return totalSize;
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V remove(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);
	
	int i = indexFor(hash, leaf.table.length);
	PrefixEntry<K,V> e = leaf.table[i]; 
	PrefixEntry<K,V> prev = e;
	while (e != null) {
	    PrefixEntry<K,V> next = e.next;
	    Object k;
	    if (e.hash == hash && 
		((k = e.getKey()) == key || (k != null && k.equals(key)))) {
		
		// remove the value and reorder the chained keys
		if (e == prev) // if this was the first element
		    leaf.table[i] = next;
		else 
		    prev.next = next;

		// mark that this table's state has changed
		AppContext.getDataManager().markForUpdate(leaf);
		
		V v = e.getValue();
		
		// if this data structure is responsible for the
		// persistence lifetime of the key or value,
		// remove them from the datastore
		e.unmanage();

		--leaf.size;
		
		// NOTE: this is where we would attempt a merge
		// operation if we decide to later support one
		return v;
		
	    }		
	    prev = e;
	    e = e.next;
	}
	return null;
    }

    /**
     * Returns the left-most leaf table from this node in the prefix
     * tree.
     *
     * @return the left-most child under this node
     */
    DistributedHashMap<K,V> leftMost() {
	// NOTE: the left-most node will have a bit prefix of all
	// zeros, which is what we use when searching for it
	return lookup(0x0);
    }
	       
	
    /**
     * Returns the bucket index for this hash value given the provided
     * number of buckets.
     *
     * @param h the hash value
     * @param length the number of possible indices
     * @return the index for the given hash 
     */
    static int indexFor(int h, int length) {
	return h & (length-1);
    }

    /**
     * An implementation of {@code Map.Entry} that incorporates
     * information about the prefix at which it is stored, as well as
     * whether the {@link DistributedHashMap} is responsible for the
     * persistent lifetime of the value.
     *
     * <p>
     *
     * If an object that does not implement {@link ManagedObject} is
     * stored in the map, then it is wrapped using the {@link
     * ManagedSerializable} utility class so that the entry may have a
     * {@code ManagedReference} to the value, rather than a Java
     * reference.  This causes accesses to the entries to only
     * deserialize the keys.
     *
     * @see ManagedSerializable
     */	
    private static class PrefixEntry<K,V> 
	implements Map.Entry<K,V>, Serializable {

	private static final long serialVersionUID = 1;
	    
	/**
	 * The a reference to key for this entry. The class type of
	 * this reference will depend on whether the map is managing
	 * the key
	 */	
	private ManagedReference keyRef;
	
	/**
	 * A reference to the value.  The class type of this reference
	 * will depend on whether this map is managing the value
	 */ 
	private ManagedReference valueRef;

	/**
	 * If both the key and the value are not {@code ManagedObject}
	 * instances, they will be combined into a single {@link
	 * KeyValuePair} that is refered to by this {@code
	 * ManagedReference}.
	 */
 	private ManagedReference keyValuePairRef;
	
	/**
	 * The next chained entry in this entry's bucket
	 */
	private PrefixEntry<K,V> next;
	
	/**
	 * The hash value for this entry.  This value is also used to
	 * compute the prefix.
	 */
	private final int hash;
	
	/**
	 * Whether the key stored in this entry is actually stored
	 * as a {@link ManagedSerializable}
	 */
	boolean isKeyWrapped;

	/**
	 * Whether the value stored in this entry is actually stored
	 * as a {@link ManagedSerializable}
	 */
	boolean isValueWrapped;

	/**
	 * Whether the key and value are currently stored together in
	 * a {@link KeyValuePair}
	 */
 	boolean isKeyValueCombined;

	/**
	 * Constructs a new {@code PrefixEntry} with the provided hash
	 * code, key, value and chained neighbor.
	 *
	 * @param h the hash code for the key
	 * @param k the key
	 * @param v the value
	 * @param next the next {@link PrefixEntry} in this bucked
	 */
	PrefixEntry(int h, K k, V v, PrefixEntry<K,V> next) { 

	    DataManager dm = AppContext.getDataManager();
	    
	    // if both the key and value are no ManagedObjects, we can
	    // save a get() and createReference() call each by merging
	    // them in a single KeyValuePair
 	    if (isKeyValueCombined = 
 		!(k instanceof ManagedObject) &&
 		!(v instanceof ManagedObject)) {
 		keyValuePairRef = 
 		    dm.createReference(new KeyValuePair<K,V>(k,v));

 		keyRef = null;
 		valueRef = null;
 	    }	 
   
	    // For the key and value, if each is already a
	    // ManagedObject, then we obtain a ManagedReference to the
	    // object itself, otherwise, we need to wrap it in a
	    // ManagedSerializable and get a ManagedReference to that
 	    else {
		keyRef = (isKeyWrapped = !(k instanceof ManagedObject))
		    ? dm.createReference(new ManagedSerializable<K>(k))
		    : dm.createReference((ManagedObject)k);
		
		
		valueRef = (isValueWrapped = !(v instanceof ManagedObject))
		    ? dm.createReference(new ManagedSerializable<V>(v))
		    : dm.createReference((ManagedObject)v);

 		keyValuePairRef = null;
 		isKeyValueCombined = false;
 	    }

	    this.next = next;
	    this.hash = h;
	}
  
	/**
	 * {@inheritDoc}
	 */
	public final K getKey() {
 	    if (isKeyValueCombined)
 		return (K)(keyValuePairRef.get(KeyValuePair.class).getKey());
 	    else
		return (isKeyWrapped)
		    ? ((ManagedSerializable<K>)
		       (keyRef.get(ManagedSerializable.class))).get()
		    : (K)(keyRef.get(Object.class));
	}
    
	/**
	 * Returns the value stored by this entry.  If the mapping has
	 * been removed from the backing map before this call is made,
	 * an {@code ObjectNotFoundException} will be thrown.
	 *
	 * @return the value stored in this entry
	 * @throws ObjectNotFoundException if the element in the
	 *         backing map was removed prior to this call
	 */
	// NOTE: this method will automatically unwrap all value that
	//       the map is responsible for managing
	public final V getValue() {
 	    if (isKeyValueCombined)
 		return (V)(keyValuePairRef.get(KeyValuePair.class).getValue());
 	    else
		return (isValueWrapped) 
		    ? ((ManagedSerializable<V>)
		       (valueRef.get(ManagedSerializable.class))).get()
		    : (V)(valueRef.get(Object.class));
	}

	/**
	 * Replaces the previous value of this entry with the provided
	 * value.  If {@code newValue} is not of type {@code
	 * ManagedObject}, the value is wrapped by a {@code
	 * ManagerWrapper} and stored in the data store.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
	public final V setValue(V newValue) {
	    V oldValue;
	    ManagedSerializable<V> wrapper = null;
 	    KeyValuePair<K,V> pair = null;
	    DataManager dm = AppContext.getDataManager();
	    
	    if (isKeyValueCombined) {
		oldValue = 
		    (pair = keyValuePairRef.get(KeyValuePair.class)).getValue();
	    }	    
 	    // unpack the value from the wrapper prior to
 	    // returning it
	    else {
		oldValue = (isValueWrapped) 
		    ? (wrapper = valueRef.get(ManagedSerializable.class)).get()
		    : (V)(valueRef.get(Object.class));
	    }

	    // if v is already a ManagedObject, then do not put it
	    // in the datastore, and instead get a reference to it	    
	    if (newValue instanceof ManagedObject) {

		// if previously the key and value were combined,
		// split them out.
		if (isKeyValueCombined) {
		    keyRef = dm.createReference(
		        new ManagedSerializable<K>(pair.getKey()));
		    dm.removeObject(pair);
		    isKeyWrapped = true;
		    isKeyValueCombined = false;
		}


		valueRef = dm.createReference((ManagedObject)newValue);
		isValueWrapped = false;
		
		// if the previous value was wrapper, remove the
		// wrapper from the datastore
		if (wrapper != null)
		    dm.removeObject(wrapper);	    
	    }
	    else {
		// NOTE: if the value has been switched from
		// Serializable to ManagedObject back to Serializable,
		// we do not support the case for collapsing back to a
		// KeyValuePair
 		if (isKeyValueCombined)
 		    pair.setValue(newValue);
 		else {
		    // re-use the old wrapper if we have one to avoid
		    // making another create call
		    if (wrapper != null)
			wrapper.set(newValue);
		    // otherwise, we need to wrap it in a new
		    // ManagedSerializable
		    else {		    
			valueRef = dm.createReference(
			    new ManagedSerializable<V>(newValue));
			isValueWrapped = true; // already true in the if-case
		    }
 	        }
	    }
	    return oldValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean equals(Object o) {
	    if (!(o instanceof Map.Entry))
		return false;
	    Map.Entry e = (Map.Entry)o;
	    Object k1 = getKey();
	    Object k2 = e.getKey();
	    if (k1 == k2 || (k1 != null && k1.equals(k2))) {
		Object v1 = getValue();
		Object v2 = e.getValue();
		if (v1 == v2 || (v1 != null && v1.equals(v2)))
		    return true;
	    }
	    return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final int hashCode() {
	    return (keyRef==null   ? 0 : keyRef.hashCode()) ^
		(valueRef==null ? 0 : valueRef.hashCode());
	}
	
	/**
	 * Returns the string form of this entry as {@code
	 * entry}={@code value}.
	 */
	public String toString() {
	    return getKey() + "=" + getValue();
	}

	/**
	 * Removes any {@code Serializable} managed by this entry from
	 * the datastore.  This should only be called from {@link
	 * DistributedHashMap#clear()} and {@link
	 * DistributedHashMap#remove(Object)} under the condition that this
	 * entry's map-managed object will never be reference again by
	 * the map.
	 */
	final void unmanage() {
	    if (isKeyValueCombined) {
		AppContext.getDataManager().
		    removeObject(keyValuePairRef.get(KeyValuePair.class));
	    }
	    else {
		if (isKeyWrapped) {
		    // unpack the key from the wrapper 
		    ManagedSerializable<V> wrapper = 
			keyRef.get(ManagedSerializable.class);
		    AppContext.getDataManager().removeObject(wrapper);
		}
		if (isValueWrapped) {
		    // unpack the value from the wrapper 
		    ManagedSerializable<V> wrapper = 
			valueRef.get(ManagedSerializable.class);
		    AppContext.getDataManager().removeObject(wrapper);
		}
	    }
	}
    }
    
    /**
     * A utlity class for PrefixEntry for storing a {@code
     * Serializable} key and value together in a single {@code
     * ManagedObject}.  By combinging both together, this saves a
     * {@link ManagedReference#get()} call per access.
     */
    private static class KeyValuePair<K,V> 
	implements Serializable, ManagedObject {

	private static final long serialVersionUID = 0x1L;

	private final K key;

	private V value;

	public KeyValuePair(K key, V value) {
	    this.key = key;
	    this.value = value;
	}

	public K getKey() { 
	    return key;
	}
	
	public V getValue() {
	    return value;
	}
	
	public void setValue(V value) {
	    AppContext.getDataManager().markForUpdate(this);
	    this.value = value;
	}

    }


    /**
     * An abstract base class for implementing iteration over entries
     * while subclasses define which data from the entry should be
     * return by {@code next()}.
     */
    private abstract class AbstractEntryIterator<E> 
	implements Iterator<E> {
	
	/**
	 * The next element to return
	 */
	PrefixEntry<K,V> next;

	/**
	 * The table index for the next element to return
	 */
	int index; 

	/**
	 * The current table in which the {@code next} reference is
	 * contained.
	 */
	DistributedHashMap<K,V> curTable;

	/**
	 * Constructs the prefix table iterator.
	 *
	 * @param start the left-most leaf in the prefix tree
	 */
	AbstractEntryIterator(DistributedHashMap<K,V> start) {

	    curTable = start;
	    index = 0;
	    next = null;

	    // load in the first table that has an element
	    while (curTable.size == 0 && curTable.rightLeaf != null) 
		curTable = curTable.rightLeaf.get(DistributedHashMap.class);
		
	    // advance to find the first Entry
	    for (index = 0; index < curTable.table.length &&
		     (next = curTable.table[index]) == null; ++index) 
		;
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean hasNext() {
	    return next != null;
	}

	/**
	 * Returns the next {@code PrefixEntry} found in this map.
	 *
	 * @throws NoSuchElementException if no next entry exists
	 */
	final PrefixEntry<K,V> nextEntry() {
	    PrefixEntry<K,V> e = next;
	    next = next.next;

	    if (e == null) 
		throw new NoSuchElementException();

	    if (next == null) {
		// start at the next index into the current table and
		// search for another element;
		for(index++; index < curTable.table.length && 
			(next = curTable.table[index]) == null; index++) 
		    ;		

		// if still null, we must be at the end of the table,
		// so begin loading in the next table, until another
		// element is found
		if (next == null) {
		    
  		    while (curTable.rightLeaf != null) {
  			curTable = 
			    curTable.rightLeaf.get(DistributedHashMap.class);
			
  			if (curTable.size == 0) 
 			    continue;
	
			// iterate to the next element
			for (index = 0; index < curTable.table.length &&
				 (next = curTable.table[index]) == null; 
			     ++index) 
			    ;		   		    		    
 			break;
 		    }
		}
	    }

	    return e;
	}
	
	/**
	 * This operation is not supported.
	 *
	 * @throws UnsupportedOperationException if called
	 */
	public void remove() {
	    // REMINDER: we probably could support this.
	    throw new UnsupportedOperationException();
	}
		
    }

    /**
     * An iterator over the entry set
     */
    private final class EntryIterator
	extends AbstractEntryIterator<Map.Entry<K,V>> {
	
	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	EntryIterator(DistributedHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public Map.Entry<K,V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map
     */
    private final class KeyIterator extends AbstractEntryIterator<K> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	KeyIterator(DistributedHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public K next() {
	    return nextEntry().getKey();
	}
    }


    /**
     * An iterator over the values in the tree
     */
    private final class ValueIterator extends AbstractEntryIterator<V> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	ValueIterator(DistributedHashMap<K,V> leftMostLeaf) {
	    super(leftMostLeaf);
	}

	/**
	 * {@inheritDoc}
	 */
	public V next() {
	    return nextEntry().getValue();
	}
    }

    /**
     * Returns a {@code Set} of all the mappings contained in this
     * map.  The returned {@code Set} is back by the map, so changes
     * to the map will be reflected by this view.  Note that the time
     * complexity of the operations on this set will be the same as
     * those on the map itself.  
     *
     * @return the set of all mappings contained in this map
     */
    public Set<Entry<K,V>> entrySet() {
	return new EntrySet(this);
    }

    /**
     * An internal-view {@code Set} implementation for viewing all the
     * entries in this map.  
     */
    private final class EntrySet extends AbstractSet<Entry<K,V>> {

	/**
	 * the root node of the prefix tree
	 */
	private final DistributedHashMap<K,V> root;

	EntrySet(DistributedHashMap<K,V> root) {
	    this.root = root;
	}
	    
	public Iterator<Entry<K,V>> iterator() {
	    return new EntryIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    if (!(o instanceof Map.Entry)) 
		return false;
	    Map.Entry<K,V> e = (Map.Entry<K,V>)o;
	    PrefixEntry<K,V> pe = root.getEntry(e.getKey());
	    return pe != null && pe.equals(e);
	}

	public void clear() {
	    root.clear();
	}
	
    }

    /**
     * Returns a {@code Set} of all the keys contained in this
     * map.  The returned {@code Set} is back by the map, so changes
     * to the map will be reflected by this view.  Note that the time
     * complexity of the operations on this set will be the same as
     * those on the map itself.  
     *
     * @return the set of all keys contained in this map
     */
    public Set<K> keySet() {
	return new KeySet(this);
    }

    /** 
     * An internal collections view class for viewing the keys in the
     * map
     */
    private final class KeySet extends AbstractSet<K> {
	  
	/**
	 * the root node of the prefix tree
	 */
	private final DistributedHashMap<K,V> root;

	KeySet(DistributedHashMap<K,V> root) {
	    this.root = root;
	}
	    
	public Iterator<K> iterator() {
	    return new KeyIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    return root.containsKey(o);
	}

	public void clear() {
	    root.clear();
	}
	
    }
	
    /**
     * Returns a {@code Collection} of all the keys contained in this
     * map.  The returned {@code Collection} is back by the map, so
     * changes to the map will be reflected by this view.  Note that
     * the time complexity of the operations on this set will be the
     * same as those on the map itself.
     *
     * @return the collection of all values contained in this map
     */

    public Collection<V> values() {
	return new Values(this);
    }
   
    /**
     * An internal collections-view of all the values contained in
     * this map
     */
    private final class Values extends AbstractCollection<V> {

	/**
	 * the root node of the prefix tree
	 */
	private final DistributedHashMap<K,V> root;

	public Values(DistributedHashMap<K,V> root) {
	    this.root = root;
	}

	public Iterator<V> iterator() {
	    return new ValueIterator(root.leftMost());
	}

	public boolean isEmpty() {
	    return root.isEmpty();
	}

	public int size() {
	    return root.size();
	}

	public boolean contains(Object o) {
	    return containsValue(o);
	}

	public void clear() {
	    root.clear();
	}
    }

    /**
     * Saves the state of this {@code DistributedHashMap} instance to the
     * provided stream.
     *
     * @serialData a {@code boolean} of whether this instance was a
     *             leaf node.  If this instance is a leaf node, this
     *             boolean is followed by a series {@code PrefixEntry}
     *             instances, some of which may be chained.  The
     *             deserialization should count each chained entry
     *             towards the total size of the leaf.
     */
    private void writeObject(java.io.ObjectOutputStream s) 
	throws java.io.IOException {
	// write out all the non-transient state
	s.defaultWriteObject();

	// write out whether this node was a leaf
	s.writeBoolean(table != null);
	
	// if this was a leaf node, write out all the elments in it
	if (table != null) {
	    // iterate over all the table, stopping when all the
	    // entries have been seen
	    PrefixEntry e;
	    for (int i = 0, elements = 0; elements < size; ++i) {
		if ((e = table[i]) != null) {
		    elements++;
		    s.writeObject(table[i]);
		    for (; (e = e.next) != null; ++elements)
			; // count any chained entries
		}
	    }
	}
    }

    /**
     * Reconstructs the {@code DistributedHashMap} from the provided
     * stream.
     */
    private void readObject(java.io.ObjectInputStream s) 
	throws java.io.IOException, ClassNotFoundException {
	
	// read in all the non-transient state
	s.defaultReadObject();

	boolean isLeaf = s.readBoolean();

	// initialize the table if it is a leaf node, otherwise, mark
	// it as null
	table = (isLeaf) ? new PrefixEntry[leafCapacity] : null;
		
	// read in entries and assign them back their positions in the
	// table, noting that some positions may have chained entries
	for (int i = 0; i < size; i++) {
	    PrefixEntry<K,V> e = (PrefixEntry<K,V>) s.readObject();
	    table[indexFor(e.hash, leafCapacity)] = e;
	    for (; (e = e.next) != null; ++i)
		; // count chained entries
	}
    }
}
