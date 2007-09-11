/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.app.util;

import java.io.Serializable;

import java.math.BigInteger;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
 * A concurrent, distributed implementation of {@code java.util.Map}.
 * The internal structure of the map is separated into distributed
 * pieces, which reduces the amount of data any one operation needs to
 * access.  The distributed structure increases the concurrency and
 * allows for parallel write operations to successfully complete.
 * 
 * <p>
 *
 * Developers may use this class as a drop-in replacement for the
 * {@link java.util.HashMap} class for use in a {@link ManagedObject}.
 * A {@code HashMap} will typically perform better than this class
 * when the number of mappings is small and the objects being stored
 * are small, and when minimal concurrency is required.  As the size
 * of the serialized {@code HashMap} increases, this class will
 * perform significantly better.  Developers are encouraged to profile
 * the size of their map to determine which implementation will
 * perform better.  Note that {@code HashMap} has no implicit
 * concurrency, so this class may perform in situations where multiple
 * tasks need to modify the set concurrently, even if the total number
 * of mappings is small.  Also note that this class should be used
 * instead of other {@code Map} implementations to store {@code
 * ManagedObject} instances.
 *
 * <p>
 *
 * This class marks itself for update as necessary; no additional
 * calls to the {@link DataManager} are necessary when modifying the
 * map.  Developers do not need to call {@code markForUpdate} or
 * {@code getForUpdate} on this map, as this will eliminate all the
 * concurrency benefits of this class.  However, calling {@code
 * getForUpdate} or {@code markForUpdate} can be used if a operation
 * needs to prevent all access to the map.
 *
 * <p>
 *
 * This implementation requires that all keys and values must be
 * {@link Serializable}.  If a key or value is an instance of {@code
 * Serializable} but does not implement {@code ManagedObject}, this
 * class will persist the object as necessary; when such an object is
 * removed from the map, it is also removed from the {@code
 * DataManager}.  If a key or value is an instance of {@code
 * ManagedObject}, the developer will be responsible for removing
 * these objects from the {@code DataManager} when done with them.
 * Developers should not remove these object from the {@code
 * DataManger} prior to removing them from the map.
 *
 * <p>
 *
 * This class provides {@code Serializable} views from the {@link
 * #entrySet()}, {@link #keySet()} and {@link #values()} methods.
 * These views may be shared and persisted by multiple {@code
 * ManagedObject} instances.
 *
 * <p>
 *
 * <a name="iterator"></a>
 * The {@code Iterator} for each view also implements {@code
 * Serializable}.  An single iterator may be saved by a different
 * {@code ManagedObject} instances, which create a distinct copy of
 * the original iterator.  A copy starts its iteration from where the
 * state of the original was at the time of the copy.  However a copy
 * maintains a separate, independent state from the original will
 * therefore not reflect any changes to the original iterator.  These
 * iterators do not throw {@link
 * java.util.ConcurrentModificationException}.  These iterators are
 * stable with respect to the concurrent changes to the associated
 * collection; an iterator will not the same object twice after a
 * change is made.  An iterator may ignore additions and removals to
 * the associated collection that occur before the iteration site.
 *
 * <p>
 *
 * Note that unlike most collections, the {@code size} operation is
 * <u>not</u> a constant time operation.  Because of the asynchronous
 * nature of the map, determining the size requires accessing all of
 * the entries in the tree.  The {@code isEmpty} operation, however,
 * is still {@code O(1)}.
 *
 * <p>
 *
 * An instance of {@code DistributedHashMap} offers one parameters for
 * performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of write operations to support in parallel.  This
 * parameter acts as a hint to the map on how to perform resizing.  As
 * the map grows, the number of supported parallel operations will
 * also grow beyond the specified minimum.  Setting the minimum
 * concurrency too high will waste space and time, while setting it
 * too low will cause conflicts until the map grows sufficiently to
 * support more concurrent operations.
 *
 * <p>
 *
 * Since the expected distribution of objects in the map is
 * essentially random, the actual concurrency will vary.  Developers
 * are stronly encouraged to use hash codes that provide a normal
 * distribution; a large number of collisions will likely reduce the
 * performance.
 *
 * <p>
 *
 * This class implements all of the optional {@code Map} operations
 * and supports both {@code null} keys and values.  This map provides
 * no guarantees on the order of elements when iterating over the key
 * set, values or entry set.
 * 
 * @see Object#hashCode()
 * @see java.util.Map
 * @see Serializable
 * @see ManagedObject
 */
public class DistributedHashMap<K,V> 
    extends AbstractMap<K,V>
    implements Serializable, ManagedObject {
    
    private static final long serialVersionUID = 0x1337L;
    
    /**
     * The split threshold used when none is specified in the
     * constructor.
     */
    // NOTE: through emprical testing, it has been shown that 98
    // elements is the maximum number of PrefixEntries that will fit
    // onto a 4K page.  As the datastore page size changes (or if
    // object level locking becomes used), this should be adjusted to
    // minimize page-level contention from writes the leaf nodes
    private static final int DEFAULT_SPLIT_THRESHOLD = 98;

    /**
     * The default size of the leaf directory when none is specified
     * in the constructor.
     */
    private static final int DEFAULT_DIRECTORY_SIZE = 32;

    /**
     * The default number of parallel write operations used when none
     * is specified in the constructor.
     */
    private static final int DEFAULT_MINIMUM_CONCURRENCY =
	DEFAULT_DIRECTORY_SIZE;    
    
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
    private ManagedReference parentRef;

    // NOTE: the leftLeaf and rightLeaf references for a doubly-linked
    //       list for the leaf nodes of the tree, which allows us to
    //       quickly iterate over them without needing to access all
    //       of the intermedate nodes.

    /**
     * The leaf table immediately to the left of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    ManagedReference leftLeafRef;

    /**
     * The leaf table immediately to the right of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    ManagedReference rightLeafRef;

    /**
     * The lookup directory for deciding which leaf node to access
     * based on a provided prefix.  If this instance is a leaf node,
     * the directory will be {@code null}.  Note that this directory
     * contains both leaf nodes as well as other directory nodes.
     */
    private ManagedReference[] nodeDirectory;

    /**
     * The fixed-size table for storing all Map entries.  This table
     * will be {@code null} if this instance is a directory node.
     */
    // NOTE: this is actually an array of type PrefixEntry<K,V> but
    //       generic arrays are not allowed, so we cast the elements
    //       as necessary
    private transient PrefixEntry[] table;    
    
    /**
     * The monotonic counter that reflects the number of times this
     * instance has been modified.  The version number is used by the
     * {@link ConcurrentIterator} to detect changes between
     * transactions.
     */
    private int version;

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
     * @see #addLeavesToDirectory(int,ManagedReference,ManagedReference)
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
     * @param directorySize the maximum number of entries in the
     *        directory.  This is equivalent to the maximum number of
     *        leaves under this node when all children have been added
     *        to it.
     *
     * @throws IllegalArgumentException if: <ul>
     *         <li> {@code depth} is out of the range of valid prefix lengths
     *	       <li> {@code minConcurrency} is non-positive
     *	       <li> {@code splitThreshold} is non-positive
     *         <li> {@code directorySize} is less than two </ul>
     */
    // NOTE: this constructor is currently left package private but
    // future implementations could expose some of these parameters
    // for performance optimization.  At no point should depth be
    // exposed as a public parameter.  directorySize should also not
    // be directly explosed.
    DistributedHashMap(int depth, int minConcurrency, int splitThreshold,
		       int directorySize) {
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
	version = 0;
	parentRef = null;
 	leftLeafRef = null;
 	rightLeafRef = null;

	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];
	nodeDirectory = null;

	for (tmp = 1; (1 << tmp) < directorySize; tmp++)
	    ;
	dirBits = tmp;

	this.splitThreshold = splitThreshold;

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
	     DEFAULT_DIRECTORY_SIZE);
    }

    /** 
     * Constructs an empty {@code DistributedHashMap} with the default
     * minimum concurrency (32).
     */
    public DistributedHashMap() {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
    }

    /**
     * Constructs a new {@code DistributedHashMap} with the same mappings
     * as the specified {@code Map}, and the default 
     * minimum concurrency (32).
     *
     * @param m the mappings to include
     */
    public DistributedHashMap(Map<? extends K, ? extends V> m) {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
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
    // NOTE: we still collapse the tree above the the minimum depth
    //       just as split() does.  This does not affect the minimum
    //       concurrency, as split() ensure that no write-locks will
    //       ever propagate up to this collapsed portion of the tree.
    private void ensureDepth(int minDepth) {
	
 	if (depth >= minDepth)
 	    return;
 	else {
	    // rather than split repeatedly, this method inlines all
	    // splits at once and links the children together.  This
	    // is much more efficient

	    table = null; // this node is no longer a leaf
	    nodeDirectory = 
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
						   1 << dirBits);
		leaves[i].parentRef = thisRef;
	    }
	    
	    // for the linked list for the leaves
	    for (int i = 1; i < numLeaves-1; ++i) {
		leaves[i].leftLeafRef = dm.createReference(leaves[i-1]);
		leaves[i].rightLeafRef = dm.createReference(leaves[i+1]);
	    }

	    // edge updating - Note that since there are guaranteed to
	    // be at least two leaves, these absolute offset calls are safe
	    leaves[0].leftLeafRef = leftLeafRef;
	    leaves[0].rightLeafRef = dm.createReference(leaves[1]);
	    leaves[numLeaves-1].leftLeafRef = 
		dm.createReference(leaves[numLeaves-2]);
	    leaves[numLeaves-1].rightLeafRef = rightLeafRef;

	    // since this node is now a directory, invalidate its
	    // leaf-list references
	    leftLeafRef = null;
	    rightLeafRef = null;
   
	    int entriesPerLeaf = nodeDirectory.length / leaves.length;

	    // lastly, fill the directory with the references
	    for (int i = 0, j = 0; i < nodeDirectory.length; ) {
		nodeDirectory[i] = dm.createReference(leaves[j]);
		if (++i % entriesPerLeaf == 0)
		    j++;
	    }
	    
	    // if the maximum depth of any leaf node under this is
	    // still smaller than the minimum depth, call ensure depth
	    // on the directory nodes under this
	    if (depth + leafDepthOffset < minDepth) {
		for (ManagedReference dirNode : nodeDirectory) 
		    dirNode.get(DistributedHashMap.class).ensureDepth(minDepth);
	    }
 	}
    }
    
    /**
     * Clears the map of all entries in {@code O(n log(n))} time.
     * When clearing, all non-{@code ManagedObject} key and values
     * persisted by this map will be removed from the {@code
     * DataManager}
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
	    for (ManagedReference r : nodeDirectory) {
		if (leafRef == r) 
		    continue;
		(leaf = ((leafRef = r).get(DistributedHashMap.class))).clear();
		dm.removeObject(leaf);
	    }
	}
	
	if (depth == 0) { // special case for root node;
	    nodeDirectory = null;
	    table = new PrefixEntry[leafCapacity];
	    size = 0;
	}

	version++;
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
    @SuppressWarnings({"unchecked"})
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
     * Note that the execution time of this method grows substantially
     * as the map size increases due to the cost of accessing the data
     * manager.
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
    @SuppressWarnings({"unchecked"})
    private void split() {
	    
	if (table == null) { // can't split an intermediate node!
	    System.out.println("trying to split directory node!");
	    return;
	}
	
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	DistributedHashMap<K,V> leftChild = 
	    new DistributedHashMap<K,V>(depth+1, minConcurrency, splitThreshold,
					1 << dirBits);
	DistributedHashMap<K,V> rightChild = 
	    new DistributedHashMap<K,V>(depth+1, minConcurrency, splitThreshold,
					1 << dirBits);

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
	    
	if (leftLeafRef != null) {
	    DistributedHashMap leftLeaf = 
		leftLeafRef.get(DistributedHashMap.class);
	    leftLeaf.rightLeafRef = leftChildRef;
	    leftChild.leftLeafRef = leftLeafRef;
	    leftLeafRef = null;
	}
	
	if (rightLeafRef != null) {
	    DistributedHashMap rightLeaf = 
		rightLeafRef.get(DistributedHashMap.class);
	    rightLeaf.leftLeafRef = rightChildRef;
	    rightChild.rightLeafRef = rightLeafRef;
	    rightLeafRef = null;
	}

	// update the family links
	leftChild.rightLeafRef = rightChildRef;
	rightChild.leftLeafRef = leftChildRef;
	
	// Decide what to do with this node:

	// This node should form a new directory node in the following
	// cases:
	// 
	// 1. If this node is the root (parent == null)
	//
	// 2. The parent's directory does not have enough bits to add
	//    this node,
	//
	// 3. The minimum concurrency requires a minimum depth to the
	//    tree.  This entails that all nodes below this be
	//    distinct from the parent, so their updates never cause
	//    write-lock contention.  If this node was a leaf node at
	//    the minimum depth, it must become a new directory node
	//    to avoid removing some concurrency by adding itself to
	//    the parent.
	//
	if (parentRef == null ||
	    depth % dirBits == 0 ||
	    depth == minDepth) {

	    // this leaf node will become a directory node
	    ManagedReference thisRef = dataManager.createReference(this);
	    rightChild.parentRef = thisRef;			  
	    leftChild.parentRef = thisRef;
	    
	    table = null;
	    nodeDirectory = 
		new ManagedReference[1 << Math.min(MAX_DEPTH - depth, dirBits)];

	    int right = nodeDirectory.length / 2;

	    for (int i = 0; i < right; ++i) {
		nodeDirectory[i] = leftChildRef;
		nodeDirectory[i | right] = rightChildRef;
	    }
	}
	// In all other cases, this node can be added to its parent
	// directory.
	else {
	    // notify the parent to remove this leaf by following
	    // the provided prefix and then replace it with
	    // references to the right and left children
	    parentRef.get(DistributedHashMap.class).
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
    @SuppressWarnings({"unchecked"})
    private DistributedHashMap<K,V> directoryLookup(int prefix) {
	
	// first, identify the number of bits in the prefix that will
	// be valid for a directory at this depth, then shift only the
	// significant bits down from the prefix and use those as an
	// index into the directory.
	int index = (prefix >>> (32 - Math.min(dirBits, MAX_DEPTH - depth)));
	return nodeDirectory[index].get(DistributedHashMap.class);	
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
    @SuppressWarnings({"unchecked"})
    private void addLeavesToDirectory(int prefix, 
				      ManagedReference rightChildRef,
				      ManagedReference leftChildRef) {		
	prefix <<= this.depth;
	
	int maxBits = Math.min(dirBits, MAX_DEPTH - depth);
	int index = prefix >>> (32 - maxBits);

	// the leaf is under this node, so just look it up using the
	// directory
	DistributedHashMap<K,V> leaf = 
	    nodeDirectory[index].get(DistributedHashMap.class);

	DataManager dm = AppContext.getDataManager();

	// remove the old leaf node
	dm.removeObject(leaf);

	// update the leaf node to point to this directory node as
	// their parent
	ManagedReference thisRef = dm.createReference(this);
	rightChildRef.get(DistributedHashMap.class).parentRef = thisRef;
	leftChildRef.get(DistributedHashMap.class).parentRef = thisRef;
	
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
	    nodeDirectory[i] = leftChildRef;
	    nodeDirectory[i | off] = rightChildRef;
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
    @SuppressWarnings({"unchecked"})
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
    @SuppressWarnings({"unchecked"})
    public V put(K key, V value) {

	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);
	AppContext.getDataManager().markForUpdate(leaf);
	leaf.version++;

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
    @SuppressWarnings({"unchecked"})
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
     @SuppressWarnings({"unchecked"})
    private void addEntry(PrefixEntry e, int index) {
 	PrefixEntry<K,V> prev = table[index];
	e.next = prev;
	table[index] = e;
 	size++;
     }
     
    /**
     * {@inheritDoc}
     */
    public int hashCode() {
	return AppContext.getDataManager().
	    createReference(this).getId().intValue();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
	if (o instanceof DistributedHashMap) {
	    DataManager dm = AppContext.getDataManager();
	    return dm.createReference(this).
		equals(dm.createReference((DistributedHashMap)o));
	}
	return super.equals(o);
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
     * runs in {@code O(n*log(n))} time.  Developers should be
     * cautious of calling this method on large maps, as the execution
     * time grows significantly.
     *
     * @return the size of the tree
     */
    @SuppressWarnings({"unchecked"})
    public int size() {
	// root is leaf node, short-circuit case
	if (table != null)
	    return size;
	
	int totalSize = 0;
	DistributedHashMap<K,V> cur = leftMost();
	totalSize += cur.size;
	while(cur.rightLeafRef != null) {
	     totalSize += 
		 (cur = cur.rightLeafRef.get(DistributedHashMap.class)).size;
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
    @SuppressWarnings({"unchecked"})
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
		leaf.version++;

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
	@SuppressWarnings({"unchecked"})
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
	 * Returns the {@code ManagedReference} for the key used in
	 * this entry.  These references are not guaranteed to stay
	 * valid across transactions and should therefore not be used
	 * except for comparisons.
	 *
	 * @return the {@code ManagedReference} for the key
	 *
	 * @see ConcurrentIterator
	 */
	ManagedReference getKeyRef() {
	    return (isKeyValueCombined) ? keyValuePairRef : keyRef;
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
	@SuppressWarnings({"unchecked"})
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
	 * ManagerWrapper} and stored in the data manager.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
	@SuppressWarnings({"unchecked"})
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
	    return 
		((keyRef==null  ? 0 : keyRef.hashCode()) << 16) ^
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
	 * the data manager.  This should only be called from {@link
	 * DistributedHashMap#clear()} and {@link
	 * DistributedHashMap#remove(Object)} under the condition that
	 * this entry's map-managed object will never be reference
	 * again by the map.
	 */
	@SuppressWarnings({"unchecked"})
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
     * A concurrent, perstistable {@code Iterator} implementation for
     * the {@code DistributeHashMap}.  This implemenation provides the
     * following guarantees: <ul><li>if no modifications occur, all
     * elements will eventually be returned by {@link
     * ConcurrentIterator#next()}. <li>if any modifications occur, an
     * element will be returned at most once from {@code next()}, with
     * no guarantee to the number of elements returned.    
     */
    abstract static class ConcurrentIterator<E>
	implements Iterator<E>, Serializable {	

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the backing trie.  This is
	 * necessary for looking up the leaf between transactions.
	 */
	private final ManagedReference rootRef;
	
	/**
	 * The current index into the cache of hash codes.
	 */
	private int cur;

	/**
	 * The ID of the current leaf that is being accessed.  This is
	 * stored between transactions so that in {@code next()} can
	 * tell when loading the leaf again whether a split has
	 * occured and therefore we need to invalidate our cache and
	 * reload the next round of entries.
	 */
	private BigInteger cachedLeafId;

	/**
	 * The revision number of the leaf that is currently being
	 * examined.  This is used to check whether a leaf has changed
	 * if the map changes from when the last cache was made.
	 */
	private int cachedRevision;

	/**
	 * A cache of all the {@code PrefixEntries} in the leaf that
	 * is currently being examined.  The entries are sorted such
	 * that the hash code that would end on the right-most leaf is
	 * stored at the end of the array.  All entries with equal
	 * hash codes should be together in a single block because of
	 * this.  If a split occurs, this cache is invalid and will be
	 * reloaded by {@link #cacheLeaf(DistributedHashMap)}
	 */
	private PrefixEntry[] leafCache ;

	/**
	 * The hash code of the entry that was last returned
	 */
	private int lastHash;	

	/**
	 * The set of {@code ManagedReference}s for the keys of
	 * entries that have already been seen that also have the same
	 * hash code as the current entry.  These entries have
	 * previously been returned through {@code nextEntry()}.  When
	 * keys have evenly distributed hash codes, this set will
	 * generally have only a single element in it.  In the event
	 * of a collision, this set is necessary to distinguish
	 * previously returned entries if changes are made to the map
	 * between deserializations of the iterator.  
	 *
	 * <p>
	 *
	 * The references contained in this set should <i>never</i>
	 * have {@link ManagedReference#get(Class)} called on them;
	 * instead, they should only be used to test for equality.
	 *
	 * @see PrefixEntry#getKeyRef()
	 */
	private Set<ManagedReference> alreadySeen;
	
	/**
	 * The current leaf that is being accessed.  This is assigned
	 * in {@link #cacheLeaf(DistirbutedHashMap}} and is valid for
	 * only the current transaction.
	 */
	private transient DistributedHashMap curLeaf;

	/**
	 * The next leaf node in the backing trie.  This is assigned
	 * from {@link #loadNextLeaf()} if a non-empty leaf can be
	 * found.
	 */
	private transient DistributedHashMap nextLeaf;

	/**
	 * Constructs a new {@code ConcurrentIterator}.
	 *
	 * @param root the root node of the {@code DistributedHashMap}
	 *        trie.
	 */
	public ConcurrentIterator(DistributedHashMap root) {

	    // keep a reference to the root so we can look up leaves
	    rootRef = AppContext.getDataManager().createReference(root);
	    
	    // NOTE: try to minimize the serialization cost of this
	    // map by keeping it small.
	    alreadySeen = new HashSet<ManagedReference>(2,2f);
	    lastHash = 0; // 0 is the left-most hash

	    // cache the initial contents of the left-most leaf
	    DistributedHashMap leftLeaf = root.leftMost();
	    cacheLeaf(leftLeaf);
	}

	/**
	 * Caches the hash codes for all the entries of the leaf in
	 * {@code hashCache} and assigns {@code cachedLeafId} and
	 * {@code curLeaf} based on the provided leaf.
	 *
	 * @param leaf the leaf that should be cached
	 */
	private void cacheLeaf(DistributedHashMap leaf) {

	    leafCache = new PrefixEntry[leaf.size];
	    int i = 0;
	    for (PrefixEntry e : leaf.table) {
		if (e != null) {
		    leafCache[i++] = e;
		    for (; (e = e.next) != null; leafCache[i++] = e)
			; // cache any chained entries
		}
	    }
	    
	    cur = 0;

	    // we need this sorted so we can use the last element in
	    // it as a reference for the next leaf in case this node
	    // is split between next() calls
	    Arrays.sort(leafCache, HashComparator.INSTANCE);
	    curLeaf = leaf;
	    cachedLeafId = 
		AppContext.getDataManager().createReference(curLeaf).getId();
	    cachedRevision = curLeaf.version;
	}

	/**
	 * Ensures that the {@code ConcurrentIterator#hashCache} is up
	 * to date and that all the hash codes contained within are
	 * valid for the current leaf.  Calling this method will set
	 * {@ConcurrentIterator#curLeaf} if was not previously set.
	 */
	private void ensureCacheCoherence() {
	    
	    // curLeaf is only initialized when the cache is coherent
	    // or when a leaf has already been manually loaded so we
	    // can immedately returned in this case
	    if (curLeaf != null) 
		return;

	    if (leafCache.length == 0) {
		// if the cache has zero length, then the iterator
		// was initialized based on an empty map,
		// therefore we should start trying to cache from
		// the left most leaf
		curLeaf = rootRef.get(DistributedHashMap.class).
		    leftMost();

		cacheLeaf(curLeaf);
	    }
	    else {
		// NOTE: we have to use the last element in the array
		// to look up our current leaf.  Otherwise, if the
		// leaf split (possibly multiple times) the current
		// leaf might point to any of the left children.  We
		// need it to point to the left most to ensure that
		// the iterator does not repeat any elements during
		// iteration.		    
		curLeaf = rootRef.get(DistributedHashMap.class).
		    lookup(leafCache[leafCache.length-1].hash);

		// after looking up the new leaf check if this is the
		// leaf that was previously cached.  Also check that
		// it hasn't been modified since we last cached it
		if (AppContext.getDataManager().createReference(curLeaf).
		    getId().equals(cachedLeafId) &&
		    curLeaf.version == cachedRevision)
		    // cache still valid
		    return;

		// otherwise, we had either a different or modified
		// leaf so we need to recache it

		// mark whether whether the next call should load a
		// new leaf
		boolean atEnd = leafCache.length == cur;

		// save the current hash that we are on so we can
		// restore cur after caching.  Or if were at the end,
		// save the last hash we saw
		int curHash = leafCache[(atEnd) ? cur - 1 : cur].hash;
		
		// recache based on the new leaf
		cacheLeaf(curLeaf);

		// search for the old hash code so we can start from
		// where we left off, or the next highest value in
		// case the entry with the previous hash code was
		// stored in a left child

		// use a binary search to locate where the hash code
		// would have been placed
		int low = 0;
		int high = leafCache.length - 1;

		while (low <= high) {
		    int mid = (low + high) >>> 1;
		    int midHash = leafCache[mid].hash;
		    
		    if (midHash < curHash)
			low = mid + 1;
		    else if (midHash > curHash)
			high = mid - 1;
		    else {
			cur = (atEnd) ? mid + 1 : mid;
			return;
		    } 
		}

		// low is now where the entry would have been
		// inserted
		cur = (atEnd) ? low + 1 : low; 
	    }
	}
    
	/**
	 * {@inheritDoc}
	 */
	public boolean hasNext() {

	    // check that the cache is up to date
	    ensureCacheCoherence();

	    // check for the edge case of multiple entries with the
	    // same hash codes.  Advance the cur pointer while
	    // checking to avoid having to perform these checks
	    // needlessly later
	    for (; cur < leafCache.length && 
		     alreadySeen.contains(leafCache[cur].getKeyRef()); cur++)
		;
	    
	    // cur should now point to an entry we haven't seen or
	    // it should be equal to the length of the cache if we
	    // have seen everything in the list		
	    if (cur < leafCache.length)
		return true;
	    
	    // start loading leaves until either no more can be
	    // found or we find a non-empty one
	    return loadNextLeaf();			    
	}

	/**
	 * Searches the leaves for the next non-empty leaf and assigns
	 * it to {@code nextLeaf}, returning {@code true} if a
	 * non-empty leaf was found.
	 *
	 * @return {@code true} if a non-empty leaf was found and
	 *         assigned to {@code nextLeaf}
	 */
	private boolean loadNextLeaf() {
	    // when looking up the current leaf, use the last hash
	    // code seen if the current leaf is now empty.  Otherwise
	    // use the right-most hash code in the sorted array to
	    // decide what leaf we're on.
	    DistributedHashMap 	curLeaf = 
		rootRef.get(DistributedHashMap.class).
		lookup((leafCache.length == 0) 
		       ? lastHash 
		       : leafCache[leafCache.length-1].hash);
	    
	    DistributedHashMap nextLeaf = curLeaf;

	    while (nextLeaf.rightLeafRef != null &&
		   (nextLeaf = 
		    nextLeaf.rightLeafRef.get(DistributedHashMap.class)).size == 0)
		;
	    
	    // check that we were able to find another leaf other than
	    // this leaf and that its size is non-zero
	    if ((curLeaf.rightLeafRef != null && 
		 curLeaf.rightLeafRef.equals(nextLeaf.rightLeafRef)) ||
		 curLeaf.rightLeafRef == nextLeaf.rightLeafRef ||
		nextLeaf.size == 0)
		return false;
	    else {
		this.nextLeaf = nextLeaf;
		return true;
	    }
	}

	/**
	 * Returns the next entry in the {@code DistributedHashMap}.
	 * Note that due to the concurrent nature of this iterator,
	 * this method may skip elements that have been added after
	 * the iterator was constructed.  Likewise, it may return new
	 * elements that have been added.  This implementation is
	 * guaranteed never to return an element more than once.
	 *
	 * <p>
	 *
	 * This method will never throw a {@link
	 * java.util.ConcurrentModificatinException}.
	 *
	 * @return the next entry in the {@code DistributedHashMap}
	 *
	 * @throws NoSuchElementException if no further entries exist
	 */
	public Entry nextEntry() {

	    ensureCacheCoherence();

	    if (cur >= leafCache.length) {
		if (nextLeaf == null && !loadNextLeaf())
		    throw new NoSuchElementException();
		else {
		    // cache the contents of the next leaf, which was
		    // assigned during a call to loadNextLeaf()
		    cacheLeaf(nextLeaf);
		    nextLeaf = null;
		}
	    }
	    
	    PrefixEntry e = leafCache[cur++];

	    // check that we don't return an element that we have
	    // already seen.
	    if (e.hash == lastHash) {
		
		// hasNext() should have done this loop for us if it
		// was called, but in case it wasn't called, loop
		// through the cache until we find an entry we haven't
		// seen
		while (alreadySeen.contains(e.getKeyRef()) && 
		       cur < leafCache.length)
		    e = leafCache[cur++];

		// This is a very rare case where we've performed
		// multiple next() calls in a row with no hasNext()
		// between, and where the last keys in the leafCache
		// all have the same hash code, but we've seen them
		// all before - which could only happen due to
		// additions and removals of the same key, which
		// causes a recaching of the leaf that resets cur back
		// to the start of that hash code
		if (cur == leafCache.length && 
		    alreadySeen.contains(e.getKeyRef())) {
		    
		    // in this case, we rely on a recursive call to
		    // nextEntry() to do the appropriate actions for
		    // loading the next leaf and setting all the other
		    // state.  There should be at most one recursive
		    // call in the stack when doing this, as this call
		    // will load a new leaf, which it guaranteed to
		    // have a different hash code, or it will throw a
		    // NoSuchElementException
		    return nextEntry();
		}
	    }
	    else {
		alreadySeen.clear();
	    }
	    
	    alreadySeen.add(e.getKeyRef());
	    lastHash = e.hash;
	    return e;	    
	}

	/**
	 * This operation is not supported.
	 *
	 * @throws UnsupportedOperationException if called
	 */
	public void remove() {
	    throw new UnsupportedOperationException();
	}

	/**
	 * Saves the state of the {@code Iterator} to the stream.	 
	 */
	private void writeObject(java.io.ObjectOutputStream s)
	    throws java.io.IOException {
	    s.defaultWriteObject();
	}

	/**
	 * Reconstitutes the {@code Iterator} from the stream.
	 */
	private void readObject(java.io.ObjectInputStream s) 
	    throws java.io.IOException, ClassNotFoundException {	    
	    s.defaultReadObject();

	    // initialize the transient fields to null
	    curLeaf = null;
	    nextLeaf = null;
	}  

	/**
	 * A comparator for sorting {@code PrefixEntry} entries
	 * according to how their hash codes would be distirbuted in
	 * the trie.  When sorting an array, the largest value after
	 * sorting will be the right-most entry of the array in the
	 * trie, while the lowest value will be the left-most.
	 */
	private static class HashComparator implements Comparator<PrefixEntry> {
	    
	    /**
	     * Singleton instance since this class is immutable.
	     */
	    static final HashComparator INSTANCE = new HashComparator();

	    public int compare(PrefixEntry t1, PrefixEntry t2) {
		int i = t1.hash;
		int j = t2.hash;
		// if both hash codes have the same sign, the do a
		// numeric comparison, otherwise return the opposite
		// sign of the first, which ensures that negative
		// values are greater than positive values.
		//
		// NOTE: the numeric comparison works because the
		// integers are stored as 2's compelment, so a hash of
		// -1 in binary is all 1's, which is the right-most
		// entry in the trie, so it should be at the
		// right-most in the array.
		return ((i & 0x80000000) == (j & 0x80000000)) 
		    ? i - j
		    : -i;
	    }

	    public boolean equals(Object o) {
		return o instanceof HashComparator;
	    }
	}
    }

    /**
     * An iterator over the entry set
     */
    private static class EntryIterator<K,V> 
	extends ConcurrentIterator<Entry<K,V>> {
	
	private static final long serialVersionUID = 0x2L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie.
	 */
	EntryIterator(DistributedHashMap<K,V> root) {
	    super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings({"unchecked"})
	public Map.Entry<K,V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map
     */
    private static final class KeyIterator<K,V> extends ConcurrentIterator<K> {

	private static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie.
	 */
	KeyIterator(DistributedHashMap<K,V> root) {
	    super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings({"unchecked"})
	public K next() {
	    return ((Entry<K,V>)nextEntry()).getKey();
	}
    }


    /**
     * An iterator over the values in the tree
     */
    private static final class ValueIterator<K,V> 
	extends ConcurrentIterator<V> 
	implements Serializable {

	public static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie.
	 */
	ValueIterator(DistributedHashMap<K,V> root) {
	    super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings({"unchecked"})
	public V next() {
	    return ((Entry<K,V>)nextEntry()).getValue();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all
     * the mappings contained in this map.  The returned {@code Set}
     * is backed by the map, so changes to the map will be reflected
     * by this view.  Note that the time complexity of the operations
     * on this set will be the same as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code
     * Serializable}.  See the <a href="#iterator">javadoc</a> for
     * details.
     *
     * @return the set of all mappings contained in this map
     */
    public Set<Entry<K,V>> entrySet() {
	return new EntrySet<K,V>(this);
    }

    /**
     * An internal-view {@code Set} implementation for viewing all the
     * entries in this map.  
     */
    private static final class EntrySet<K,V>
	extends AbstractSet<Entry<K,V>>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;
	
	/**
	 * A reference to the root node of the prefix tree
	 */
	private final ManagedReference rootRef;

	/**
	 * A cached version of the root node for faster accessing
	 */
	private transient DistributedHashMap<K,V> root;
	

	EntrySet(DistributedHashMap<K,V> root) {
	    this.root = root;
	    rootRef = AppContext.getDataManager().createReference(root);
	}
	
	@SuppressWarnings({"unchecked"})
	private void checkCache() {
	    if (root == null) 
		root = rootRef.get(DistributedHashMap.class);
	}
   
	public Iterator<Entry<K,V>> iterator() {
	    checkCache();
	    return new EntryIterator<K,V>(root);
	}

	public boolean isEmpty() {
	    checkCache();	    
	    return root.isEmpty();
	}

	public int size() {
	    checkCache();	    
	    return root.size();
	}

	@SuppressWarnings({"unchecked"})
	public boolean contains(Object o) {
	    checkCache();
	    if (!(o instanceof Map.Entry)) 
		return false;
	    Map.Entry<K,V> e = (Map.Entry<K,V>)o;
	    PrefixEntry<K,V> pe = root.getEntry(e.getKey());
	    return pe != null && pe.equals(e);
	}

	public void clear() {
	    checkCache();
	    root.clear();
	}	
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all
     * the keys contained in this map.  The returned {@code Set} is
     * backed by the map, so changes to the map will be reflected by
     * this view.  Note that the time complexity of the operations on
     * this set will be the same as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code
     * Serializable}.  See the <a href="#iterator">javadoc</a> for
     * details.
     *
     * @return the set of all keys contained in this map
     */
    public Set<K> keySet() {
	return new KeySet<K,V>(this);
    }

    /** 
     * An internal collections view class for viewing the keys in the
     * map
     */
    private static final class KeySet<K,V> 
	extends AbstractSet<K>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;
	  
	/**
	 * A reference to the root node of the prefix tree
	 */
	private final ManagedReference rootRef;

	/**
	 * A cached version of the root node for faster accessing
	 */
	private transient DistributedHashMap<K,V> root;

	KeySet(DistributedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}
	    
	@SuppressWarnings({"unchecked"})
	private void checkCache() {
	    if (root == null) 
		root = rootRef.get(DistributedHashMap.class);
	}

	public Iterator<K> iterator() {
	    checkCache();
	    return new KeyIterator<K,V>(root);
	}

	public boolean isEmpty() {
	    checkCache();
	    return root.isEmpty();
	}

	public int size() {
	    checkCache();
	    return root.size();
	}

	public boolean contains(Object o) {
	    checkCache();
	    return root.containsKey(o);
	}

	public void clear() {
	    checkCache();
	    root.clear();
	}	
    }    
	
    /**
     * Returns a concurrent, {@code Serializable} {@code Collection}
     * of all the keys contained in this map.  The returned {@code
     * Collection} is backed by the map, so changes to the map will be
     * reflected by this view.  Note that the time complexity of the
     * operations on this set will be the same as those on the map
     * itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code
     * Serializable}.  See the <a href="#iterator">javadoc</a> for
     * details.
     *
     * @return the collection of all values contained in this map
     */

    public Collection<V> values() {
	return new Values<K,V>(this);
    }
   
    /**
     * An internal collections-view of all the values contained in
     * this map
     */
    private static final class Values<K,V> 
	extends AbstractCollection<V>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;
	  
	/**
	 * A reference to the root node of the prefix tree
	 */
	private final ManagedReference rootRef;

	/**
	 * A cached version of the root node for faster accessing
	 */
	private transient DistributedHashMap<K,V> root;

	public Values(DistributedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}

	@SuppressWarnings({"unchecked"})
	private void checkCache() {
	    if (root == null) 
		root = rootRef.get(DistributedHashMap.class);
	}

	public Iterator<V> iterator() {
	    checkCache();
	    return new ValueIterator<K,V>(root);
	}

	public boolean isEmpty() {
	    checkCache();
	    return root.isEmpty();
	}

	public int size() {
	    checkCache();
	    return root.size();
	}

	public boolean contains(Object o) {
	    checkCache();
	    return root.containsValue(o);
	}

	public void clear() {
	    checkCache();
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
    @SuppressWarnings({"unchecked"})
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
