/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.impl.service.data.store.db.DataEncoding;

import com.sun.sgs.impl.util.ManagedSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.math.BigInteger;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;

/*
 * Comments copied from ScalableHashMap - dj202934 (7/28/08)
 *
 * TBD: Add an asynchronous version of size to avoid scaling problems.
 * -tjb@sun.com (09/26/2007)
 *
 * TBD: Maybe use a separate top level object, to avoid repeating fields that
 * have the same value in every node?  -tjb@sun.com (10/09/2007)
 */

/**
 * A scalable implementation of {@link Map}, which provides a
 * predictable iteration ordering like {@link LinkedHashMap}.  This
 * implementation differs from {@link ScalabeLinkedHashmap} in that it
 * maintains a doubly-linked list of all the entries according to
 * their insertion order.  Therefore, the iteration ordering is
 * equivalent to the insertion ordering.  As with {@code
 * LinkedHashMap} if a key is re-inserted into a map, it will not
 * change the order iteration.  Unlike the {@code LinkedHashMap} class
 * this implementation does not support iterating by access order.
 *
 * <p>
 *
 * The internal structure of the map is separated into distributed
 * pieces, which reduces the amount of data any one operation needs to
 * access.  Due to the nature of maintaining the doubly-linked list
 * of entries, this implementation does not support concurrent
 * operation that change the insertion order: put, when the key is not
 * already present, and remove.  However, this implementation does
 * support concurrent re-insertion operations where the key is already
 * present, as these do not change the insertion order.
 *
 * <p>
 *
 * Peformance is likely to be just below {@code ScalableHashMap} due
 * to added expense of maintaining the doubly-linked list, with one
 * exception.  Iteration over the views of a {@code
 * ScalableLinkedHashMap} will be faster due to fewer object accesses
 * from the data store, and will cause less contention.
 *
 * <p>
 *
 * Developers may use this class as a drop-in replacement for the
 * {@link java.util.LinkedHashMap} class.  A {@code LinkedHashMap}
 * will typically perform better than this class when the number of
 * mappings is small, the objects being stored are small, and minimal
 * concurrency is required.  As the size of the serialized {@code
 * LinkedHashMap} increases, the cost of accessing it significantly
 * increases.  Since the cost of accessing instance of this class this
 * class is <i>independent</i> of the number of elements, this class
 * will perform significantly better than a {@code LinkedHashMap} as
 * the number of mappings increases.  Developers are encouraged to
 * profile the serialized size of their map to determine which
 * implementation will perform better.  Note that {@code
 * LinkedHashMap} does not provide any concurrency for {@code Task}s
 * running in parallel that attempt to modify the map at the same
 * time, so this class may perform better in situations where multiple
 * tasks need to modify the map concurrently, even if the total number
 * of mappings is small.  Also note that, unlike {@code
 * LinkedHashMap}, this class can be used to store {@code
 * ManagedObject} instances directly.
 *
 * <p>
 *
 * This implementation requires that all non-{@code null} keys and values
 * implement {@link Serializable}.  Attempting to add keys or values to the map
 * that do not implement {@code Serializable} will result in an {@link
 * IllegalArgumentException} being thrown.  If a key or value is an instance of
 * {@code Serializable} but does not implement {@code ManagedObject}, this
 * class will persist the object as necessary; when such an object is removed
 * from the map, it is also removed from the {@code DataManager}.  If a key or
 * value is an instance of {@code ManagedObject}, the developer will be
 * responsible for removing these objects from the {@code DataManager} when
 * done with them.  Developers should not remove these object from the {@code
 * DataManager} prior to removing them from the map.
 *
 * <p>
 *
 * Applications must make sure that objects used as keys in maps of this class
 * have {@code equals} and {@code hashCode} methods that return the same values
 * after the keys have been serialized and deserialized.  In particular, keys
 * that use {@link Object#equals Object.equals} and {@link Object#hashCode
 * Object.hashCode} will typically not be equal, and will have different hash
 * codes, each time they are deserialized, and so are probably not suitable for
 * use with this class.  The same requirements apply to objects used as values
 * if the application intends to use {@link #containsValue containsValue} or to
 * compare map entries.
 *
 * <p>
 *
 * This class marks itself for update as necessary; no additional calls to the
 * {@link DataManager} are necessary when modifying the map.  Developers do not
 * need to call {@code markForUpdate} or {@code getForUpdate} on this map, as
 * this will eliminate all the concurrency benefits of this class.  However,
 * calling {@code getForUpdate} or {@code markForUpdate} can be used if a
 * operation needs to prevent all access to the map.
 *
 * <p>
 *
 * Note that, unlike most collections, the {@code size} and {@code isEmpty}
 * methods for this class are <u>not</u> constant-time operations.  Because of
 * the asynchronous nature of the map, these operations may require accessing
 * all of the entries in the map.
 *
 * <p>
 *
 * An instance of {@code ScalableLinkedHashMap} offers one parameter
 * for performance tuning: {@code minConcurrency}, which specifies the
 * minimum number of re-insertion operations to support in parallel.
 * This paramenter will not improve the performance of operations that
 * modify the doubly-linked list of entries.  The {@code
 * minConcurrency} parameter acts as a hint to the map on how to
 * perform internal resizing.  As the map grows, the number of
 * supported parallel operations will also grow beyond the specified
 * minimum.  Setting the minimum concurrency too high will waste space
 * and time, while setting it too low will cause conflicts until the
 * map grows sufficiently to support more concurrent operations.
 *
 * <p>
 *
 * Since the expected distribution of objects in the map is essentially random,
 * the actual concurrency will vary.  Developers are strongly encouraged to use
 * hash codes that provide a normal distribution; a large number of collisions
 * will likely reduce the performance.
 *
 * <p>
 *
 * This class provides {@code Serializable} views from the {@link #entrySet
 * entrySet}, {@link #keySet keySet} and {@link #values values} methods.  These
 * views may be shared and persisted by multiple {@code ManagedObject}
 * instances.
 *
 * <p>
 *
 * <a name="iterator"></a> The {@code Iterator} for each view also
 * implements {@code Serializable}.  A single iterator should only be
 * used by a single {@code ManagedObject} instance at a time.
 * Multiple {@code ManagedObject} instances may have the same iterator
 * as a part of their state, but concurrent traversal of the elements
 * will result in a corrupted ordering.
 *
 * <p>
 *
 * The iterators do not throw {@link
 * java.util.ConcurrentModificationException}.  The iterators are
 * stable with respect to concurrent changes to the associated
 * collection.  Attempting to use an iterator when the associated map
 * has been removed from the {@code DataManager} will result in an
 * {@code ObjectNotFoundException} being thrown, although the {@link
 * Iterator#remove remove} method may throw {@code
 * IllegalStateException} instead if that is appropriate.
 *
 * <p>
 *
 * If a call to the {@link Iterator#next next} method on the iterators causes
 * an {@code ObjectNotFoundException} to be thrown because the return value has
 * been removed from the {@code DataManager}, the iterator will still have
 * successfully moved to the next entry in its iteration.  In this case, the
 * {@link Iterator#remove remove} method may be called on the iterator to
 * remove the current object even though that object could not be returned.
 *
 * <p>
 *
 * This class and its iterator implement all optional operations and support
 * both {@code null} keys and values.  
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @see Object#hashCode Object.hashCode
 * @see java.util.Map
 * @see java.util.LinkedHashMap
 * @see ScalableHashMap
 * @see Serializable
 * @see ManagedObject
 */
