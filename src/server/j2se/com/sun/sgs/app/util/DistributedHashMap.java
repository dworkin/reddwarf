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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectNotFoundException;


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
 * concurrency where two {@code Task}s running in parallel are able to
 * modify it at the same time, so this class may perform in situations
 * where multiple tasks need to modify the set concurrently, even if
 * the total number of mappings is small.  Also note that this class
 * should be used instead of other {@code Map} implementations to
 * store {@code ManagedObject} instances.
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
 * This implementation requires that all keys and values be instances
 * of {@link Serializable}.  If a key or value is an instance of
 * {@code Serializable} but does not implement {@code ManagedObject},
 * this class will persist the object as necessary; when such an
 * object is removed from the map, it is also removed from the {@code
 * DataManager}.  If a key or value is an instance of {@code
 * ManagedObject}, the developer will be responsible for removing
 * these objects from the {@code DataManager} when done with them.
 * Developers should not remove these object from the {@code
 * DataManager} prior to removing them from the map.
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
 * <a name="iterator"></a> The {@code Iterator} for each view also
 * implements {@code Serializable}.  An single iterator may be saved
 * by a different {@code ManagedObject} instances, which create a
 * distinct copy of the original iterator.  A copy starts its
 * iteration from where the state of the original was at the time of
 * the copy.  However, a copy maintains a separate, independent state
 * from the original and will therefore not reflect any changes to the
 * original iterator.  To share a single {@code Iterator} between
 * multiple {@code ManagedObject} <i>and</i> have the iterator use a
 * consistent view for each, the iterator should be contained within a
 * shared {@code ManagedObject}, such as by wrapping it with a {@link
 * ManagedSerializable}.
 *
 * <p>
 *
 * These iterators do not throw {@link
 * java.util.ConcurrentModificationException}.  An iterator is stable
 * with respect to the concurrent changes to the associated
 * collection.  An iterator may ignore additions and removals to the
 * associated collection that occur before the iteration site.
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
 * An instance of {@code DistributedHashMap} offers one parameter for
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
 * are strongly encouraged to use hash codes that provide a normal
 * distribution; a large number of collisions will likely reduce the
 * performance.
 *
 * <p>
 *
 * This class and its iterator implement all of the optional {@code
 * Map} operations and supports both {@code null} keys and values.
 * This map provides no guarantees on the order of elements when
 * iterating over the key set, values or entry set.
 * 
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
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
     * The default size of the node directory when none is specified
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

    // NOTE: the leftLeafRef and rightLeafRef references form a
    //       doubly-linked list for the leaf nodes of the tree, which
    //       allows us to quickly iterate over them without needing to
    //       access all of the intermediate nodes.

    /**
     * The leaf node immediately to the left of this table if this
     * node is itself a leaf.  If this node is an intermediate node in
     * tree this reference as well as the {@code rightLeafRef} will be
     * {@code null}.
     */
    ManagedReference leftLeafRef;

    /**
     * The leaf node immediately to the left of this table if this
     * node is itself a leaf.  If this node is an intermediate node in
     * tree this reference as well as the {@code rightLeafRef} will be
     * {@code null}
     */
    ManagedReference rightLeafRef;

    /**
     * The lookup directory for deciding which node to access based on
     * a provided prefix.  If this instance is a leaf node, the {@code
     * nodeDirectory} for that instance will be {@code null}.  Note
     * that this directory contains both leaf nodes as well as other
     * directory nodes.
     */
    // NOTE: for a diagram of how this directory is arranged, see
    // Fagin et al. "Extendible Hashing" (1979) p. 321, fig 3
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
     * instance has been modified.  The counter is used by the {@link
     * DistributedHashMap.ConcurrentIterator} to detect changes
     * between transactions.
     */
    private int modifications;

    /**
     * The number of elements in this node's table.  Note that this is
     * <i>not</i> the total number of elements in the entire tree.
     * For a directory node, this should be set to 0.
     */
    private int size;

    /**
     * The maximum number of {@code PrefixEntry} entries in this table
     * before it will split this table into two leaf tables.
     *
     * @see #split()
     */
    private final int splitThreshold;

    /**
     * The capacity of the {@code PrefixEntry} table.
     */
    private final int leafCapacity;

    /**
     * The minimum depth of the tree, which is controlled by the
     * minimum concurrency factor
     *
     * @see #initDepth(int)
     */
    private final int minDepth;

    /**
     * The depth of this node in the tree.  
     */
    final int depth;

    /**
     * The number of bits used in the node directory.  This is
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
     * @param minDepth the necessary depth to support the minimum
     *        number of concurrent write operations to support
     * @param splitThreshold the number of entries at a leaf node that
     *        will cause the leaf to split
     * @param directorySize the maximum number of entries in the
     *        directory.  This is equivalent to the maximum number of
     *        leaves under this node when all children have been added
     *        to it.
     *
     * @throws IllegalArgumentException if: <ul>
     *         <li> {@code depth} is out of the range of valid prefix lengths
     *	       <li> {@code minDepth} is negative
     *	       <li> {@code splitThreshold} is non-positive
     *         <li> {@code directorySize} is less than two </ul>
     */
    // NOTE: this constructor is currently left package private but
    // future implementations could expose some of these parameters
    // for performance optimization.  At no point should depth be
    // exposed for public modification.  directorySize should also not
    // be directly exposed.
    DistributedHashMap(int depth, int minDepth, int splitThreshold,
		       int directorySize) {
	if (depth < 0 || depth > MAX_DEPTH) {
	    throw new IllegalArgumentException("Illegal tree depth: " + 
					       depth);	    
	}
	if (minDepth < 0) {
	    throw new IllegalArgumentException("Illegal minimum depth: " 
					       + minDepth);	    
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
	this.minDepth = minDepth;

	size = 0;
	modifications = 0;
	parentRef = null;
 	leftLeafRef = null;
 	rightLeafRef = null;

	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];
	nodeDirectory = null;

	dirBits = requiredNumBits(directorySize);

	this.splitThreshold = splitThreshold;

	// Only the root note should ensure depth, otherwise this call
	// causes the children to be created in depth-first fashion,
	// which prevents the leaf references from being correctly
	// established
	if (depth == 0) 
	    initDepth(minDepth);
    }

    /**
     * Returns the number of bits needed to represent the specified number of
     * values, which should be greater than zero.
     *
     * @param n the number
     *
     * @return the number of bits
     */
    private static int requiredNumBits(int n) {
	assert n > 0;
	return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    /** 
     * Constructs an empty {@code DistributedHashMap} with the provided
     * minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations supported
     *
     * @throws IllegalArgumentException if minConcurrency is non-positive
     */
    public DistributedHashMap(int minConcurrency) {
	this(0, findMinDepthFor(minConcurrency), DEFAULT_SPLIT_THRESHOLD, 
	     DEFAULT_DIRECTORY_SIZE);
    }

    /** 
     * Constructs an empty {@code DistributedHashMap} with the default
     * minimum concurrency (32).
     */
    public DistributedHashMap() {
	this(0, findMinDepthFor(DEFAULT_MINIMUM_CONCURRENCY), 
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
	this(0, findMinDepthFor(DEFAULT_MINIMUM_CONCURRENCY), 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
	if (m == null)
	    throw new NullPointerException("The provided map is null");
	
	putAll(m);
    }

    /**
     * Returns the minimum depth of the tree necessary to support the
     * provided minimum number of concurrent write operations.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations to perform
     *
     * @return the necessary minimum depth to the tree
     *
     * @throws IllegalArgumentException if minConcurrency is
     *         non-positive
     */
    static int findMinDepthFor(int minConcurrency) {	
	if (minConcurrency <= 0)
	    throw new IllegalArgumentException("Non-positive minimum "+
					       "concurrency: "+ minConcurrency);
	return requiredNumBits(minConcurrency);
    }

    /**
     * Ensures that this node has children of at least the provided
     * minimum depth.  Nodes the above the minimum depth will be added
     * to the nearest directory node.
     *
     * <p>
     *
     * This method is only safe to be used by the constructor once, or
     * recursively from within this method; this ensures that the
     * leaves of the tree are initialized properly.
     *
     * @param minDepth the minimum depth of the leaf nodes under this
     *        node
     *
     * @see #split()
     */
    // NOTE: if this were to be called in a depth-first fashion, with
    // split being called repeatedly, the leaves of the tree of the
    // tree would have their left and right reference improperly
    // initialized.  
    private void initDepth(int minDepth) {
	
 	if (depth >= minDepth)
 	    return;
 	
	// rather than split repeatedly, this method inlines all
	// splits at once and links the children together.  This is
	// much more efficient

	table = null; // this node is no longer a leaf
	nodeDirectory = new ManagedReference[1 << getNodeDirBits()];
	
	// decide how many leaves to make based on the required depth.
	// Note that we never create more than the maximum number of
	// leaves here.  If we need more depth, we will use a
	// recursive call to the ensure depth on the leaves.
	int leafDepthOffset = Math.min(minDepth - depth, dirBits);
	int numLeaves = 1 << leafDepthOffset;
	
	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);
	ManagedReference thisRef = dm.createReference(this);
	
	DistributedHashMap[] leaves = new DistributedHashMap[numLeaves];
	for (int i = 0; i < numLeaves; ++i) {
	    leaves[i] = new DistributedHashMap(depth + leafDepthOffset,
					       minDepth, splitThreshold,
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
	
	// if the maximum depth of any leaf node under this is still
	// smaller than the minimum depth, call ensure depth on the
	// directory nodes under this
	if (depth + leafDepthOffset < minDepth) {
	    for (ManagedReference dirNode : nodeDirectory) 
		dirNode.get(DistributedHashMap.class).initDepth(minDepth);
	}   
    }
    
    /**
     * Returns the maximum number of bits of the hash code that are used for
     * looking up children of this node in the node directory.
     *
     * @return the number of directory bits for this node
     */
    private int getNodeDirBits() {
	/*
	 * If the node is very deep, then the number of bits used to do
	 * directory lookups is limited by the total number of bits.
	 */
	return Math.min(MAX_DEPTH - depth, dirBits);
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
	    ManagedReference prevNodeRef = null;
	    DistributedHashMap node = null; 
	    for (ManagedReference r : nodeDirectory) {
		// skip re-clearing duplicate nodes in the directory
		if (r == prevNodeRef) 
		    continue;
		prevNodeRef = r;
		node = r.get(DistributedHashMap.class);
		node.clear();
		dm.removeObject(node);
	    }
	}
	
	if (depth == 0) { // special case for root node;
	    nodeDirectory = null;
	    table = new PrefixEntry[leafCapacity];
	    size = 0;
	}

	modifications++;
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
	for (PrefixEntry<K,V> e = leaf.getBucket(leaf.indexFor(hash));
	     e != null; e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && ((k = e.getKey()) == key || 
				   (k != null && k.equals(key)))) 
		return e;	    
	}
	return null;
    } 

    /**
     * Returns the first entry in this leaf for the specified index, or {@code
     * null} if there is none.
     *
     * @param index the index
     *
     * @return the entry or {@code null}
     */
    @SuppressWarnings("unchecked")
    private PrefixEntry<K,V> getBucket(int index) {
	return table[index];
    }

    /**
     * Returns the first entry in this leaf, or {@code null} if there are no
     * entries.
     *
     * @return the first entry in this leaf or {@code null}
     */
    PrefixEntry<K,V> firstEntry() {
	for (int i = 0; i < table.length; i++) {
	    PrefixEntry<K,V> entry = getBucket(i);
	    if (entry != null) {
		return entry;
	    }
	}
	return null;
    }

    /**
     * Returns the next entry in this leaf after the entry with the specified
     * hash and key reference, or {@code null} if there are no entries after
     * that position.
     *
     * @param hash the hash code
     * @param keyRef the reference for the entry key
     *
     * @return the next entry or {@code null}
     */
    PrefixEntry<K,V> nextEntry(int hash, ManagedReference keyRef) {
	BigInteger keyId = keyRef.getId();
	for (int i = indexFor(hash); i < table.length; i++) {
	    for (PrefixEntry<K,V> e = getBucket(i); e != null; e = e.next) {
		if (unsignedLessThan(e.hash, hash)) {
		    continue;
		} else if (e.hash != hash ||
			   e.keyRef().getId().compareTo(keyId) > 0)
		{
		    return e;
		}
	    }
	}
	return null;
    }

    /**
     * Compares the arguments as unsigned integers.
     *
     * @param x first value
     * @param y second value
     *
     * @return {@code true} if {@code x} is less than {@code y} when viewed
     *	       as an unsigned integer, else {@code false}
     */
    private static boolean unsignedLessThan(int x, int y) {
	/* Flip the sign bit, so that negative values are considered larger */
	return (x ^ 0x80000000) < (y ^ 0x80000000);
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
     * Divides the entries in this node into two leaf nodes on the
     * basis of prefix, and then marks this node as an intermediate
     * node.  This method should only be called when the entries
     * contained within this node have valid prefix bits remaining
     * (i.e. they have not already been shifted to the maximum
     * possible precision).
     *
     * @see #addEntry
     */
    private void split() {
	    
	if (table == null) { // can't split an intermediate node!
	    return;
	}
	
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	DistributedHashMap<K,V> leftChild = 
	    new DistributedHashMap<K,V>(depth+1, minDepth, splitThreshold,
					1 << dirBits);
	DistributedHashMap<K,V> rightChild = 
	    new DistributedHashMap<K,V>(depth+1, minDepth, splitThreshold,
					1 << dirBits);

	// in order add this node to the parent directory, we to
	// determine the prefix that will lead to this node.  Grabbing
	// a hash code from one of our entries will suffice.
	int prefix = 0x0; // this should never stay at its initial value    

	// iterate over all the entries in this table and assign
	// them to either the right child or left child
	int firstRight = table.length / 2;
	for (int i = 0; i < table.length; ++i) {
	    DistributedHashMap<K,V> child =
		(i < firstRight) ? leftChild : rightChild;
	    PrefixEntry<K,V> prev = null;
	    int prevIndex = 0;
	    PrefixEntry<K,V> e = getBucket(i);
	    while (e != null) {
		prefix = e.hash;
		int index = child.indexFor(e.hash);
		PrefixEntry<K,V> next = e.next;
		/* Chain to the previous node if the index is the same */
		child.addEntry(e, index == prevIndex ? prev : null);
		prev = e;
		prevIndex = index;
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
	// 1. If this node is the root (parent == null). 
	//
	// 2. The directory represents a limited subtree of the trie
	//    structure.  Within the directory, on a fixed number of
	//    levels may be represented.  Since this is a binary trie,
	//    the maximum depth of the subtree is precalculated as
	//    dirBits, which is in practice log_2(directory.length).
	//    If a new leaf nodes need to be added that would cause
	//    the directory to exceed its implicit maximum depth, the
	//    lowest leaf node on the path to where the new leaf nodes
	//    will be added should become itself a directory node.
	//    The idicies in parent's directory that would have been
	//    used for storing the children are already in use by
	//    nodes at the maximum depth of the directory.
	//
	// 3. The minimum concurrency requires a minimum depth to the
	//    tree.  When the trie is constructed, a leaves will be of
	//    this minimum depth.  When one of these leaves needs to
	//    split, it should not be added to its parent directory.
	//    This is required so that no changes to a node at or
	//    below the minimum depth require write-locking a
	//    directory node above it; avoiding this write-lock
	//    eliminates the chance of write-locking the entire tree.
	//    Therefore, if this node was a leaf node at the minimum
	//    depth, it must become a new directory node.
	//
	if (parentRef == null ||
	    depth % dirBits == 0 ||
	    depth == minDepth) {

	    // this leaf node will become a directory node
	    ManagedReference thisRef = dataManager.createReference(this);
	    rightChild.parentRef = thisRef;			  
	    leftChild.parentRef = thisRef;
	    
	    table = null;
	    nodeDirectory = new ManagedReference[1 << getNodeDirBits()];

	    int right = nodeDirectory.length / 2;

	    // update the new directory with redundant references to
	    // each of the new leaf nodes.
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
     * Uses the provided prefix to look up the appropriate node in the
     * directory.
     *
     * @param prefix the current prefix of the lookup at this
     *        directory node
     *
     * @return the leaf node that is associated with the prefix
     */
    @SuppressWarnings("unchecked")
    private DistributedHashMap<K,V> directoryLookup(int prefix) {
	
	// first, identify the number of bits in the prefix that will
	// be valid for a directory at this depth, then shift only the
	// significant bits down from the prefix and use those as an
	// index into the directory.
	int index = (prefix >>> (32 - getNodeDirBits()));
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
    private void addLeavesToDirectory(int prefix, 
				      ManagedReference rightChildRef,
				      ManagedReference leftChildRef) {		
	prefix <<= this.depth;
	
	// calculate the maximum number of bits available for the
	// directory.  Because the hash code is only 32 bits, it may
	// not be equal to the maximum tree depth, which is determined
	// by the directory size.  The split() method checks that this
	// directory has room; however, we still need to know how many
	// bits are being used to calculate the index.
	int maxBits = getNodeDirBits();
	int index = prefix >>> (32 - maxBits);

	// the leaf is under this node, so just look it up using the
	// directory
	@SuppressWarnings("unchecked") DistributedHashMap<K,V> leaf = 
	    nodeDirectory[index].get(DistributedHashMap.class);

	DataManager dm = AppContext.getDataManager();

	// remove the old leaf node
	dm.removeObject(leaf);
	// mark this leaf for update since it will be changing its
	// directory
	dm.markForUpdate(this);

	// update the leaf node to point to this directory node as
	// their parent
	ManagedReference thisRef = dm.createReference(this);
	rightChildRef.get(DistributedHashMap.class).parentRef = thisRef;
	leftChildRef.get(DistributedHashMap.class).parentRef = thisRef;
	
	// how many bits in the prefix are significant for looking up
	// the child.  
	int sigBits = (leaf.depth - depth);

	// create a bit mask for the parent's significant bits
	int mask = ((1 << sigBits) - 1) << (maxBits - sigBits);

	// this section calculates the starting and end points for the
	// left and right leaf nodes in the directory.  It then adds
	// references to the directory, which may include adding
	// redundant references.  

	// directory index where the left child starts
	int left = index & mask;

	// bit offset between the left and right children in the
	// directory array.  When we logical-or this offset with the
	// left index, we get the index where the right child starts
	// in the directory
	int off = 1 << ((maxBits - sigBits) - 1);

	// exclusive upper bound index in the directory for where the
	// left child ends.  This is also where the right child starts
	// in the directory.
	int right = left | off;
	
	// update all the directory entres for the prefix
	for (int i = left; i < right; ++i) {
	    nodeDirectory[i] = leftChildRef;
	    nodeDirectory[i | off] = rightChildRef;
	}
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
	PrefixEntry<K,V> entry = getEntry(key);
	return (entry == null) ? null : entry.getValue();
    }

    /**
     * A secondary hash function for better distributing the keys.
     *
     * @param h the initial hash value
     * @return a re-hashed version of the provided hash value
     */
    static int hash(int h) {
	/*
	 * Bad hashes tend to have only low bits set, and we choose nodes and
	 * buckets starting with the higher bits, so XOR some lower bits into
	 * the higher bits.
	 */
	h ^= (h << 20);
	h ^= (h << 12);
	return h ^ (h << 7) ^ (h << 4);
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
	leaf.modifications++;

	int i = leaf.indexFor(hash);
 	PrefixEntry<K,V> newEntry = null;
	BigInteger keyId = null;
	PrefixEntry<K,V> prev = null;
	for (PrefixEntry<K,V> e = leaf.getBucket(i); e != null; e = e.next) {
	    /*
	     * Keep bucket chain sorted by hash code, treating the hash codes
	     * as unsigned integers so that they sort the same way as directory
	     * lookups.
	     */
	    if (unsignedLessThan(e.hash, hash)) {
		prev = e;
		continue;
	    } else if (e.hash != hash) {
		break;
	    }
	    Object k = e.getKey();
	    if (k == key || (k != null && k.equals(key))) {
		/* Remove the unused new entry, if any */
		if (newEntry != null) {
		    newEntry.unmanage();
		}
		// if the keys and hash match, swap the values
		// and return the old value
		return e.setValue(value);
	    }
	    /* Create the new entry to get the ID of its key ref */
	    if (newEntry == null) {
		newEntry = new PrefixEntry<K,V>(hash, key, value);
		keyId = newEntry.keyRef().getId();
	    }
	    /* Keep bucket chain sorted by key reference object ID */
	    int compareKey = e.keyRef().getId().compareTo(keyId);
	    if (compareKey < 0) {
		prev = e;
	    } else {
		assert compareKey != 0 : "Entry is already present";
		break;
	    }
	}

 	// we found no key match, so add an entry
	if (newEntry == null) {
	    newEntry = new PrefixEntry<K,V>(hash, key, value);
	}
	leaf.addEntryMaybeSplit(newEntry, prev);

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
     * @param entry the entry to add
     * @param prev the entry immediately prior to the entry to add, or {@code
     *	      null} if the entry should be the first in the bucket
     */
    private void addEntryMaybeSplit(PrefixEntry<K,V> entry,
				    PrefixEntry<K,V> prev)
    {
	addEntry(entry, prev);
	// ensure that the prefix has enough precision to support
	// another split operation	    
	if (size >= splitThreshold && depth < MAX_DEPTH)
	    split();
    }
    
    /**
     * Adds the provided entry to the the current leaf, chaining as
     * necessary, but does <i>not</i> perform the size check for
     * splitting.  This should only be called from {@link #split()}
     * when adding children entries, or if splitting is already handled.
     *
     * @param entry the entry to add
     * @param prev the entry immediately prior to the entry to add, or {@code
     *	      null} if the entry should be the first
     */
    private void addEntry(PrefixEntry<K,V> entry, PrefixEntry<K,V> prev) {
	size++;
	if (prev == null) {
	    int index = indexFor(entry.hash);
	    entry.next = getBucket(index);
	    table[index] = entry;
	} else {
	    assert indexFor(entry.hash) == indexFor(prev.hash) :
	        "Previous node was in a different bucket";
	    PrefixEntry<K,V> next = prev.next;
	    prev.next = entry;
	    entry.next = next;
	}
    }

    /**
     * Returns whether this map has no mappings.  
     *
     * @return {@code true} if this map contains no mappings
     */
    public boolean isEmpty() {
	if (table != null && size == 0) 
	    return true;
	else {
	    DistributedHashMap cur = leftMost();
	    if (cur.size > 0)
		return false;

	    while(cur.rightLeafRef != null) {
		cur = cur.rightLeafRef.get(DistributedHashMap.class);
		if (cur.size > 0)
		    return false;
	    } 
	    return true;
	}
    }
    
    /**
     * Returns the size of the tree.  Note that this implementation
     * runs in {@code O(n*log(n))} time.  Developers should be
     * cautious of calling this method on large maps, as the execution
     * time grows significantly.
     *
     * @return the size of the tree
     */
    public int size() {
	// root is leaf node, short-circuit case
	if (table != null)
	    return size;
	
	int totalSize = 0;
	DistributedHashMap cur = leftMost();
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
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     */
    public V remove(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	DistributedHashMap<K,V> leaf = lookup(hash);	

	int i = leaf.indexFor(hash);
	PrefixEntry<K,V> e = leaf.getBucket(i);
	PrefixEntry<K,V> prev = e;
	while (e != null) {
	    PrefixEntry<K,V> next = e.next;
	    Object k;
	    if (unsignedLessThan(hash, e.hash)) {
		break;
	    } else if (e.hash == hash &&
		       ((k = e.getKey()) == key ||
			(k != null && k.equals(key))))
	    {
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
		leaf.modifications++;

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
     * Removes the entry with the given hash code and key reference, if
     * present.
     *
     * @param hash the hash code
     * @param keyRef the reference for the entry key
     *
     * @see ConcurrentIterator#remove
     */
    void remove(int hash, ManagedReference keyRef) {
	int index = indexFor(hash);
	PrefixEntry<K,V> prev = null;
	for (PrefixEntry<K,V> e = getBucket(index); e != null; e = e.next) {
	    if (unsignedLessThan(hash, e.hash)) {
		break;
	    } else if (e.hash == hash && keyRef.equals(e.keyRef())) {
		AppContext.getDataManager().markForUpdate(this);
		modifications++;
		size--;
		if (prev == null) {
		    table[index] = e.next;
		} else {
		    prev.next = e.next;
		}
		e.unmanage();
		break;
	    }
	    prev = e;
	}
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
     *
     * @return the index for the given hash 
     */
    int indexFor(int h) {
	/*
	 * Return the bits immediately to the right of those used to choose
	 * this node from the parent, but using the full complement of bits if
	 * this is a very deep node.  Using the bits immediately after the
	 * directory bits insures that the buckets are ordered the same way as
	 * the nodes would be after a split.
	 */
	int leafBits = requiredNumBits(leafCapacity);
	int shiftRight = 32 - Math.min(32, depth + leafBits);
	return (h >>> shiftRight) & (table.length - 1);
    }

    /**
     * An implementation of {@code Map.Entry} that incorporates
     * information about the prefix at which it is stored, as well as
     * whether the {@link DistributedHashMap} is responsible for the
     * persistent lifetime of the value.
     *
     * <p>
     *
     * If a key or value that does not implement {@link ManagedObject}
     * is stored in the map, then it is wrapped using the {@link
     * ManagedSerializable} utility class so that the entry may have a
     * {@code ManagedReference} to the value, rather than a Java
     * reference.
     *
     * This class performs an optimization if both key and value do
     * not implemented {@code ManagedObject}.  In this case, both
     * objects will be stored together in a {@link KeyValuePair},
     * which reduces the number of accesses to the data store.
     *
     * @see ManagedSerializable
     */	
    private static class PrefixEntry<K,V> 
	implements Map.Entry<K,V>, Serializable {

	private static final long serialVersionUID = 1;
	    
	/**
	 * The reference to key for this entry. The class type of
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
	 * KeyValuePair} that is referred to by this {@code
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
	 */
	PrefixEntry(int h, K k, V v) { 

	    DataManager dm = AppContext.getDataManager();
	    
	    // if both the key and value are not ManagedObjects, we
	    // can save a get() and createReference() call each by
	    // merging them in a single KeyValuePair
 	    isKeyValueCombined = (!(k instanceof ManagedObject) &&
				  !(v instanceof ManagedObject));
	    if (isKeyValueCombined) {
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
 	    }

	    this.hash = h;
	}
  
	/**
	 * Returns the key stored by this entry.  If the mapping has
	 * been removed from the backing map before this call is made,
	 * an {@code ObjectNotFoundException} will be thrown.
	 *
	 * @return the key stored in this entry
	 * @throws ObjectNotFoundException if the key in the
	 *         backing map was removed prior to this call
	 */
        @SuppressWarnings("unchecked")
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
	ManagedReference keyRef() {
	    return (isKeyValueCombined) ? keyValuePairRef : keyRef;
	}
	
    
	/**
	 * Returns the value stored by this entry.  If the mapping has
	 * been removed from the backing map before this call is made,
	 * an {@code ObjectNotFoundException} will be thrown.
	 *
	 * @return the value stored in this entry
	 * @throws ObjectNotFoundException if the value in the
	 *         backing map was removed prior to this call
	 */
        @SuppressWarnings("unchecked")
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
	 * ManagedSerializable} and stored in the data manager.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
        @SuppressWarnings("unchecked")
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

	    // if v is already a ManagedObject, then do not wrap it
	    // with a ManagedSerializable, and instead acquire a
	    // ManagedReference to it like we would for the
	    // ManagedSerializable.
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
	    return ((keyRef==null   ? 0 : getKey().hashCode())) ^
		    (valueRef==null ? 0 : getValue().hashCode());
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
	 * DistributedHashMap#clear()}, {@link
	 * DistributedHashMap#remove(Object)}, or {@link #remove()}
	 * under the condition that this entry's map-managed object
	 * will never be referenced again by the map.
	 */
	final void unmanage() {
	    DataManager dm = AppContext.getDataManager();

	    if (isKeyValueCombined) {
		try {
		    dm.removeObject(keyValuePairRef.
				    get(KeyValuePair.class));
		} catch (ObjectNotFoundException onfe) {
		    // silent
		}
	    }
	    else {
		if (isKeyWrapped) {
		    try {
			dm.removeObject(keyRef.
					get(ManagedSerializable.class));
		    } catch (ObjectNotFoundException onfe) {
			// silent
		    }
		}
		if (isValueWrapped) {
		    try {
			dm.removeObject(valueRef.
					get(ManagedSerializable.class));
		    } catch (ObjectNotFoundException onfe) {
			// silent
		    }
		}
	    }
 	}
    }
    
    /**
     * A utlity class for PrefixEntry for storing a {@code
     * Serializable} key and value together in a single {@code
     * ManagedObject}.  By combining both together, this saves a
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
     * A concurrent, persistable {@code Iterator} implementation for
     * the {@code DistributedHashMap}.  This implementation provides
     * the following guarantees: <ul><li>if no modifications occur,
     * all elements will eventually be returned by {@link
     * ConcurrentIterator#next()}. <li>if any modifications occur, an
     * element will be returned at most once from {@code next()}, with
     * no guarantee to the number of elements returned.
     */
    abstract static class ConcurrentIterator<E>
	implements Iterator<E>, Serializable {	

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the table, used to look up the
	 * current leaf.
	 */
	private final ManagedReference rootRef;
	
	/**
	 * A reference to the leaf containing the current position of the
	 * iterator, or null if the iteration has not started or has been
	 * completed.
	 */
	private ManagedReference currentLeafRef = null;

	/**
	 * The hash code of the current entry.
	 */
	private int currentHash = 0;

	/**
	 * A reference to the managed object for the key of the current entry,
	 * null if the iteration has not started, or the same value as rootRef
	 * if the iteration has been completed.
	 */
	private ManagedReference currentKeyRef = null;

	/** Set to true when the current entry is removed. */
	private boolean currentRemoved = false;

	/** The leaf containing the next entry, or null if not computed. */
	private transient DistributedHashMap nextLeaf = null;

	/**
	 * The next entry, or null if there is no next entry or if not
	 * computed.
	 */
	private transient PrefixEntry nextEntry = null;

	/**
	 * The value of the modification count when the nextLeaf and
	 * nextEntry fields were computed.
	 */
	private transient int nextLeafModifications = 0;

	/**
	 * Constructs a new {@code ConcurrentIterator}.
	 *
	 * @param root the root node of the {@code DistributedHashMap}
	 */
	ConcurrentIterator(DistributedHashMap root) {
	    rootRef = AppContext.getDataManager().createReference(root);
	    getNext();
	}

	/** Makes sure that the next entry is up to date. */
	private void checkNext() {
	    if (nextLeaf == null ||
		nextLeafModifications != nextLeaf.modifications)
	    {
		getNext();
	    }
	}

	/**
	 * Computes the next entry.
	 */
	private void getNext() {
	    if (currentKeyRef == rootRef) {
		/* No more entries */
		nextLeaf = rootRef.get(DistributedHashMap.class);
		nextEntry = null;
	    } else {
		if (currentLeafRef == null) {
		    /* Find first entry */
		    nextLeaf =
			rootRef.get(DistributedHashMap.class).leftMost();
		    nextEntry = nextLeaf.firstEntry();
		} else {
		    /* Find next entry */
		    nextLeaf = getCurrentLeaf();
		    nextEntry = nextLeaf.nextEntry(currentHash, currentKeyRef);
		}
		/* Find an entry in later leaves, if needed */
		while (nextEntry == null && nextLeaf.rightLeafRef != null) {
		    nextLeaf = nextLeaf.rightLeafRef.get(
			DistributedHashMap.class);
		    nextEntry = nextLeaf.firstEntry();
		}
	    }
	    nextLeafModifications = nextLeaf.modifications;
	}

	/** Returns the current leaf. */
	private DistributedHashMap getCurrentLeaf() {
	    try {
		DistributedHashMap leaf =
		    currentLeafRef.get(DistributedHashMap.class);
		/* Make sure the leaf was not converted to a directory node */
		if (leaf.nodeDirectory == null) {
		    return leaf;
		}
	    } catch (ObjectNotFoundException e) {
		/* The leaf was removed */
	    }
	    return rootRef.get(DistributedHashMap.class).lookup(currentHash);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean hasNext() {
	    checkNext();
	    return nextEntry != null;
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
	Entry nextEntry() {
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }
	    currentLeafRef =
		AppContext.getDataManager().createReference(nextLeaf);
	    currentHash = nextEntry.hash;
	    currentKeyRef = nextEntry.keyRef();
	    currentRemoved = false;
	    Entry result = nextEntry;
	    getNext();
	    if (nextEntry == null) {
		currentKeyRef = rootRef;
	    }
	    return result;
	}

	/**
	 * {@inheritDoc}
	 */
	public void remove() {
	    if (currentRemoved) {
		throw new IllegalStateException(
		    "The current element has already been removed");
	    } else if (currentLeafRef == null) {
		throw new IllegalStateException("No current element");
	    }
	    getCurrentLeaf().remove(currentHash, currentKeyRef);
	}
    }

    /**
     * An iterator over the entry set
     */
    private static final class EntryIterator<K,V> 
	extends ConcurrentIterator<Entry<K,V>> {
	
	private static final long serialVersionUID = 0x1L;

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
        @SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
	public K next() {
	    return ((Entry<K,V>)nextEntry()).getKey();
	}
    }


    /**
     * An iterator over the values in the tree
     */
    private static final class ValueIterator<K,V> 
	extends ConcurrentIterator<V> {

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
	@SuppressWarnings("unchecked")
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
	
	@SuppressWarnings("unchecked")
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

	public boolean contains(Object o) {
	    checkCache();
	    if (!(o instanceof Map.Entry)) 
		return false;
	    @SuppressWarnings("unchecked")
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
	    
        @SuppressWarnings("unchecked")
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
     * of all the values contained in this map.  The returned {@code
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

	Values(DistributedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}

        @SuppressWarnings("unchecked")
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
	
	// if this was a leaf node, write out all the elements in it
	if (table != null) {
	    // iterate over all the table, stopping when all the
	    // entries have been seen
	    PrefixEntry e;
	    int elements = 0;
	    for (int i = 0; elements < size; i++) {
		if ((e = table[i]) != null) {
		    s.writeObject(e);
		    do { // count any chained entries
			elements++;
		    } while ((e = e.next) != null);
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
	    @SuppressWarnings("unchecked")
	    PrefixEntry<K,V> e = (PrefixEntry<K,V>) s.readObject();
	    table[indexFor(e.hash)] = e;
	    for (; (e = e.next) != null; i++)
		; // count chained entries
	}
    }
}