public class ScalableLinkedHashMap<K,V>
    extends AbstractMap<K,V>
    implements Serializable, ManagedObjectRemoval {

    /*
     * This class's implementation is based on the paper "Extendible hashing -
     * a fast access method for dynamic files" by Fagin, Nievergelt, Pippenger,
     * and Strong, ACM Transactions on Database Systems, Volume 4, Issue 3
     * (September 1979), pp 315 - 344.
     *
     * The basic structure is a tree whose leaves are standard hash tables.
     * Each non-leaf, or directory, node contains a fixed-size array, the node
     * directory, of references to its children.  Keys are looked up in the
     * tree based on their hash code.  Each directory node records its depth in
     * the tree, which represents the number of the high order bits of the hash
     * code to ignore when selecting the child node.  The necessary number of
     * bits following that position are used as an index into the node
     * directory to select the child node.  Although the full node directory
     * array is allocated at the start, the children nodes are allocated as
     * needed, and appear multiple times in the directory in order to select
     * the proper node for the multiple indices that are associated with it.
     * For example, if a node's directory can support 32 children but has only
     * 2, then the first 16 entries in the node directory will point to the
     * first child, and the remainder to the second child.
     *
     * Leaf nodes contain an array of hash buckets, each of which stores a
     * linked list of the entries that hash to the that bucket.  The hash
     * bucket chosen is based on the bits immediately below those used to
     * choose the node in the parent's node directory.
     *
     * When a leaf node becomes too full, it is split into two nodes, which
     * either replace the original node as children of that node's parent, or
     * become children of the original node, which becomes a directory node.
     * Once split, nodes are never merged, to avoid the concurrency conflicts
     * that such a merge might produce.
     *
     * To support iteration, the entries in the bucket chains are ordered by
     * hash code, treating the hash code as an unsigned integer so that buckets
     * are ordered the same way as children nodes are.  Using the same order
     * for nodes and buckets insures that the order of entries will be
     * maintained when a node is split.  Keys with the same hash code are
     * further ordered by the object ID of managed object that stores the key.
     * Storing the hash code and object ID in the iterator provides a way to
     * uniquely identify the location of the iteration within the table.
     */

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The split threshold used when none is specified in the constructor.
     */
    // NOTE: through empirical testing, it has been shown that 98 elements is
    // the maximum number of PrefixEntries that will fit onto a 4K page.  As
    // the data store page size changes (or if object level locking becomes
    // used), this should be adjusted to minimize page-level contention from
    // writes to the leaf nodes.
    private static final int DEFAULT_SPLIT_THRESHOLD = 98;

    /**
     * The default size of the node directory when none is specified in the
     * constructor.
     */
    private static final int DEFAULT_DIRECTORY_SIZE = 32;

    /**
     * The default number of parallel write operations used when none is
     * specified in the constructor.
     */
    private static final int DEFAULT_MINIMUM_CONCURRENCY =
	DEFAULT_DIRECTORY_SIZE;

    /**
     * The default number of {@code PrefixEntry} slots allocated in the leaf
     * table.
     */
    // NOTE: *must* be a power of 2.
    private static final int DEFAULT_LEAF_CAPACITY = 1 << 8; // 256

    /**
     * The number of bits in an int, which is also the number of bits available
     * for performing lookup ups in the node directory.
     */
    private static final int INT_SIZE = 32;

    /**
     * The maximum depth of this tree.  The value is limited by the number of
     * bits in the hash code and by the need to have at least one bit available
     * for choosing the child node.
     */
    private static final int MAX_DEPTH = INT_SIZE - 1;

    /**
     * The maximum number of entries to remove in a single run of tasks that
     * asynchronously remove nodes and entries.
     */
    private static final int MAX_REMOVE_ENTRIES = 100;

    /**
     * The name used to load a {@code ManagedSerializable<Integer>}
     * that contains the most instance number of this map.  This
     * instance value is used to create a unique map prefix for each
     * instance.
     *
     * @see ScalableLinkedHashMap#findMapPrefix()
     */
    private static final String CLASS_INSTANCE_COUNT = 
	ScalableLinkedHashMap.class.getName() + "-map-num";    

    /**
     * If non-null, a runnable to call when a task that asynchronously removes
     * nodes is done -- used for testing.  Note that this method is called
     * during the transaction that completes the removal.
     */
    private static volatile Runnable noteDoneRemoving = null;

    /**
     * The minor version number, which can be modified to note a compatible
     * change to the data structure.  Incompatible changes should be marked by
     * a change to the serialVersionUID.
     *
     * @serial
     */
    private final short minorVersion = 1;

    /**
     * The parent node directly above this.  For the root node, this
     * should always be null.
     *
     * @serial
     */
    private ManagedReference<ScalableLinkedHashMap<K,V>> parentRef;

    // NOTE: the leftLeafRef and rightLeafRef references form a doubly-linked
    //       list for the leaf nodes of the tree, which allows us to quickly
    //       iterate over them without needing to access all of the
    //       intermediate nodes.

    /**
     * The leaf node immediately to the left of this node if this is a leaf
     * node, else {@code null}.
     *
     * @serial
     */
    ManagedReference<ScalableLinkedHashMap<K,V>> leftLeafRef;

    /**
     * The leaf node immediately to the right of this node if this is a leaf
     * node, else {@code null}.
     *
     * @serial
     */
    ManagedReference<ScalableLinkedHashMap<K,V>> rightLeafRef;

    /**
     * The lookup directory for deciding which node to access based on a
     * provided prefix.  If this instance is a leaf node, the {@code
     * nodeDirectory} for that instance will be {@code null}.  Note that this
     * directory contains both leaf nodes as well as other directory nodes.
     *
     * @serial
     */
    // NOTE: for a diagram of how this directory is arranged, see Fagin et
    //	     al. "Extendible Hashing" (1979) p. 321, fig 3
    ManagedReference[] nodeDirectory;

    /**
     * The fixed-size table for storing all Map entries.  This table will be
     * {@code null} if this instance is a directory node.
     */
    // NOTE: this is actually an array of type PrefixEntry<K,V> but generic
    //       arrays are not allowed, so we cast the elements as necessary
    //private transient PrefixEntry[] table;

    private ManagedReference[] table;

    /**
     * The number of entries in this node's table.  Note that this is
     * <i>not</i> the total number of entries in the entire tree.  For a
     * directory node, this should be set to 0.
     *
     * @serial
     */
    private int size;

    /**
     * FIX ME
     */
    private String nameForFirstEntry;

    /**
     * FIX ME
     */
    private String nameForLastEntry;


    private ManagedReference<ManagedSerializable<Map<
        String,ManagedReference<PrefixEntry<K,V>>>>>
	serializedIteratorsNextElementsRef;

    /**
     * The maximum number of {@code PrefixEntry} entries in a leaf node before
     * it will split into two leaf nodes.
     *
     * @see #split split
     * @serial
     */
    private final int splitThreshold;

    /**
     * The capacity of the {@code PrefixEntry} table.
     *
     * @serial
     */
    private final int leafCapacity;

    /**
     * The minimum depth of the tree, which is controlled by the minimum
     * concurrency factor
     *
     * @see #initDepth initDepth
     * @serial
     */
    private final int minDepth;

    /**
     * The depth of this node in the tree.
     *
     * @serial
     */
    private final int depth;

    /**
     * The maximum number of bits used in the node directory.  This is
     * calculated based on the {@code directorySize} provided in the
     * constructor.
     *
     * @see #addLeavesToDirectory addLeavesToDirectory
     * @serial
     */
    private final int maxDirBits;

    /**
     * Creates an empty map.
     *
     * @param depth the depth of this node in the tree
     * @param minDepth the minimum depth of leaf nodes requested to support a
     *        minimum number of concurrent write operations
     * @param splitThreshold the number of entries in a leaf node that will
     *        cause the leaf to split
     * @param directorySize the maximum number of children nodes of a directory
     *        node
     *
     * @throws IllegalArgumentException if:
     *	       <ul>
     *         <li> {@code depth} is negative or greater than {@link #MAX_DEPTH}
     *	       <li> {@code minDepth} is negative or greater than {@code
     *		    MAX_DEPTH}
     *	       <li> {@code splitThreshold} is not greater than zero
     *         <li> {@code directorySize} is less than two
     *	       </ul>
     */
    // NOTE: this constructor is currently left package private but future
    // implementations could expose some of these parameters for performance
    // optimization.  At no point should depth be exposed for public
    // modification.  directorySize should also not be directly exposed.
    private ScalableLinkedHashMap(int depth, int minDepth, int splitThreshold,
				  int directorySize, String nameForFirstEntry, 
				  String nameForLastEntry, 
				  ManagedReference<ManagedSerializable<Map<
				  String,ManagedReference<PrefixEntry<K,V>>>>>
				  serializedIteratorsNextElementsRef)
	{
	this(depth, minDepth, splitThreshold, directorySize);
	
	this.nameForLastEntry = nameForLastEntry;
	this.serializedIteratorsNextElementsRef = serializedIteratorsNextElementsRef;
    }


    /**
     * Creates an empty map.
     *
     * @param depth the depth of this node in the tree
     * @param minDepth the minimum depth of leaf nodes requested to support a
     *        minimum number of concurrent write operations
     * @param splitThreshold the number of entries in a leaf node that will
     *        cause the leaf to split
     * @param directorySize the maximum number of children nodes of a directory
     *        node
     *
     * @throws IllegalArgumentException if:
     *	       <ul>
     *         <li> {@code depth} is negative or greater than {@link #MAX_DEPTH}
     *	       <li> {@code minDepth} is negative or greater than {@code
     *		    MAX_DEPTH}
     *	       <li> {@code splitThreshold} is not greater than zero
     *         <li> {@code directorySize} is less than two
     *	       </ul>
     */
    // NOTE: this constructor is currently left package private but future
    // implementations could expose some of these parameters for performance
    // optimization.  At no point should depth be exposed for public
    // modification.  directorySize should also not be directly exposed.
    ScalableLinkedHashMap(int depth, int minDepth, int splitThreshold,
			  int directorySize) {
	if (depth < 0 || depth > MAX_DEPTH) {
	    throw new IllegalArgumentException(
		"Illegal tree depth: " + depth);
	}
	if (minDepth < 0 || minDepth > MAX_DEPTH) {
	    throw new IllegalArgumentException(
		"Illegal minimum depth: " + minDepth);
	}
	if (splitThreshold <= 0) {
	    throw new IllegalArgumentException(
		"Illegal split threshold: " + splitThreshold);
	}
	if (directorySize < 2) {
	    throw new IllegalArgumentException(
		"Illegal directory size: " + directorySize);
	}

	this.depth = depth;
	this.minDepth = minDepth;

	size = 0;
	parentRef = null;
	leftLeafRef = null;
	rightLeafRef = null;

	leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new ManagedReference[leafCapacity];
	nodeDirectory = null;

	maxDirBits = requiredNumBits(directorySize);

	this.splitThreshold = splitThreshold;

	// Only the root node should ensure depth, otherwise this call causes
	// the children to be created in depth-first fashion, which prevents
	// the leaf references from being correctly established
	if (depth == 0) {

	    String mapPrefix = findMapPrefix();
	    nameForFirstEntry = mapPrefix + "first-entry";
	    nameForLastEntry = mapPrefix + "last-entry";
	    
	    ManagedSerializable<Map<String,ManagedReference<PrefixEntry<K,V>>>>
		serializedIteratorsNextElements = new ManagedSerializable<
		Map<String,ManagedReference<PrefixEntry<K,V>>>>(
		    new HashMap<String,ManagedReference<PrefixEntry<K,V>>>());
	    
	    serializedIteratorsNextElementsRef = 
		AppContext.getDataManager().
		createReference(serializedIteratorsNextElements);

	    initDepth(minDepth, nameForFirstEntry, nameForLastEntry,
		      serializedIteratorsNextElementsRef);
	}
    }

    private static String findMapPrefix() {
	    
	DataManager dm = AppContext.getDataManager();
	int instanceNum = -1;
	try {
	    ManagedSerializable<Integer> mostRecentMapNum = 
		uncheckedCast(dm.getBinding(CLASS_INSTANCE_COUNT));
	    instanceNum = mostRecentMapNum.get().intValue() + 1;
	} catch (NameNotBoundException nnbe) {
	    // we must be the first instance
	    instanceNum = 0;
	}
	
	// update the most recent instance binding to reflect this
	// map's creation
	dm.setBinding(CLASS_INSTANCE_COUNT, 
		      new ManagedSerializable<Integer>(instanceNum));
	
	// then set up the prefix string
	String mapSuffix = String.format("%014d", instanceNum);

	// combine the common class prefix for entries with this
	// instance's suffix to create a unique namespace for this map
	return ScalableLinkedHashMap.class.getName() + mapSuffix + "-entry-";
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
	return INT_SIZE - Integer.numberOfLeadingZeros(n - 1);
    }

    /**
     * Creates an empty map with the specified minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write operations
     *        to support
     *
     * @throws IllegalArgumentException if {@code minConcurrency} is
     *	       not greater than zero
     */
    public ScalableLinkedHashMap(int minConcurrency) {
	this(0, findMinDepthFor(minConcurrency), DEFAULT_SPLIT_THRESHOLD,
	     DEFAULT_DIRECTORY_SIZE);
    }

    /**
     * Constructs an empty map with the default minimum concurrency ({@code
     * 32}).
     */
    public ScalableLinkedHashMap() {
	this(0, findMinDepthFor(DEFAULT_MINIMUM_CONCURRENCY),
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
    }

    /**
     * Constructs a new map with the same mappings as the specified {@code
     * Map}, and the default minimum concurrency ({@code 32}).
     *
     * @param map the mappings to include
     *
     * @throws IllegalArgumentException if any of the keys or values contained
     *	       in the argument are not {@code null} and do not implement {@code
     *	       Serializable}
     */
    public ScalableLinkedHashMap(Map<? extends K, ? extends V> map) {
	this(0, findMinDepthFor(DEFAULT_MINIMUM_CONCURRENCY),
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_DIRECTORY_SIZE);
	if (map == null) {
	    throw new NullPointerException(
		"The map argument must not be null");
	}
	putAll(map);
    }

    /**
     * Returns the minimum depth of the tree necessary to support the requested
     * minimum number of concurrent write operations.
     *
     * @param minConcurrency the minimum number of concurrent write operations
     *        to perform
     *
     * @return the necessary minimum depth to the tree
     *
     * @throws IllegalArgumentException if minConcurrency is not greater than
     *	       zero
     */
    private static int findMinDepthFor(int minConcurrency) {
	if (minConcurrency <= 0) {
	    throw new IllegalArgumentException(
		"Minimum concurrency must be greater than zero: " +
		minConcurrency);
	}
	return Math.min(MAX_DEPTH, requiredNumBits(minConcurrency));
    }

    /**
     * Ensures that this node has children of at least the provided minimum
     * depth.  Nodes above the minimum depth will be added to the nearest
     * directory node.
     *
     * <p>
     *
     * This method is only safe to be used by the constructor once, or
     * recursively from within this method; this ensures that the leaves of the
     * tree are initialized properly.
     *
     * @param minDepth the minimum depth of the leaf nodes under this node
     *
     * @param mapEntryPrefix
     *
     * @see #split split
     */
    // NOTE: if this were to be called in a depth-first fashion, with split
    // being called repeatedly, the leaves of the tree of the tree would have
    // their left and right reference improperly initialized.
    private void initDepth(int minDepth,  String nameForFirstEntry, 
			   String nameForLastEntry, 
			   ManagedReference<ManagedSerializable<Map<
			   String,ManagedReference<PrefixEntry<K,V>>>>>
			   serializedIteratorsNextElementsRef) {

	if (depth >= minDepth) {
	    return;
	}
	// rather than split repeatedly, this method inlines all splits at once
	// and links the children together.  This is much more efficient.

	setLeafNode(); // this node is no longer a leaf
	nodeDirectory = new ManagedReference[1 << getNodeDirBits()];

	// decide how many leaves to make based on the required depth.  Note
	// that we never create more than the maximum number of leaves here.
	// If we need more depth, we will use a recursive call to the ensure
	// depth on the leaves.
	int leafBits = Math.min(minDepth - depth, maxDirBits);
	int numLeaves = 1 << leafBits;

	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);
	ManagedReference<ScalableLinkedHashMap<K,V>> thisRef =
	    dm.createReference(this);

	ScalableLinkedHashMap[] leaves = new ScalableLinkedHashMap[numLeaves];
	for (int i = 0; i < numLeaves; ++i) {
	    ScalableLinkedHashMap<K,V> leaf = new ScalableLinkedHashMap<K,V>(
		depth + leafBits, minDepth, splitThreshold, 1 << maxDirBits,
		nameForFirstEntry, nameForLastEntry, 
		serializedIteratorsNextElementsRef);
									
	    leaves[i] = leaf;
	    leaf.parentRef = thisRef;
	}

	// for the linked list for the leaves
	for (int i = 1; i < numLeaves-1; ++i) {
	    ScalableLinkedHashMap<K,V> leaf = uncheckedCast(leaves[i]);
	    leaf.leftLeafRef = uncheckedCast(dm.createReference(leaves[i-1]));
	    leaf.rightLeafRef = uncheckedCast(dm.createReference(leaves[i+1]));
	}

	// edge updating - Note that since there are guaranteed to be at least
	// two leaves, these absolute offset calls are safe
	ScalableLinkedHashMap<K,V> firstLeaf = uncheckedCast(leaves[0]);
	firstLeaf.leftLeafRef = leftLeafRef;
	firstLeaf.rightLeafRef = uncheckedCast(dm.createReference(leaves[1]));
	ScalableLinkedHashMap<K,V> lastLeaf = uncheckedCast(leaves[numLeaves-1]);
	lastLeaf.leftLeafRef =
	    uncheckedCast(dm.createReference(leaves[numLeaves-2]));
	lastLeaf.rightLeafRef = rightLeafRef;

	// since this node is now a directory, invalidate its leaf-list
	// references
	leftLeafRef = null;
	rightLeafRef = null;

	int entriesPerLeaf = nodeDirectory.length / numLeaves;

	// lastly, fill the directory with the references
	int pos = 0;
	for (ScalableLinkedHashMap leaf : leaves) {
	    int nextPos = pos + entriesPerLeaf;
	    Arrays.fill(nodeDirectory, pos, nextPos, dm.createReference(leaf));
	    pos = nextPos;
	}

	/* Make sure the leaves have the minimum required depth. */
	for (ScalableLinkedHashMap leaf : leaves) {
	    leaf.initDepth(minDepth, nameForFirstEntry, nameForLastEntry, 
			   serializedIteratorsNextElementsRef);
	}
    }

    /**
     * Mark this node as a leaf node by setting its entry table to {@code
     * null}.
     */
    private void setLeafNode() {
	table = null;
    }

    /**
     * Returns the number of bits of the hash code that are used for looking up
     * children of this node in the node directory.
     *
     * @return the number of directory bits for this node
     */
    private int getNodeDirBits() {
	/*
	 * If the node is very deep, then the number of bits used to do
	 * directory lookups is limited by the total number of bits.
	 */
	return Math.min(INT_SIZE - depth, maxDirBits);
    }

    /**
     * Clears the map of all entries.  When clearing, all non-{@code
     * ManagedObject} keys and values persisted by this map will be removed
     * from the {@code DataManager}.
     */
    public void clear() {
	assert isRootNode()
	    : "The clear method should only be called on the root";
	removeChildrenAndEntries();
	size = 0;
	leftLeafRef = null;
	rightLeafRef = null;
	table = new ManagedReference[leafCapacity];
	DataManager dm = AppContext.getDataManager();
	try {
	    dm.removeBinding(nameForFirstEntry);
	    dm.removeBinding(nameForLastEntry);
	} catch (NameNotBoundException nnbe) {
	    // we might have been called twice
	}
	
	// let all the iterators know that the map has been cleared by
	// setting their next element to null
	Map<String,ManagedReference<PrefixEntry<K,V>>>
	    iteratorToCurrentEntry = 
	    serializedIteratorsNextElementsRef.getForUpdate().get();
	
	// examine each iterator's next entry and set it to null
	for (Map.Entry<String,ManagedReference<PrefixEntry<K,V>>> e :
		 iteratorToCurrentEntry.entrySet()) {
	    e.setValue(null);
	}
	    
	// increment the version number to invalidate all the other
	// names while they are being cleared.  This lets us continue
	// to add name bindings with a unique prefix.  Also, any
	// outstanding iterators will notice the change and no longer
	// iterate over the cleared entries.

	if (depth == 0) {
	    initDepth(minDepth, nameForFirstEntry, nameForLastEntry, 
		      serializedIteratorsNextElementsRef);
	}
    }

    /**
     * Checks if this is a leaf node.
     *
     * @return whether this is a leaf node.
     */
    private boolean isLeafNode() {
	return table != null;
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
     * @return the entry associated with the key or {@code null} if no such
     *         entry exists
     */
    PrefixEntry<K,V> getEntry(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	ScalableLinkedHashMap<K,V> leaf = lookup(hash);
	for (PrefixEntry<K,V> e = leaf.getBucket(leaf.indexFor(hash));
	     e != null; e = e.next())
	{
	    if (e.hash == hash) {
		K k;
		try {
		    k = e.getKey();
		} catch (ObjectNotFoundException onfe) {
		    continue;
		}
		if (safeEquals(k, key)) {
		    return e;
		}
	    } else if (unsignedLessThan(hash, e.hash)) {
		break;
	    }
	}
	return null;
    }

    /**
     * Returns {@code true} if the objects are equal or both {@code null}.
     *
     * @param x first argument
     * @param y second argument
     *
     * @return whether the objects are equal or both {@code null}
     */
    static boolean safeEquals(Object x, Object y) {
	return x == y || (x != null && x.equals(y));
    }

    /**
     * Returns the first entry in this leaf for the specified index, or {@code
     * null} if there is none.
     *
     * @param index the index
     *
     * @return the entry or {@code null}
     */
    private PrefixEntry<K,V> getBucket(int index) {
	if (table[index] == null)
	    return null;	
	ManagedReference<PrefixEntry<K,V>> ref = uncheckedCast(table[index]);
	return ref.get();
    }

//     /**
//      * Returns the first entry in this leaf, or {@code null} if there are no
//      * entries.
//      *
//      * @return the first entry in this leaf or {@code null}
//      */
//     PrefixEntry<K,V> firstEntry() {
// 	for (int i = 0; i < table.length; i++) {
// 	    PrefixEntry<K,V> entry = getBucket(i);
// 	    if (entry != null) {
// 		return entry;
// 	    }
// 	}
// 	return null;
//     }

//     /**
//      * Returns the next entry in this leaf after the entry with the specified
//      * hash and key reference, or {@code null} if there are no entries after
//      * that position.
//      *
//      * @param hash the hash code
//      * @param keyRef the reference for the entry key
//      *
//      * @return the next entry or {@code null}
//      */
//     PrefixEntry<K,V> nextEntry(int hash, ManagedReference<?> keyRef) {
// 	BigInteger keyId = keyRef.getId();
// 	for (int i = indexFor(hash); i < table.length; i++) {
// 	    for (PrefixEntry<K,V> e = getBucket(i); e != null; e = e.next()) {
// 		if (unsignedLessThan(e.hash, hash)) {
// 		    continue;
// 		} else if (e.hash != hash ||
// 			   e.keyRef().getId().compareTo(keyId) > 0)
// 		{
// 		    return e;
// 		}
// 	    }
// 	}
// 	return null;
//     }

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
     * Note that the execution time of this method grows substantially as the
     * map size increases due to the cost of accessing the data manager.
     */
    public boolean containsValue(Object value) {
	for (Iterator<V> i = values().iterator(); i.hasNext(); ) {
	    V v;
	    try {
		v = i.next();
	    } catch (ObjectNotFoundException e) {
		continue;
	    }
	    if (safeEquals(v, value)) {
		return true;
	    }
	}
	return false;
    }

    /**
     * Divides the entries in this leaf node into two leaf nodes on the basis
     * of prefix, and then either adds the new nodes to the parent or converts
     * this node to a directory node.  This method should only be called when
     * the entries contained within this node have valid prefix bits remaining
     * (i.e. they have not already been shifted to the maximum possible
     * precision).
     *
     * @see #addEntry addEntry
     */
    private void split() {
	assert isLeafNode() : "Can't split an directory node";
	assert depth < MAX_DEPTH : "Can't split at maximum depth";

	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	ScalableLinkedHashMap<K,V> leftChild =
	    new ScalableLinkedHashMap<K,V>(
		depth+1, minDepth, splitThreshold, 1 << maxDirBits,
		nameForFirstEntry, nameForLastEntry,
		serializedIteratorsNextElementsRef);
	ScalableLinkedHashMap<K,V> rightChild =
	    new ScalableLinkedHashMap<K,V>(
	        depth+1, minDepth, splitThreshold, 1 << maxDirBits,
		nameForFirstEntry, nameForLastEntry, 
		serializedIteratorsNextElementsRef);

	// to add this node to the parent directory, we need to determine the
	// prefix that will lead to this node.  Grabbing a hash code from one
	// of our entries will suffice.
	int prefix = 0x0; // this should never stay at its initial value

	// iterate over all the entries in this table and assign them to either
	// the right child or left child
	int firstRight = table.length / 2;
	for (int i = 0; i < table.length; i++) {
	    ScalableLinkedHashMap<K,V> child =
		(i < firstRight) ? leftChild : rightChild;
	    PrefixEntry<K,V> prev = null;
	    int prevIndex = 0;
	    PrefixEntry<K,V> e = getBucket(i);
	    while (e != null) {
		prefix = e.hash;
		int index = child.indexFor(e.hash);
		PrefixEntry<K,V> next = e.next();
		/* Chain to the previous node if the index is the same */
		child.addEntry(e, index == prevIndex ? prev : null);
		prev = e;
		prevIndex = index;
		e = next;
	    }
	}

	// null out the intermediate node's table
	setLeafNode();
	size = 0;

	// create the references to the new children
	ManagedReference<ScalableLinkedHashMap<K,V>> leftChildRef =
	    dataManager.createReference(leftChild);
	ManagedReference<ScalableLinkedHashMap<K,V>> rightChildRef =
	    dataManager.createReference(rightChild);

	if (leftLeafRef != null) {
	    ScalableLinkedHashMap<K,V> leftLeaf = leftLeafRef.get();
	    leftLeaf.rightLeafRef = leftChildRef;
	    leftChild.leftLeafRef = leftLeafRef;
	    leftLeafRef = null;
	}

	if (rightLeafRef != null) {
	    ScalableLinkedHashMap<K,V> rightLeaf = rightLeafRef.get();
	    rightLeaf.leftLeafRef = rightChildRef;
	    rightChild.rightLeafRef = rightLeafRef;
	    rightLeafRef = null;
	}

	// update the family links
	leftChild.rightLeafRef = rightChildRef;
	rightChild.leftLeafRef = leftChildRef;

	// Decide what to do with this node:

	// This node should form a new directory node in the following cases:
	//
	// 1. This node is the root node.
	//
	// 2. This node has reached the maximum permitted depth relative to its
	//    parent.  Each directory node uses at most maxDirBits of the hash
	//    code to determine the position of a child node in its directory.
	//    If the depth of this node relative to its parent already uses
	//    that number of bits, then an additional level of directory nodes
	//    is needed to reach the desired depth.  Note that a node will
	//    reach its maximum depth only after all of its parents have
	//    already done so.
	//
	// 3. The minimum concurrency requested requires that the parent node
	//    not be modified.  When the trie is constructed, leaves are
	//    created at a minimum depth.  When one of these leaves needs to
	//    split, it should not be added to its parent directory, in order
	//    to provide the requested concurrency.
	if (isRootNode() ||
	    depth % maxDirBits == 0 ||
	    depth == minDepth) {

	    // this leaf node will become a directory node
	    ManagedReference<ScalableLinkedHashMap<K,V>> thisRef =
		dataManager.createReference(this);
	    rightChild.parentRef = thisRef;
	    leftChild.parentRef = thisRef;

	    setLeafNode();
	    nodeDirectory = new ManagedReference[1 << getNodeDirBits()];

	    int firstRightIndex = nodeDirectory.length / 2;

	    // update the new directory with references to the new leaf nodes
	    Arrays.fill(nodeDirectory, 0, firstRightIndex, leftChildRef);
	    Arrays.fill(nodeDirectory, firstRightIndex, nodeDirectory.length,
			rightChildRef);
	} else {
	    // In all other cases, this node can be added to its parent
	    // directory.

	    // notify the parent to remove this leaf by following the provided
	    // prefix and then replace it with references to the right and left
	    // children
	    parentRef.get().addLeavesToDirectory(
		prefix, leftChildRef, rightChildRef);
	}
    }

    /**
     * Checks if this is the root node.
     *
     * @return whether this is the root node.
     */
    private boolean isRootNode() {
	return parentRef == null;
    }

    /**
     * Locates the leaf node that is associated with the provided prefix.
     *
     * @param prefix the initial prefix for which to search
     *
     * @return the leaf table responsible for storing all entries with the
     *         specified prefix
     */
    ScalableLinkedHashMap<K,V> lookup(int prefix) {
	ScalableLinkedHashMap<K,V> node = this;
	while (!node.isLeafNode()) {
	    int index = highBits(prefix << node.depth, node.getNodeDirBits());
	    node = node.getChildNode(index);
	}
	return node;
    }

    /**
     * Returns the child node with the specified index.
     *
     * @param index the index of the child node
     *
     * @return the child node
     */
    private ScalableLinkedHashMap<K,V> getChildNode(int index) {
	return uncheckedCast(nodeDirectory[index].get());
    }

    /**
     * Shifts the specified number of the highest order bits to the rightmost
     * position in the value, so that they can be used as an integer value.
     *
     * @param n the value
     * @param numBits the number of highest order bits
     *
     * @return the requested highest order bits of the value
     */
    private static int highBits(int n, int numBits) {
	return n >>> (INT_SIZE - numBits);
    }

    /**
     * Replaces the leaf node pointed to by the provided prefix with directory
     * entries for its children {@code rightChildRef} and {@code leftChildRef}.
     * This method should only be called by {@link #split split} under the
     * proper conditions.
     *
     * @param prefix the prefix that leads to the node that will be replaced
     * @param leftChildRef the left child of the node that will be replaced
     * @param rightChildRef the right child of the node that will be replaced
     */
    private void addLeavesToDirectory(
	int prefix,
	ManagedReference<ScalableLinkedHashMap<K,V>> leftChildRef,
	ManagedReference<ScalableLinkedHashMap<K,V>> rightChildRef)
    {
	prefix <<= this.depth;

	int dirBits = getNodeDirBits();
	int index = highBits(prefix, dirBits);

	// the leaf is under this node, so just look it up using the directory
	ScalableLinkedHashMap<K,V> leaf = getChildNode(index);

	DataManager dm = AppContext.getDataManager();

	// remove the old leaf node
	dm.removeObject(leaf);
	// mark this node for update since we will be changing its directory
	dm.markForUpdate(this);

	// update the new children nodes to point to this directory node as
	// their parent
	ManagedReference<ScalableLinkedHashMap<K,V>> thisRef =
	    dm.createReference(this);
	rightChildRef.get().parentRef = thisRef;
	leftChildRef.get().parentRef = thisRef;

	// how many bits in the prefix are significant for looking up the
	// old leaf
	int sigBits = leaf.depth - depth;

	// create a bit mask for the bits in the directory index that selected
	// the old leaf
	int mask = ((1 << sigBits) - 1) << (dirBits - sigBits);

	// this section calculates the starting and end points for the left and
	// right leaf nodes in the directory.  It then adds references to the
	// directory, which may include adding redundant references.

	// directory index where the old leaf started, which is where left
	// child starts
	int left = index & mask;

	// number of entries for each of left and right children -- half the
	// total number of entries for the old leaf
	int numEach = 1 << ((dirBits - sigBits) - 1);

	// directory index where the right child starts
	int right = left + numEach;

	// update the directory entries
	Arrays.fill(nodeDirectory, left, left + numEach, leftChildRef);
	Arrays.fill(nodeDirectory, right, right + numEach, rightChildRef);
    }

    /**
     * Returns the value to which this key is mapped or {@code null} if the map
     * contains no mapping for this key.  Note that the return value of {@code
     * null} does not necessarily imply that no mapping for this key existed
     * since this implementation supports {@code null} values.  The {@link
     * #containsKey containsKey} method can be used to determine whether a
     * mapping exists.
     *
     * @param key the key whose mapped value is to be returned
     *
     * @return the value mapped to the provided key or {@code null} if no such
     *         mapping exists
     *
     * @throws ObjectNotFoundException if the value associated with the key has
     *	       been removed from the {@link DataManager}
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
    private static int hash(int h) {
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
     * Associates the specified key with the provided value and returns the
     * previous value if the key was previous mapped.  This map supports both
     * {@code null} keys and values. <p>
     *
     * If the value currently associated with {@code key} has been removed from
     * the {@link DataManager}, then an {@link ObjectNotFoundException} will be
     * thrown and the mapping will not be changed.
     *
     * @param key the key
     * @param value the value to be mapped to the key
     *
     * @return the previous value mapped to the provided key, if any
     *
     * @throws IllegalArgumentException if either {@code key} or {@code value}
     *	       is not {@code null} and does not implement {@code Serializable}
     * @throws ObjectNotFoundException if the previous value associated with
     *	       the key has been removed from the {@link DataManager}
     */
    public V put(K key, V value) {
	checkSerializable(key, "key");
	checkSerializable(value, "value");
	return putInternal(key, value, true);
    }

    /**
     * Like put, but does not check if arguments are serializable, and only
     * returns the old value if requested, to avoid an exception if the old
     * value is not found.  Note that this method needs to keep entries ordered
     * by object ID if they have the same hash code, to support iteration.
     *     
     * @param key the key
     * @param value the value to be mapped to the key
     * @param returnOldValue whether to return the old value
     *
     * @return the previous value mapped to the provided key, if any.  Always
     *	       returns {@code null} if {@code returnOldValue} is {@code false}
     * @throws ObjectNotFoundException if the previous value associated with
     *	       the key has been removed from the {@link DataManager} and is
     *	       being returned
     */
    private V putInternal(K key, V value, boolean returnOldValue) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	ScalableLinkedHashMap<K,V> leaf = lookup(hash);
	AppContext.getDataManager().markForUpdate(leaf);

	int i = leaf.indexFor(hash);
	PrefixEntry<K,V> newEntry = null;
	BigInteger keyId = null;
	PrefixEntry<K,V> prev = null;
	for (PrefixEntry<K,V> e = leaf.getBucket(i); e != null; e = e.next()) {
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
	    boolean keyMatches;
	    try {
		keyMatches = safeEquals(e.getKey(), key);
	    } catch (ObjectNotFoundException onfe) {
		keyMatches = false;
	    }
	    if (keyMatches) {

		// if the keys and hash match, swap the values and return the
		// old value
		if (returnOldValue) {
		    return e.setValue(value);
		} else {
		    e.setValueInternal(value);
		    return null;
		}
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
	    }
	}

	// we found no key match, so add an entry
	if (newEntry == null) {
	    newEntry = new PrefixEntry<K,V>(hash, key, value);	   
	}
	leaf.addEntryMaybeSplit(newEntry, prev);

	// update the insertion-order list
	PrefixEntry<K,V> lastInsert = getLastEntry();
	DataManager dm = AppContext.getDataManager();

	if (lastInsert != null) {
	    lastInsert.nextInsert = dm.createReference(newEntry);
	    newEntry.prevInsert = dm.createReference(lastInsert);
	}
	else {
	    // if the last entry inserted was null, then it means this
	    // is the first entry, to set the binding accordingly
	    dm.setBinding(nameForFirstEntry, newEntry);
	}
	// bind the new entry as the most recently inserted entry
	dm.setBinding(nameForLastEntry, newEntry);

	return null;
    }
    
    private PrefixEntry<K,V> getLastEntry() {
	DataManager dm = AppContext.getDataManager();
	try {
	    PrefixEntry<K,V> e = 
		uncheckedCast(dm.getBinding(nameForLastEntry));
	    return e;
	}
	catch (NameNotBoundException nnbe) {
	    // this only happens if the map is empty
	    return null;
	}
    }

    /**
     * Checks that an object supplied as an argument is either {@code null} or
     * implements {@code Serializable}.
     *
     * @param object the object
     * @param argName the name of the argument
     *
     * @throws IllegalArgumentException if the object is not {@code null} and
     *	       does not implement {@code Serializable}
     */
    static void checkSerializable(Object object, String argName) {
	if (object != null && !(object instanceof Serializable)) {
	    throw new IllegalArgumentException(
		"The " + argName + " argument must be Serializable");
	}
    }

    /**
     * Copies all of the mappings from the provided map into this map.  This
     * operation will replace any mappings for keys currently in the map if
     * they occur in both this map and the provided map.
     *
     * @param m the map to be copied
     *
     * @throws IllegalArgumentException if any of the keys or values contained
     *	       in the argument are not {@code null} and do not implement {@code
     *	       Serializable}.  If this exception is thrown, some of the entries
     *	       from the argument may have already been added to this map.
     */
    public void putAll(Map<? extends K, ? extends V> m) {
	for (Entry<? extends K,? extends V> e : m.entrySet()) {
	    K key = e.getKey();
	    if (key != null && !(key instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The collection contains a non-serializable key");
	    }
	    V value = e.getValue();
	    if (value != null && !(value instanceof Serializable)) {
		throw new IllegalArgumentException(
		    "The collection contains a non-serializable value");
	    }
	    putInternal(key, value, false);
	}
    }

    /**
     * Adds a new entry at the specified index and determines if a {@link
     * #split split} operation is necessary.
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
	if (size >= splitThreshold && depth < MAX_DEPTH) {
	    split();
	}
    }

    /**
     * Adds the provided entry to the the current leaf, but does <i>not</i>
     * perform the size check for splitting.  This should only be called from
     * {@link #split split} when adding children entries, or if splitting is
     * already handled.
     *
     * @param entry the entry to add
     * @param prev the entry immediately prior to the entry to add, or {@code
     *	      null} if the entry should be the first in the list
     */
    private void addEntry(PrefixEntry<K,V> entry, PrefixEntry<K,V> prev) {
	size++;

	if (prev == null) {
	    int index = indexFor(entry.hash);
	    entry.setNext(getBucket(index));	    
	    DataManager dm = AppContext.getDataManager();
	    table[index] = dm.createReference(entry);
	} else {
	    assert indexFor(entry.hash) == indexFor(prev.hash) :
		"Previous node was in a different bucket";
	    PrefixEntry<K,V> next = prev.next();
	    prev.setNext(entry);
	    entry.setNext(next);
	}
    }

    /**
     * Returns whether this map has no mappings.
     *
     * @return {@code true} if this map contains no mappings
     */
    public boolean isEmpty() {
	if (isLeafNode() && size == 0) {
	    return true;
	} else {
	    ScalableLinkedHashMap<K,V> cur = leftMost();
	    if (cur.size > 0) {
		return false;
	    }
	    while (cur.rightLeafRef != null) {
		cur = cur.rightLeafRef.get();
		if (cur.size > 0) {
		    return false;
		}
	    }
	    return true;
	}
    }

    /**
     * Returns the size of the tree.  Note that this implementation runs in
     * {@code O(n*log(n))} time.  Developers should be cautious of calling this
     * method on large maps, as the execution time grows significantly. <p>
     *
     * Developers can avoid possible scaling problems by using an iterator to
     * count the number of elements in the tree, but counting only a few
     * elements before scheduling the rest of the job as a task to be performed
     * by the task scheduler.
     *
     * @return the size of the tree
     */
    public int size() {
	// root is leaf node, short-circuit case
	if (isLeafNode()) {
	    return size;
	}
	int totalSize = 0;
	ScalableLinkedHashMap<K,V> cur = leftMost();
	totalSize += cur.size;
	while (cur.rightLeafRef != null) {
	    cur = cur.rightLeafRef.get();
	    totalSize += cur.size;
	}

	return totalSize;
    }

    /**
     * Removes the mapping for the specified key from this map if present. <p>
     *
     * If the value currently associated with {@code key} has been removed from
     * the {@link DataManager}, then an {@link ObjectNotFoundException} will be
     * thrown and the mapping will not be removed.
     *
     * @param  key key whose mapping is to be removed from the map
     *
     * @return the previous value associated with {@code key}, or {@code null}
     *         if there was no mapping for {@code key}.  (A {@code null} return
     *         can also indicate that the map previously associated {@code
     *         null} with {@code key}.)
     *
     * @throws ObjectNotFoundException if the value associated with the key has
     *	       been removed from the {@link DataManager}
     */
    public V remove(Object key) {
	int hash = (key == null) ? 0x0 : hash(key.hashCode());
	ScalableLinkedHashMap<K,V> leaf = lookup(hash);

	int i = leaf.indexFor(hash);
	PrefixEntry<K,V> e = leaf.getBucket(i);
	PrefixEntry<K,V> prev = e;
	while (e != null) {
	    PrefixEntry<K,V> next = e.next();
	    if (unsignedLessThan(hash, e.hash)) {
		break;
	    } else if (e.hash == hash) {
		boolean keyMatches;
		try {
		    keyMatches = safeEquals(e.getKey(), key);
		} catch (ObjectNotFoundException onfe) {
		    keyMatches = false;
		}
		if (keyMatches) {
		    /*
		     * Retrieve the value first, in case it has been removed
		     * from the DataManager.
		     */
		    V v = e.getValue();

		    DataManager dm = AppContext.getDataManager();
		    // mark that this table's state has changed
		    dm.markForUpdate(leaf);
		    leaf.size--;

		    // remove the value and reorder the chained keys
		    if (e == prev) { // if this was the first element
			leaf.table[i] = (next == null) 
			    ? null : dm.createReference(next);
		    } else {
			prev.setNext(next);
		    }

		    removeEntryFromInsertionList(e);
		    checkEntryPointers(e);
		    checkIterators(e);

		    // if this data structure is responsible for the
		    // persistence lifetime of the key or value, remove them
		    // from the data store
		    e.unmanage();

		    // NOTE: this is where we would attempt a merge operation
		    // if we decide to later support one
		    return v;
		}
	    }
	    prev = e;
	    e = e.next();
	}
	return null;
    }

    /**
     * When removing the provided entry, this method updates the first
     * and last entry name bindings as necessary if the removed
     * element was bound to either of these names
     *
     * @param e the entry being removed
     */
    private void checkEntryPointers(PrefixEntry<K,V> e) {
	DataManager dm = AppContext.getDataManager();

	PrefixEntry<K,V> first = 
	    uncheckedCast(dm.getBinding(nameForFirstEntry));

	PrefixEntry<K,V> last  = 
	    uncheckedCast(dm.getBinding(nameForLastEntry));
	
	if (e.equals(first)) {
	    if (e.nextInsert == null)
		dm.removeBinding(nameForFirstEntry);
	    else
		dm.setBinding(nameForFirstEntry, e.nextInsert.get());
	}
	else if (e.equals(last)) {
	    if (e.prevInsert == null)
		dm.removeBinding(nameForLastEntry);
	    else
		dm.setBinding(nameForLastEntry, e.prevInsert.get());
	}
    }

    /**
     * When removing the provided entry, this method updates the
     * doubly linked list of entries that keeps track of their
     * insertion order.
     *
     * @param e the entry being removed.
     */
    private void removeEntryFromInsertionList(PrefixEntry<K,V> e) {
	// update the insertion order chain
	PrefixEntry<K,V> prev = (e.prevInsert == null) 
	    ? null : e.prevInsert.getForUpdate();
	PrefixEntry<K,V> next = (e.nextInsert == null)
	    ? null : e.nextInsert.getForUpdate();
	
	if (prev != null)
	    prev.nextInsert = e.nextInsert;
	if (next != null)
	    next.prevInsert = e.prevInsert;
    }

    /**
     * Checks the state of all {@link
     * ConcurrentInsertionOrderIterator} instances to see if the
     * provided entry, which is being removed, is their next entry to
     * return, and updates their state accordingly.  
     *
     * <p>
     * 
     * Note that this effect is only meaningful to iterators that are
     * in a serialized state at the time of this call.  This method
     * will never be called if the iterator is currently traversing on
     * the entry prior to the one removed.  This is due to the fact
     * that the {@link
     * ScalableLinkedHashMap#removeEntryFromInsertionList(PrefixEntry)}
     * method has to acquire a write lock on the entry the iterator is
     * currently accessing.  Therefore, either the iterator will have
     * to abort and this update will succeed, in which case, the
     * iterator will deserialize again and update to the correct
     * state.  Or, the task doing the removal will abort and the
     * iterator will proceed with its traversal.
     *
     * @param entry the entry being removed
     */
    private void checkIterators(PrefixEntry<K,V> entry) {
	Map<String,ManagedReference<PrefixEntry<K,V>>>
	    iteratorToCurrentEntry = 
	    serializedIteratorsNextElementsRef.get().get();

	DataManager dm = AppContext.getDataManager();
	ManagedReference entryRef = dm.createReference(entry);
	
	// examine each iterator's next entry and see if it is the one
	// we have just removed
	for (Map.Entry<String,ManagedReference<PrefixEntry<K,V>>> e :
		 iteratorToCurrentEntry.entrySet()) {
	    
	    ManagedReference<PrefixEntry<K,V>> nextEntry = e.getValue();

	    // if the iterator was going to return the removed entry
	    // next, then we need to update it with the nextInsert
	    // value from the removed entry
	    if (nextEntry != null && nextEntry.equals(entryRef)) {

		// mark that the map has changed
		dm.markForUpdate(serializedIteratorsNextElementsRef.get());
		
		// then update the iterators next value
		e.setValue(entry.nextInsert);
	    }
	}

    }



    /**
     * Removes the entry with the given hash code and key reference, if
     * present.
     *
     * @param hash the hash code
     * @param keyRef the reference for the entry key
     *
     * @see ConcurrentInsertionOrderIterator#remove ConcurrentInsertionOrderIterator.remove
     */
    void remove(int hash, ManagedReference<?> keyRef) {
	int index = indexFor(hash);
	PrefixEntry<K,V> prev = null;
	for (PrefixEntry<K,V> e = getBucket(index); e != null; e = e.next()) {
	    if (unsignedLessThan(hash, e.hash)) {
		break;
	    } else if (e.hash == hash && keyRef.equals(e.keyRef())) {

		// mark that this leaf table's state has changed
		DataManager dm = AppContext.getDataManager();
		dm.markForUpdate(this);
		size--;

		// update the prefix entry chain in the bucket
		if (prev == null) {
		    PrefixEntry next = e.next();
		    table[index] = (next == null)
			? null : dm.createReference(next);
		} else {
		    prev.setNext(e.next());
		}

		removeEntryFromInsertionList(e);
		checkEntryPointers(e);
		checkIterators(e);		

		// free up any Serializable objects that were managing
		e.unmanage();

		break;
	    }
	    prev = e;
	}
    }

    /**
     * Returns the left-most leaf table from this node in the prefix tree.
     *
     * @return the left-most child under this node
     */
    ScalableLinkedHashMap<K,V> leftMost() {
	// NOTE: the left-most node will have a bit prefix of all zeros, which
	// is what we use when searching for it
	return lookup(0x0);
    }

    PrefixEntry<K,V> firstEntry() {
	try {
	    return uncheckedCast(AppContext.getDataManager().
				 getBinding(nameForFirstEntry));
	}
	catch (NameNotBoundException nnbe) {
	    // occurs if size == 0
	    return null;
	}
    }


    /**
     * Returns the bucket index for this hash value given the provided number
     * of buckets.
     *
     * @param h the hash value
     *
     * @return the index for the given hash
     */
    private int indexFor(int h) {
	/*
	 * Return the bits immediately to the right of those used to choose
	 * this node from the parent, but using the full complement of bits if
	 * this is a very deep node.  Using the bits immediately after the
	 * directory bits insures that the buckets are ordered the same way as
	 * the nodes would be after a split.
	 */
	int leafBits = requiredNumBits(leafCapacity);
	int leftOffset = Math.min(INT_SIZE, depth + leafBits);
	return highBits(h, leftOffset) & (leafCapacity - 1);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation removes from the {@code DataManager} all non-{@code
     * ManagedObject} keys and values persisted by this map, as well as objects
     * that make up the internal structure of the map itself.
     */
    public void removingObject() {
	/*
	 * Only operate on the top-level node.  Don't check the parentRef field
	 * to determine if the node is a top-level node since we'll be clearing
	 * that field on the children on the root node.
	 */
	if (depth == 0) {
	    removeChildrenAndEntries();
	}
    }

    /**
     * Recursively removes the children and entries of this node, performing
     * the actual removal in a separate task to avoid scaling problems.
     */
    private void removeChildrenAndEntries() {
	AppContext.getDataManager().markForUpdate(this);
	TaskManager taskManager = AppContext.getTaskManager();
	if (isLeafNode()) {
	    taskManager.scheduleTask(new RemoveNodeEntriesTask(this));
	    table = null;
	} else {
	    taskManager.scheduleTask(new RemoveNodesTask<K,V>(this));
	    nodeDirectory = null;
	}
    }

    /**
     * Removes up to MAX_REMOVE_ENTRIES entries from this leaf node, returning
     * true if they were all removed.
     */
    boolean removeSomeLeafEntries() {
	assert isLeafNode();
	AppContext.getDataManager().markForUpdate(this);
	int count = removeSomeLeafEntriesInternal(table);
	if (count == -1) {
	    size = 0;
	    return true;
	} else {
	    size -= count;
	    return false;
	}
    }

    /**
     * Removes up to MAX_REMOVE_ENTRIES entries from the specified array of
     * entries, returning true if they were all removed.
     */
    static boolean removeSomeLeafEntries(ManagedReference[] table) {
	return removeSomeLeafEntriesInternal(table) == -1;
    }

    /**
     * An internal method for removing entries.  Returns the number of entries
     * removed, or -1 if they were all removed.
     */
    private static int removeSomeLeafEntriesInternal(ManagedReference[] table) {
	DataManager dm = AppContext.getDataManager();
	int count = 0;
	for (int i = 0; i < table.length; i++) {
	    if (table[i] != null) {

		// try to remove entries in the chain until we run of
		// out entries in this bucket or we hit the limit on
		// the maximum number of entries to remove
		ManagedReference<PrefixEntry> cur = uncheckedCast(table[i]);
		PrefixEntry e = cur.get();

		do {
		    // free any resources created by the entry 
		    e.unmanage();
		    
		    // NOTE: we don't need to call these because the
		    // global call to clear already took care it this
		    // for us
		    //
 		    // removeEntryFromInsertionList(e);
 		    // checkEntryPointers(e);
 		    // checkIterators(e);

		    PrefixEntry next = e.next();
		    table[i] = (next == null) ? null : dm.createReference(next);

		    if (++count >= MAX_REMOVE_ENTRIES) {
			return count;
		    }
		    
		    // move to the next entry in the bucket chain
		    e = next;
		} while (e != null);
	    }
	}
	return -1;
    }

    /**
     * A task that removes the entries in a leaf node, performing a limited
     * amount of work each time it runs, and rescheduling itself if there is
     * more work to do.  The entries are copied from the node so that the node
     * can continue to be used.
     */
    private static final class RemoveNodeEntriesTask
	implements ManagedObject, Serializable, Task
    {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The entries to remove. */ 
	private final ManagedReference[] table;

	/** Creates an instance for the specified leaf node. */
	RemoveNodeEntriesTask(ScalableLinkedHashMap node) {
	    assert node.isLeafNode();
	    table = removeNulls(node.table);
	}

	/** Returns an array with the nulls removed. */
	private static ManagedReference[] removeNulls(ManagedReference[] table) 
	{
	    int count = 0;
	    for (ManagedReference ref : table) {
		if (ref != null) {
		    count++;
		}
	    }
	    ManagedReference[] result = new ManagedReference[count];
	    int i = 0;
	    for (ManagedReference ref : table) {
		if (ref != null) {
		    result[i++] = ref;
		}
	    }
	    return result;
	}

	/**
	 * Removes some entries, scheduling the task again if there are more,
	 * and otherwise removing this task object.
	 */
	public void run() {
	    if (!removeSomeLeafEntries(table)) {
		AppContext.getTaskManager().scheduleTask(this);
	    } else {
		AppContext.getDataManager().removeObject(this);
		Runnable r = noteDoneRemoving;
		if (r != null) {
		    r.run();
		}
	    }
	}
    }

    /**
     * A task that recursively removes the children and entries of a directory
     * node by depth-first recursion.  Does not remove the node itself, and
     * makes a copy of the node's children so that the node can continue to be
     * used.
     *
     * Each time the task is run it moves to the next non-empty leaf node,
     * removes a limited number of entries and just emptied nodes, and if
     * needed walks up the node graph to the next non-empty directory node.  If
     * there is more work to do, it reschedules the task, otherwise it removes
     * it.  This scheme insures that the number of nodes visited per run is the
     * same as the number needed to perform a lookup, and limits the number of
     * entries visited, all in an attempt to keep the work small enough to fit
     * within the transaction timeout.
     */
    private static final class RemoveNodesTask<K,V>
	implements ManagedObject, Serializable, Task
    {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the current node. */
	private ManagedReference<ScalableLinkedHashMap<K,V>> currentNodeRef;

	/** A collection of references to more nodes to remove. */
	private final Stack<ManagedReference<ScalableLinkedHashMap<K,V>>> nodeRefs =
	    new Stack<ManagedReference<ScalableLinkedHashMap<K,V>>>();

	/**
	 * A stack of values for each directory node at or above the current
	 * node that represent the offset of the child within that node to
	 * traverse in order to the find a non-empty leaf.
	 */
	private final Stack<Integer> offsets = new Stack<Integer>();

	/** Creates an instance for the specified directory node. */
	private RemoveNodesTask(ScalableLinkedHashMap<K,V> node) {
	    assert !node.isLeafNode();
	    DataManager dm = AppContext.getDataManager();
	    ManagedReference<ScalableLinkedHashMap<K,V>> lastRef = null;
	    for (int i = 0; i < node.nodeDirectory.length; i++) {
		ManagedReference<ScalableLinkedHashMap<K,V>> ref =
		    uncheckedCast(node.nodeDirectory[i]);
		/* Skip clearing duplicate nodes in the directory */
		if (ref != lastRef) {
		    ScalableLinkedHashMap<K,V> child = ref.get();
		    /*
		     * Clear the parent reference so we don't walk up to the
		     * root node, which is being reused.
		     */
		    dm.markForUpdate(child);
		    child.parentRef = null;
		    if (lastRef == null) {
			currentNodeRef = ref;
		    } else {
			nodeRefs.add(ref);
		    }
		    lastRef = ref;
		}
	    }
	    offsets.push(0);
	}

	/**
	 * Removes some entries, rescheduling the task if there is more work to
	 * do, or else removing the task if it is done.
	 */
	public void run() {
	    if (doWork()) {
		AppContext.getTaskManager().scheduleTask(this);
	    } else {
		AppContext.getDataManager().removeObject(this);
		Runnable r = noteDoneRemoving;
		if (r != null) {
		    r.run();
		}
	    }
	}

	/** Removes some entries, returning true if there is more to do. */
	private boolean doWork() {
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.markForUpdate(this);
	    ScalableLinkedHashMap<K,V> node = currentNodeRef.get();
	    /* Find the leaf node */
	    if (!node.isLeafNode()) {
		while (true) {
		    currentNodeRef =
			uncheckedCast(node.nodeDirectory[offsets.peek()]);
		    node = currentNodeRef.get();
		    if (node.isLeafNode()) {
			break;
		    }
		    offsets.push(0);
		}
	    }
	    if (!node.removeSomeLeafEntries()) {
		/* More entries in this node */
		return true;
	    }
	    /* Search the parents for a non-empty node, removing empty ones */
	    while (true) {
		currentNodeRef = node.parentRef;
		dataManager.removeObject(node);
		if (currentNodeRef == null) {
		    break;
		}
		int offset = offsets.pop();
		node = currentNodeRef.get();
		ManagedReference<?> childRef = node.nodeDirectory[offset];
		while (++offset < node.nodeDirectory.length) {
		    /* Skip clearing duplicate nodes in the directory */
		    if (childRef != node.nodeDirectory[offset]) {
			offsets.push(offset);
			/* More work under this node */
			return true;
		    }
		}
	    }
	    if (nodeRefs.isEmpty()) {
		/* No more top-level nodes */
		return false;
	    }
	    /* Select the next top-level node */
	    currentNodeRef = nodeRefs.pop();
	    offsets.clear();
	    offsets.push(0);
	    return true;
	}
    }

    


    /**
     * An implementation of {@code Entry} that incorporates information about
     * the prefix at which it is stored, as well as whether the {@link
     * ScalableLinkedHashMap} is responsible for the persistent lifetime of the key
     * and value.
     *
     * <p>
     *
     * If a key or value that does not implement {@link ManagedObject} is
     * stored in the map, then it is wrapped using the {@link
     * ManagedSerializable} utility class so that the entry may have a {@code
     * ManagedReference} to the value, rather than a standard reference.
     *
     * <p>
     *
     * This class performs an optimization if both key and value do not
     * implement {@code ManagedObject}.  In this case, both objects will be
     * stored together in a {@link KeyValuePair}, which reduces the number of
     * accesses to the data store.
     *
     * <p>
     *
     * Note that the ordering of entries depends on the object ID of the
     * managed object that represents the key.  To insure the proper behavior
     * of the iterator, that object ID must not change after the entry is
     * created.  The object ID used is either that of the key itself, if the
     * key is a managed object, or that of the ManagedSerializable wrapper
     * created to wrap either the just key or both the key and value.  In
     * either case, the managed object associated with the key is determined in
     * the constructor
     *
     * @see ManagedSerializable
     */
    private static class PrefixEntry<K,V>
	implements Entry<K,V>, ManagedObject, Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The state bit mask for when the key is wrapped. */
	private static final int KEY_WRAPPED = 1;

	/** The state bit mask for when the value is wrapped. */
	private static final int VALUE_WRAPPED = 2;

	/** The state value for when the key and value are stored as a pair. */
	private static final int KEY_VALUE_PAIR = 4;

	/**
	 * A reference to the key, or key and value pair, for this entry. The
	 * class type of this reference will depend on whether the map is
	 * managing the key, and whether the key and value are paired.
	 *
	 * @serial
	 */
	private final ManagedReference<?> keyOrPairRef;

	private ManagedReference<PrefixEntry<K,V>> prevInsert;

	private ManagedReference<PrefixEntry<K,V>> nextInsert;


	/**
	 * A reference to the value, or null if the key and value are paired.
	 * The class type of this reference will depend on whether this map is
	 * managing the value.
	 *
	 * @serial
	 */
	private ManagedReference<?> valueRef;

	/**
	 * The next chained entry in this entry's bucket.
	 *
	 * @serial
	 */
	ManagedReference<PrefixEntry<K,V>> nextRef;

	/**
	 * The hash value for this entry.  This value is also used to compute
	 * the prefix.
	 *
	 * @serial
	 */
	final int hash;

	/**
	 * The state of the key and value, which is either a non-zero
	 * combination of the KEY_WRAPPED and VALUE_WRAPPED, or is
	 * KEY_VALUE_PAIR.
	 *
	 * @serial
	 */
	byte state = 0;

	/**
	 * Constructs a new {@code PrefixEntry}.
	 *
	 * @param h the hash code for the key
	 * @param k the key
	 * @param v the value
	 */
	PrefixEntry(int h, K k, V v) {
	    this.hash = h;

	    this.prevInsert = null;
	    this.nextInsert = null;

	    DataManager dm = AppContext.getDataManager();

	    // if both the key and value are not ManagedObjects, we can save a
	    // get() and createReference() call each by merging them in a
	    // single KeyValuePair
	    if (!(k instanceof ManagedObject) &&
		!(v instanceof ManagedObject))
	    {
		setKeyValuePair();
		keyOrPairRef = dm.createReference(
		    new ManagedSerializable<Object>(
			new KeyValuePair<K,V>(k, v)));
	    } else {
		// For the key and value, if each is already a ManagedObject,
		// then we obtain a ManagedReference to the object itself,
		// otherwise, we need to wrap it in a ManagedSerializable and
		// get a ManagedReference to that
		setKeyWrapped(!(k instanceof ManagedObject));
		keyOrPairRef = dm.createReference(
		    isKeyWrapped() ? new ManagedSerializable<Object>(k) : k);
		setValueWrapped(!(v instanceof ManagedObject));
		valueRef = dm.createReference(
		    isValueWrapped() ? new ManagedSerializable<V>(v) : v);
	    }
	}

	/** Returns whether the key and value are stored as a pair. */
	private boolean isKeyValuePair() {
	    return state == KEY_VALUE_PAIR;
	}

	/** Notes that the key and value are stored as a pair. */
	private void setKeyValuePair() {
	    state = KEY_VALUE_PAIR;
	}

	/** Returns whether the key is wrapped. */
	private boolean isKeyWrapped() {
	    return (state & KEY_WRAPPED) != 0;
	}

	/** Notes whether the key is wrapped. */
	private void setKeyWrapped(boolean wrapped) {
	    if (wrapped) {
		state &= ~KEY_VALUE_PAIR;
		state |= KEY_WRAPPED;
	    } else {
		assert state != KEY_VALUE_PAIR;
		state &= ~KEY_WRAPPED;
	    }
	}

	/** Returns whether the value is wrapped. */
	private boolean isValueWrapped() {
	    return (state & VALUE_WRAPPED) != 0;
	}

	/** Notes whether the value is wrapped. */
	private void setValueWrapped(boolean wrapped) {
	    if (wrapped) {
		state &= ~KEY_VALUE_PAIR;
		state |= VALUE_WRAPPED;
	    } else {
		assert state != KEY_VALUE_PAIR;
		state &= ~VALUE_WRAPPED;
	    }
	}

	/**
	 * Returns the key stored by this entry.  If the mapping has been
	 * removed from the backing map before this call is made, an {@code
	 * ObjectNotFoundException} will be thrown.
	 *
	 * @return the key stored in this entry
	 * @throws ObjectNotFoundException if the key in the backing map was
	 *         removed prior to this call
	 */
	public final K getKey() {
	    if (isKeyValuePair()) {
		ManagedSerializable<KeyValuePair<K,V>> msPair =
		    uncheckedCast(keyOrPairRef.get());
		return msPair.get().getKey();
	    } else if (isKeyWrapped()) {
		ManagedSerializable<K> msKey =
		    uncheckedCast(keyOrPairRef.get());
		return msKey.get();
	    } else {
		@SuppressWarnings("unchecked")
		K result = (K) keyOrPairRef.get();
		return result;
	    }
	}

	/**
	 * Returns the {@code ManagedReference} for the key used in this entry.
	 * These references are not guaranteed to stay valid across
	 * transactions and should therefore not be used except for
	 * comparisons.
	 *
	 * @return the {@code ManagedReference} for the key
	 *
	 * @see ConcurrentInsertionOrderIterator
	 */
	ManagedReference<?> keyRef() {
	    return keyOrPairRef;
	}

	/**
	 * Returns the value stored by this entry.  If the mapping has been
	 * removed from the backing map before this call is made, an {@code
	 * ObjectNotFoundException} will be thrown.
	 *
	 * @return the value stored in this entry
	 * @throws ObjectNotFoundException if the value in the backing map was
	 *         removed prior to this call
	 */
	public final V getValue() {
	    if (isKeyValuePair()) {
		ManagedSerializable<KeyValuePair<K,V>> msPair =
		    uncheckedCast(keyOrPairRef.get());
		return msPair.get().getValue();
	    } else if (isValueWrapped()) {
		ManagedSerializable<V> msValue = uncheckedCast(valueRef.get());
		return msValue.get();
	    } else {
		@SuppressWarnings("unchecked")
		V value = (V) valueRef.get();
		return value;
	    }
	}

	PrefixEntry<K,V> next() {
	    return (nextRef == null) ? null : nextRef.get();
	}

	void setNext(PrefixEntry<K,V> next) {
	    if (next == null)
		nextRef = null;
	    else {
		DataManager dm = AppContext.getDataManager();
		ManagedReference<PrefixEntry<K,V>> ref = 
		    dm.createReference(next);
		if (nextRef == null || !(ref.equals(nextRef))) {
		    dm.markForUpdate(this);
		    nextRef = ref;
		}
	    }
	}

	/**
	 * Replaces the previous value of this entry with the provided value.
	 *
	 * @param newValue the value to be stored
	 * @return the previous value of this entry
	 */
	public final V setValue(V newValue) {
	    checkSerializable(newValue, "newValue");
	    V oldValue = getValue();
	    setValueInternal(newValue);
	    return oldValue;
	}

	/**
	 * Replaces the previous value of this entry with the provided value.
	 * Does not check if the new value is serializable or return the old
	 * value.
	 *
	 * @param newValue the value to be stored
	 */
	void setValueInternal(V newValue) {
	    DataManager dm = AppContext.getDataManager();
	    dm.markForUpdate(this);

	    if (newValue instanceof ManagedObject) {
		if (isKeyValuePair()) {
		    /* Switch from wrapping key/value pair to wrapping key */
		    ManagedSerializable<KeyValuePair<K,V>> msPair =
			uncheckedCast(keyOrPairRef.get());
		    ManagedSerializable<K> msKey =
			uncheckedCast(keyOrPairRef.get());
		    msKey.set(msPair.get().getKey());
		    setKeyWrapped(true);
		} else if (isValueWrapped()) {
		    dm.removeObject(valueRef.get());
		    setValueWrapped(false);
		}
		valueRef = dm.createReference(newValue);
	    } else if (isKeyValuePair()) {
		ManagedSerializable<KeyValuePair<K,V>> msPair =
		    uncheckedCast(keyOrPairRef.get());
		msPair.get().setValue(newValue);
	    } else if (isKeyWrapped()) {
		/* Switch from wrapping key to wrapping key/value pair */
		ManagedSerializable<K> msKey =
		    uncheckedCast(keyOrPairRef.get());
		ManagedSerializable<KeyValuePair<K,V>> msPair =
		    uncheckedCast(keyOrPairRef.get());
		msPair.set(new KeyValuePair<K,V>(msKey.get(), newValue));
		if (isValueWrapped()) {
		    dm.removeObject(valueRef.get());
		}
		setKeyValuePair();
	    } else if (isValueWrapped()) {
		ManagedSerializable<V> ms = uncheckedCast(valueRef.get());
		ms.set(newValue);
	    } else {
		valueRef = dm.createReference(
		    new ManagedSerializable<V>(newValue));
		setValueWrapped(true);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean equals(Object o) {
	    if (o instanceof Entry) {
		Entry e = (Entry) o;
		if (safeEquals(getKey(), e.getKey()) &&
		    safeEquals(getValue(), e.getValue()))
		{
		    return true;
		}
	    }
	    return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public final int hashCode() {
	    K key = getKey();
	    V value = getValue();
	    return ((key==null   ? 0 : key.hashCode())) ^
		    (value==null ? 0 : value.hashCode());
	}

	/**
	 * Returns the string form of this entry as {@code entry}={@code
	 * value}.
	 */
	public String toString() {
	    return getKey() + "=" + getValue();
	}

	/**
	 * Removes any {@code Serializable} managed by this entry from the data
	 * manager.  This should only be called from {@link
	 * ScalableLinkedHashMap#clear ScalableLinkedHashMap.clear}, {@link
	 * ScalableLinkedHashMap#remove ScalableLinkedHashMap.remove}, or {@link #remove
	 * remove} under the condition that this entry's map-managed object
	 * will never be referenced again by the map.
	 */
	final void unmanage() {
	    DataManager dm = AppContext.getDataManager();

	    if (isKeyValuePair()) {
		try {
		    dm.removeObject(keyOrPairRef.get());
		} catch (ObjectNotFoundException onfe) {
		    // silent
		}
	    } else {
		if (isKeyWrapped()) {
		    try {
			dm.removeObject(keyOrPairRef.get());
		    } catch (ObjectNotFoundException onfe) {
			// silent
		    }
		}
		if (isValueWrapped()) {
		    try {
			dm.removeObject(valueRef.get());
		    } catch (ObjectNotFoundException onfe) {
			// silent
		    }
		}
	    }
	}
    }

    /**
     * A utility class for PrefixEntry for storing a {@code Serializable} key
     * and value together in a single {@code ManagedObject}.  By combining both
     * together, this saves a {@link ManagedReference#get ManagedReference.get}
     * call per access.
     */
    private static class KeyValuePair<K,V> implements Serializable {

	private static final long serialVersionUID = 0x1L;

	private final K key;

	private V value;

	KeyValuePair(K key, V value) {
	    this.key = key;
	    this.value = value;
	}

	K getKey() {
	    return key;
	}

	V getValue() {
	    return value;
	}

	void setValue(V value) {
	    this.value = value;
	}
    }

    /**
     * A concurrent, persistable {@code Iterator} implementation for
     * the {@code ScalableLinkedHashMap}.  This implementation is
     * stable with respect to concurrent changes to the associated
     * collection, but may ignore additions and removals made to the
     * collection during iteration, and may also visit more than once
     * a key value that is removed and re-added.
     *
     * <p>
     *
     * If an iterator is created for an empty map, and then
     * serialized, it will remain valid upon any subsequent
     * deserialization.  An iterator in this state, where it has been
     * created but {@code next} has never been called, will always
     * begin an the first entry in the map, if any, since its
     * deserialization.
     *
     * <p> 
     *
     * Instance of this class are <i>not</i> designed to be shared
     * between concurrent tasks.
     */
    abstract static class ConcurrentInsertionOrderIterator<E,K,V>
	implements Iterator<E>, Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 2;

	private static final String ITERATOR_INSTANCE_COUNT = 
	    ConcurrentInsertionOrderIterator.class.getName() + "-instance-num";

	/**
	 * Whether the current entry has already been removed
	 */
	private boolean currentRemoved;

	/**
	 * A reference to the current entry
	 */
	private ManagedReference<PrefixEntry<K,V>> nextEntry;

	/**
	 * A reference to the current entry
	 */
	private ManagedReference<PrefixEntry<K,V>> curEntry;

	private final String iteratorName;

	/**
	 * A reference to the backing map
	 */
	private ManagedReference<ScalableLinkedHashMap<K,V>> backingMapRef;

	private ManagedReference<ManagedSerializable<Map<
	    String,ManagedReference<PrefixEntry<K,V>>>>>
	    serializedIteratorsNextElementsRef;

	private boolean nextEntryWasNullOnCreation;

	private transient boolean recheckNextEntry;
	
	/**
	 * Constructs a new {@code ConcurrentEntryOrderIterator}.
	 *
	 * @param backingMap the root node of the {@code ScalableLinkedHashMap}
	 */
	ConcurrentInsertionOrderIterator(ScalableLinkedHashMap<K,V> 
					 backingMap) {

	    currentRemoved = false;
	    curEntry = null;

	    DataManager dm = AppContext.getDataManager();
	    PrefixEntry<K,V> first = backingMap.firstEntry();	    
	    nextEntry = (first == null) ? null : dm.createReference(first);

	    // mark if the next entry was null.  If so, if we
	    // serialize this iterator and then deserialize it, we
	    // should refresh the first entry in the map	   
	    nextEntryWasNullOnCreation = nextEntry == null;

	    serializedIteratorsNextElementsRef = 
		backingMap.serializedIteratorsNextElementsRef;

	    backingMapRef = dm.createReference(backingMap);
	    
	    // get a unique name for this iterator
	    iteratorName = newIteratorName();

	    recheckNextEntry = false;
	    updatePersistentNextEntry();
	}

	/**
	 * Returns the next unique name for a {@code
	 * ConcurrentInsertionOrderIterator} instance.
	 *
	 * @return the next unique name
	 */
	private static String newIteratorName() {
	    
	    DataManager dm = AppContext.getDataManager();
	    int instanceNum = -1;
	    try {
		ManagedSerializable<Integer> mostRecentIteratorNum = 
		    uncheckedCast(dm.getBinding(ITERATOR_INSTANCE_COUNT));
		instanceNum = mostRecentIteratorNum.get().intValue() + 1;
	    } catch (NameNotBoundException nnbe) {
		// we must be the first instance
		instanceNum = 0;
	    }
	    
	    // update the most recent instance binding to reflect this
	    // iterator's creation
	    dm.setBinding(ITERATOR_INSTANCE_COUNT, 
			  new ManagedSerializable<Integer>(instanceNum));
	    
	    // then set up the prefix string
	    String iteratorSuffix = String.format("%014d", instanceNum);
	    
	    // combine the common class prefix for entries with this
	    // instance's suffix to create a unique namespace for this map
	    return ConcurrentInsertionOrderIterator.class.getName() + 
		iteratorSuffix;
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean hasNext() {
	    if (recheckNextEntry) {
		checkForNextEntryUpdates();
	    }
	    return nextEntry != null;
	}

	/**
	 * After deserialization, this iterator should check that its
	 * reference to the next entry is still valid.  Two cases
	 * exist to check.
	 *
	 * <p>
	 *
	 * First, if this iterator was created based on an empty map,
	 * and has never iterated over the first element, the iterator
	 * must check whether any new elements exist in the map.  Once
	 * an element exists, the iterator updates its nextEntry
	 * reference and is no longer in the "empty map" state.
	 *
	 * <p>
	 *
	 * In the second case, while serialized, the next entry could
	 * have been removed from the backing map.  Should it have
	 * been removed, the map will have updated the shared mapping
	 * from iterator to next entry, with a reference as to what
	 * this iterator's new next entry should.  
	 *
	 * @see ScalableLinkedHashMap#checkIterators(PrefixEntry)
	 */
	private void checkForNextEntryUpdates() {
	    System.out.println("before recheck, " + this 
			       + ".nextEntry = " + nextEntry);

	    // check to see if this iterator was created with a null
	    // first entry.  This flag will only be true if this
	    // iterator has never seen a first entry
	    if (nextEntryWasNullOnCreation) {

		// see if the first entry in the map is now non-null
		PrefixEntry<K,V> first = backingMapRef.get().firstEntry();
		nextEntry = (first == null) ? null : 
		    AppContext.getDataManager().createReference(first);
		
		// mark if the next entry is now non-null.  If so, if
		// we unset the flag and the iterator, which will
		// never be set to true again.
		if (nextEntry != null)
		    nextEntryWasNullOnCreation = false;		
	    }
	    // otherwise, this iterator has seen at least one entry
	    // and had updated the map prior to serialization what its
	    // next entry was.  In this case, we should check to see
	    // if the next entry prior to serialization has been removed.
	    else {
		Map<String,ManagedReference<PrefixEntry<K,V>>>
		    iteratorToNextEntry = 
		    serializedIteratorsNextElementsRef.getForUpdate().get();
		
		// remove ourselves and assign whatever is listed as the
		// next entry for us
		nextEntry = iteratorToNextEntry.remove(iteratorName);
		
		// obtain a read lock on the entry to start with
		if (nextEntry != null)
		    nextEntry.get();
	    }

	    recheckNextEntry = false;

	    System.out.println("after recheck, " + this 
			       + ".nextEntry = " + nextEntry);
	}

	/**
	 * Returns the next entry in the {@code ScalableLinkedHashMap}.  Note that
	 * due to the concurrent nature of this iterator, this method may skip
	 * elements that have been added after the iterator was constructed.
	 * Likewise, it may return new elements that have been added.  This
	 * implementation is guaranteed never to return an element more than
	 * once.
	 *
	 * <p>
	 *
	 * This method will never throw a {@link
	 * java.util.ConcurrentModificationException}.
	 *
	 * @return the next entry in the {@code ScalableLinkedHashMap}
	 *
	 * @throws NoSuchElementException if no further entries exist
	 */
	Entry<K,V> nextEntry() {
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }
	    DataManager dm = AppContext.getDataManager();

	    PrefixEntry<K,V> entry = nextEntry.get();
	    
	    currentRemoved = false;

	    // update the iterator state
	    curEntry = nextEntry;
	    nextEntry = entry.nextInsert;
		
	    // save the next entry that we're going to return in case
	    // we're serialized after this call
	    updatePersistentNextEntry();

	    return entry;
	}

	/**
	 * Saves the {@code ManagedReference} of the next entry that
	 * this iterator is going to return to a peristant state.
	 * This enables the iterator to receive updates from the map
	 * while serialized if the next element that it should return
	 * was removed.
	 *
	 * @see ScalableLinkedHashMap#checkIterators(PrefixEntry)
	 */
	private void updatePersistentNextEntry() {
	    Map<String,ManagedReference<PrefixEntry<K,V>>>
		iteratorToNextEntry = 
		serializedIteratorsNextElementsRef.getForUpdate().get();

	    iteratorToNextEntry.put(iteratorName, nextEntry);
	}

	/**
	 * {@inheritDoc}
	 */
	public void remove() {
	    if (currentRemoved) {
		throw new IllegalStateException(
		    "The current element has already been removed");
	    } else if (curEntry == null) {
		throw new IllegalStateException("No current element");
	    }
	    try {
		K key = curEntry.get().getKey();
		backingMapRef.get().remove(key);
	    } catch (ObjectNotFoundException onfe) {
		// this happens if the current entry was removed while
		// this iterator was serialized.  We could check for
		// this upon deserialization, but instead we rely on
		// this lazy check at call-time here to avoid doing
		// any unnecessary work.
	    }
		currentRemoved = true;
	}

	/**
	 * Returns the unique name for this iterator
	 */
	public String toString() {
	    return iteratorName;
	}

	private void writeObject(ObjectOutputStream s)
	    throws IOException {
	    // write out all the non-transient state
	    s.defaultWriteObject();
	}


	/**
	 * Reconstructs the {@code ConcurrentInsertionOrderIterator}
	 * from the provided stream and marks that this iterator
	 * should check that its next entry is still valid
	 *
	 * @see ConcurrentInsertionOrderIterator#checkForNextEntryUpdates()
	 */
	private void readObject(ObjectInputStream s)
	    throws IOException, ClassNotFoundException {
	    
	    // read in all the non-transient state
	    s.defaultReadObject();	
	    
	    // mark that the iterator should recheck what its next
	    // element is prior to returning any next entry.
	    recheckNextEntry = true;
	}
    }

    /**
     * An iterator over the entry set
     */
    private static final class EntryIterator<K,V>
	extends ConcurrentInsertionOrderIterator<Entry<K,V>,K,V> {

	private static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	EntryIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
	}

	/**
	 * {@inheritDoc}
	 */
	public Entry<K,V> next() {
	    return nextEntry();
	}
    }

    /**
     * An iterator over the keys in the map.
     */
    private static final class KeyIterator<K,V>
	extends ConcurrentInsertionOrderIterator<K,K,V>
    {
	private static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	KeyIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
	}

	/**
	 * {@inheritDoc}
	 */
	public K next() {
	    return nextEntry().getKey();
	}
    }


    /**
     * An iterator over the values in the tree.
     */
    private static final class ValueIterator<K,V>
	extends ConcurrentInsertionOrderIterator<V,K,V> {

	public static final long serialVersionUID = 0x1L;

	/**
	 * Constructs the iterator
	 *
	 * @param root the root node of the backing trie
	 */
	ValueIterator(ScalableLinkedHashMap<K,V> backingMap) {
	    super(backingMap);
	}

	/**
	 * {@inheritDoc}
	 */
	public V next() {
	    return nextEntry().getValue();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all the
     * mappings contained in this map.  The returned {@code Set} is backed by
     * the map, so changes to the map will be reflected by this view.  Note
     * that the time complexity of the operations on this set will be the same
     * as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the set of all mappings contained in this map
     */
    public Set<Entry<K,V>> entrySet() {
	return new EntrySet<K,V>(this);
    }

    /**
     * An internal-view {@code Set} implementation for viewing all the entries
     * in this map.
     */
    private static final class EntrySet<K,V>
	extends AbstractSet<Entry<K,V>>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the prefix tree.
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> rootRef;

	/**
	 * A cached version of the root node for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> root;

	EntrySet(ScalableLinkedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}

	private void checkCache() {
	    if (root == null) {
		root = rootRef.get();
	    }
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
	    if (!(o instanceof Entry)) {
		return false;
	    }
	    Entry<K,V> e = uncheckedCast(o);
	    PrefixEntry<K,V> pe = root.getEntry(e.getKey());
	    return pe != null && pe.equals(e);
	}

	public void clear() {
	    checkCache();
	    root.clear();
	}
    }

    /**
     * Returns a concurrent, {@code Serializable} {@code Set} of all the keys
     * contained in this map.  The returned {@code Set} is backed by the map,
     * so changes to the map will be reflected by this view.  Note that the
     * time complexity of the operations on this set will be the same as those
     * on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the set of all keys contained in this map
     */
    public Set<K> keySet() {
	return new KeySet<K,V>(this);
    }

    /**
     * An internal collections view class for viewing the keys in the map.
     */
    private static final class KeySet<K,V>
	extends AbstractSet<K>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the prefix tree.
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> rootRef;

	/**
	 * A cached version of the root node for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> root;

	KeySet(ScalableLinkedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}

	private void checkCache() {
	    if (root == null) {
		root = rootRef.get();
	    }
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
     * Returns a concurrent, {@code Serializable} {@code Collection} of all the
     * values contained in this map.  The returned {@code Collection} is backed
     * by the map, so changes to the map will be reflected by this view.  Note
     * that the time complexity of the operations on this set will be the same
     * as those on the map itself.
     *
     * <p>
     *
     * The iterator returned by this set also implements {@code Serializable}.
     * See the <a href="#iterator">javadoc</a> for details.
     *
     * @return the collection of all values contained in this map
     */
    public Collection<V> values() {
	return new Values<K,V>(this);
    }

    /**
     * An internal collections-view of all the values contained in this map.
     */
    private static final class Values<K,V>
	extends AbstractCollection<V>
	implements Serializable {

	private static final long serialVersionUID = 0x1L;

	/**
	 * A reference to the root node of the prefix tree.
	 *
	 * @serial
	 */
	private final ManagedReference<ScalableLinkedHashMap<K,V>> rootRef;

	/**
	 * A cached version of the root node for faster accessing.
	 */
	private transient ScalableLinkedHashMap<K,V> root;

	Values(ScalableLinkedHashMap<K,V> root) {
	    this.root = root;
	     rootRef = AppContext.getDataManager().createReference(root);
	}

	private void checkCache() {
	    if (root == null) {
		root = rootRef.get();
	    }
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
     * Saves the state of this {@code ScalableLinkedHashMap} instance to the provided
     * stream.
     *
     * @serialData a {@code boolean} of whether this instance was a leaf node.
     *             If this instance is a leaf node, this boolean is followed by
     *             a series {@code PrefixEntry} instances, some of which may be
     *             chained.  The deserialization should count each chained
     *             entry towards the total size of the leaf.
     */
    private void writeObject(ObjectOutputStream s)
	throws IOException {
	// write out all the non-transient state
	s.defaultWriteObject();

    }

    /**
     * Reconstructs the {@code ScalableLinkedHashMap} from the provided stream.
     */
    private void readObject(ObjectInputStream s)
	throws IOException, ClassNotFoundException {

	// read in all the non-transient state
	s.defaultReadObject();	

    }

    /**
     * Returns the minimum depth for any leaf node in the map's backing tree.
     * The root node has a depth of {@code 1}.  This method is used for
     * testing.
     *
     * @return the minimum depth
     */
    private int getMinTreeDepth() {
	ScalableLinkedHashMap<K,V> cur = leftMost();
	int minDepth = cur.depth;
	while (cur.rightLeafRef != null) {
	    cur = cur.rightLeafRef.get();
	    minDepth = Math.min(minDepth, cur.depth);
	}
	return minDepth + 1;
    }


    /**
     * Returns the maximum depth for any leaf node in the map's backing tree.
     * The root node has a depth of {@code 1}.  This method is used for
     * testing.
     *
     * @return the maximum depth
     */
    private int getMaxTreeDepth() {
	ScalableLinkedHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
	while (cur.rightLeafRef != null) {
	    cur = cur.rightLeafRef.get();
	    maxDepth = Math.max(maxDepth, cur.depth);
	}
	return maxDepth + 1;
    }

    /**
     * Returns the average of all depth for the leaf nodes in the map's backing
     * tree.  The root node has a depth of {@code 1}.  This method is used for
     * testing.
     *
     * @return the average depth
     */
    private double getAvgTreeDepth() {
	ScalableLinkedHashMap<K,V> cur = leftMost();
	int maxDepth = cur.depth;
	int leaves = 1;
	while (cur.rightLeafRef != null) {
	    cur = cur.rightLeafRef.get();
	    maxDepth = Math.max(maxDepth, cur.depth);
	    leaves++;
	}
	return (maxDepth / (double) leaves) + 1;
    }
}
