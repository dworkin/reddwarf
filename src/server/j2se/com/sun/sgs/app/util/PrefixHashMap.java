package com.sun.sgs.app.util;

import java.io.Serializable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
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
 * The {@code PrefixHashMap} is implemented as a prefix tree of hash
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
 * An instance of {@code PrefixHashMap} offers one parameters for
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
 * @version 1.4
 *
 * @see Object#hashCode()
 * @see Map
 * @see Serializable
 * @see ManagedObject
 */
@SuppressWarnings({"unchecked"})
public class PrefixHashMap<K,V> 
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
     * The default value for maximum number of levels of the tree to
     * collapse when none is specified in the constructor.
     */
    private static final int DEFAULT_MAX_COLLAPSE = 5; // 32 nodes total
    
    /**
     * The default number of {@code PrefixEntry} entries per
     * array for a leaf table.
     */
    // NOTE: *must* be a power of 2.
    private static final int DEFAULT_LEAF_CAPACITY = 1 << 8; // 256

    /**
     * The maximum depth of this tree
     */
    // NOTE: this is currently limited by the lenth of the prefix,
    //       which currently is implemented as an int
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
    private ManagedReference leftLeaf;

    /**
     * The leaf table immediately to the right of this table, or {@code
     * null} if this table is an intermediate node in tree.
     */
    private ManagedReference rightLeaf;


    // NOTE: During split() operations, children nodes may be replaced
    //       with CollapsedNode instances, which turn this node into a
    //       larger logical node.  For this reason, only three
    //       configurations of children are possible:
    //   
    //       1. leftChild == null && collapsedLeftChild == null
    //          - if this node is a leaf node
    //
    //       2. leftChild != null && collapsedLeftChild == null
    //          - if this node refers to a child that has node been
    //            collapsed.  This may happen in two circumstances:
    //            a. if the depth of the child is less than the
    //               minDepth
    //            b. if this node roots a new logical node, and its
    //               child is a leaf node
    //
    //       3. leftChild == null && collapsedLeftChild != null
    //          - if this node roots a new logical node and its
    //            children are no longer leaf nodes and have therefore
    //            been collapsed
    //
    //       The fourth configuration is invalid and should never
    //       occur.  Node replacement occurs in the collapse() and
    //       disperse() operations.  See there for implementation
    //       details.
    
    /**
     * The left child of this node, or {@code null} if this node is a
     * leaf or if the left leaf has been replaced by a {@code
     * CollapsedNode}.
     */
    private ManagedReference leftChild;
    
    /**
     * The collapsed left child of this node or {@code null} if this
     * node is a leaf node, or if the left child has not yet been
     * replaced with a {@code CollapsedNode}
     */
    private CollapsedNode<K,V> collapsedLeftChild;

    /**
     * The right child of this node, or {@code null} if this node is a
     * leaf or if the right child has been replaced by a {@code
     * CollapsedNode}.
     */
    private ManagedReference rightChild;

    /**
     * The collapsed right child of this node or {@code null} if this
     * node is a leaf node, or if the right child has not yet been
     * replaced with a {@code CollapsedNode}
     */	
    private CollapsedNode<K,V> collapsedRightChild;

    /**
     * The fixed-size table for storing all Map entries.
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
     * The size of the {@code PrefixEntry} table.
     */
    private final int leafCapacity;

    /**
     * The minimum number of concurrent write operations to support.
     */
    private final int minConcurrency;

    /**
     * The minimum depth of the tree, which is controlled by the
     * minimum concurrency factor
     */
    private int minDepth;

    /**
     * The depth of this node in the tree
     */
    private final int depth;

    /**
     * The maximum number of levels of the tree to collapse into a
     * single logical node.
     * 
     * @see #split()
     */
    private final int maxCollapse;
    
    /** 
     * Constructs an empty {@code PrefixHashMap} at the provided
     * depth, with the specified minimum concurrency, split factor and
     * load factor.
     *
     * @param depth the depth of this table in the tree
     * @param minConcurrency the minimum number of concurrent write
     *        operations to support
     * @param splitThreshold the number of entries at a leaf node that
     *        will cause the leaf to split
     * @param mergeTheshold the numer of entries at a leaf node that
     *        will cause the leaf to attempt merging with its sibling
     *
     * @throws IllegalArgumentException if the depth is out of the
     *         range of valid prefix lengths
     * @throws IllegalArgumentException if minConcurrency is non positive
     * @throws IllegalArgumentException if the split threshold is non
     *         positive
     * @throws IllegalArgumentException if the merge threshold is
     *         greater than or equal to the split threshold
     * @throws IllegalArgumentException if {@code maxCollapse} is less
     *         than zero
     */
    // NOTE: this constructor is currently left package private but
    // future implementations could expose these implementation
    // details.
    PrefixHashMap(int depth, int minConcurrency, int splitThreshold,
			  int mergeThreshold, int maxCollapse) {
	if (depth < 0 || depth > 32) {
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
	if (maxCollapse < 0) {
	    throw new IllegalArgumentException("Illegal maximum collapse: " + 
					       maxCollapse);	    
	}

	this.depth = depth;
	this.minConcurrency = minConcurrency;
	for (minDepth = 0; (1 << minDepth) < minConcurrency; minDepth++)
	    ;
	size = 0;
	parent = null;
	leftLeaf = null;
	rightLeaf = null;
	leftChild = null;
	rightChild = null;
	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];

	this.splitThreshold = splitThreshold;
	this.mergeThreshold = mergeThreshold;

	collapsedLeftChild = null;
	collapsedRightChild = null;
	this.maxCollapse = maxCollapse;

	// Only the root note should ensure depth, otherwise this call
	// causes the children to be created in depth-first fashion,
	// which prevents the leaf references from being correctly
	// established
	if (depth == 0) 
	    ensureDepth(minDepth);
    }

    /** 
     * Constructs an empty {@code PrefixHashMap} with the provided
     * minimum concurrency.
     *
     * @param minConcurrency the minimum number of concurrent write
     *        operations supported
     *
     * @throws IllegalArgumentException if minConcurrency is non positive
     */
    public PrefixHashMap(int minConcurrency) {
	this(0, minConcurrency, DEFAULT_SPLIT_THRESHOLD, 
	     DEFAULT_MERGE_THRESHOLD, DEFAULT_MAX_COLLAPSE);
    }


    /** 
     * Constructs an empty {@code PrefixHashMap} with the default
     * minimum concurrency (1).
     */
    public PrefixHashMap() {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_MERGE_THRESHOLD, 
	     DEFAULT_MAX_COLLAPSE);
    }

    /**
     * Constructs a new {@code PrefixHashMap} with the same mappings
     * as the specified {@code Map}, and the default 
     * minimum concurrency (1).
     *
     * @param m the mappings to include
     *
     * @throws NullPointerException if the provided map is null
     */
    public PrefixHashMap(Map<? extends K, ? extends V> m) {
	this(0, DEFAULT_MINIMUM_CONCURRENCY, 
	     DEFAULT_SPLIT_THRESHOLD, DEFAULT_MERGE_THRESHOLD,
	     DEFAULT_MAX_COLLAPSE);
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
	    split(); // split first to ensure breadth-first creation
	    rightChild.get(PrefixHashMap.class).ensureDepth(minDepth);
	    leftChild.get(PrefixHashMap.class).ensureDepth(minDepth);
	}
    }

    /**
     * Clears the map of all entries in {@code O(n log(n))} time.
     * When clearing, all values managed by this map will be removed
     * from the persistence mechanism.
     */
    public void clear() {
	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);
	
	// special case: leaf node
	if (leftChild == null && collapsedLeftChild == null) { 
	    for (PrefixEntry<K,V> e : table) {
		if (e != null) {
		    // remove all references that we are responsible
		    // for, only calling this when we are sure that we
		    // will never reference these entries again
		    e.unmanage();
		}
	    }
	}
	else {
	    if (leftChild != null) {
		PrefixHashMap l = leftChild.get(PrefixHashMap.class);
		l.clear();
		dm.removeObject(l);
	    }
	    else 
		collapsedLeftChild.clear();
	    if (rightChild != null) {
		PrefixHashMap r = rightChild.get(PrefixHashMap.class);
		r.clear();
		dm.removeObject(r);
	    }
	    else
		collapsedRightChild.clear();
	}

	// special case: root node	    
	if (depth == 0) { 
	    if (table == null) // restore the table, if it was deleted
		table = new PrefixEntry[leafCapacity];
	    else // otherwise, clear it
		Arrays.fill(table, null);
	    size = 0;
	    parent = null;
	    leftLeaf = null;
	    rightLeaf = null;
	    leftChild = null;
	    rightChild = null;
	    collapsedLeftChild = null;
	    collapsedRightChild = null;
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
	return getEntry(key) != null;
    }

    /**
     * Returns the {@code PrefixEntry} associated with this key
     */ 
    private PrefixEntry<K,V> getEntry(Object key) {
	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
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
     */
    public boolean containsValue(Object value) {
	for (V v : values()) {
	    if (v == value || (v != null && v.equals(value)))
		return true;
	}
	return false;
    }

    /**
     * Merges the children nodes of this node into this node and
     * removes them.  This method should <i>only</i> be called by
     * {@code #disperse(int,int)} after the appropriate conditions
     * have been checked.
     *
     * @see #addEntry(PrefixEntry, int)
     * @see #disperse(int, int)
     */
    private void merge() {	   
	DataManager dataManager = AppContext.getDataManager();

 	PrefixHashMap<K,V> leftChild_ = leftChild.get(PrefixHashMap.class);
 	PrefixHashMap<K,V> rightChild_ = 
 	    rightChild.get(PrefixHashMap.class);

	dataManager.markForUpdate(this);

	// recreate our table, as it was made null in split()
	table = new PrefixEntry[leftChild_.table.length];

	// iterate over each child's table, combining the entries into
	// this node's table.
	for (int i = 0; i < table.length; ++i) {

 	    for (PrefixEntry<K,V> e = leftChild_.table[i]; e != null; e = e.next) 
		addEntry(e, i);    

 	    for (PrefixEntry<K,V> e = rightChild_.table[i]; e != null; e = e.next) 
		addEntry(e, i);	    
	}

	// update the remaining family references
	leftLeaf = leftChild_.leftLeaf;
	rightLeaf = rightChild_.rightLeaf;
	
	// ensure that the child's neighboring leaf reference now
	// point to this table
	if (leftLeaf != null) {
	    PrefixHashMap leftLeaf_ = leftLeaf.get(PrefixHashMap.class);
	    dataManager.markForUpdate(leftLeaf_);
	    leftLeaf_.rightLeaf = dataManager.createReference(this);
	}
	if (rightLeaf != null) {
	    PrefixHashMap rightLeaf_ = rightLeaf.get(PrefixHashMap.class);
	    dataManager.markForUpdate(rightLeaf_);	    
	    rightLeaf_.leftLeaf = dataManager.createReference(this);
	}	
	
	// mark this table as a leaf by removing the child
	// references
	leftChild = null;
	rightChild = null;

	// now delete the leaf tables
	dataManager.removeObject(leftChild_);
	dataManager.removeObject(rightChild_);
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
	    
	if (leftChild != null)  // can't split an intermediate node!
	    return;
	
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	PrefixHashMap<K,V> leftChild_ = 
	    new PrefixHashMap<K,V>(depth+1, minConcurrency, 
				   splitThreshold, mergeThreshold, maxCollapse);
	PrefixHashMap<K,V> rightChild_ = 
	    new PrefixHashMap<K,V>(depth+1, minConcurrency, 
				   splitThreshold, mergeThreshold, maxCollapse);

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
		((((e.hash << depth) >>> 31) == 1) ? leftChild_ : rightChild_).
		    addEntry(e, i);
		e = next;
	    }
	}

	// null out the intermediate node's table as an optimization
	// to reduce serialization time.
	table = null;
	size = 0;
		
	// create the references to the new children
	leftChild = dataManager.createReference(leftChild_);
	rightChild = dataManager.createReference(rightChild_);
	    
	if (leftLeaf != null) {
	    PrefixHashMap leftLeaf_ = leftLeaf.get(PrefixHashMap.class);
	    leftLeaf_.rightLeaf = leftChild;

	}
	
	if (rightLeaf != null) {
	    PrefixHashMap rightLeaf_ = rightLeaf.get(PrefixHashMap.class);
	    rightLeaf_.leftLeaf = rightChild;
	}

	// update the family links
	ManagedReference thisRef = dataManager.createReference(this);
	leftChild_.rightLeaf = rightChild;
	leftChild_.leftLeaf = leftLeaf;
	leftChild_.parent = thisRef;
	rightChild_.leftLeaf = leftChild;
	rightChild_.rightLeaf = rightLeaf;
	rightChild_.parent = thisRef;			  
	
	// invalidate this node's leaf references
	leftLeaf = null;
	rightLeaf = null;
	
 	if (parent != null && 
	    depth > minDepth && 
	    maxCollapse > 0 &&
	    (depth + 1) % maxCollapse != 0)
 	    parent.get(PrefixHashMap.class).collapse(depth, prefix);
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
    private PrefixHashMap<K,V> lookup(int prefix) {

	// a leading bit of 1 indicates the left child prefix
	PrefixHashMap<K,V> leaf = this;
	int original = prefix;
	
	while (true) {
	    // a leading bit of 1 indicates the left child prefix
	    if ((prefix >>> 31) == 1) { // look left
		if (leaf.leftChild != null)
		    leaf = leaf.leftChild.get(PrefixHashMap.class);
		else if (leaf.collapsedLeftChild != null) {
		    // walk the compressed tree to find the next
		    // PrefixHashMap node
		    CollapsedNode<K,V> intermediate = leaf.collapsedLeftChild;
		    leaf = bypassIntermediate(intermediate, prefix << 1);
		}
		else { // this node is a leaf
		    break;
		}

	    }
	    else { // look right
		if (leaf.rightChild != null)
		    leaf = leaf.rightChild.get(PrefixHashMap.class);
		else if (leaf.collapsedRightChild != null) {
		    // walk the compressed tree to find the next
		    // PrefixHashMap node
		    CollapsedNode<K,V> intermediate = leaf.collapsedRightChild;
		    leaf = bypassIntermediate(intermediate, prefix << 1);
		}
		else { // this node is a leaf
		    break;
		}
	    }
	    prefix = original << leaf.depth;
	}

	return leaf;
    }


    /**
     * Returns the next {@code PrefixHashMap} node on the path
     * specified by {@code prefix} starting at the tree rooted at
     * {@code node}.  Note that the path specified {@code prefix} must
     * begin immediately (i.e. the highest order bit of {@code prefix}
     * should denote the next child to select).
     *
     * @param node the root of the tree to search
     * @param prefix the path to take when searching the tree
     *
     * @return the next {@code PrefixHashMap} found on the path
     */
    private static PrefixHashMap bypassIntermediate(CollapsedNode node, 
						    int prefix) {
	PrefixHashMap next = null;
	while (next == null) {
	    if ((prefix >>> 31) == 1) {
		if (node.leftChild != null)
		    next = node.leftChild.
			get(PrefixHashMap.class);
		else
		    node = node.leftCollapsed;
	    }
	    else {
		if (node.rightChild != null)
		    next = node.rightChild.
			get(PrefixHashMap.class);
		else
		    node = node.rightCollapsed;
	    }
	    prefix <<= 1;
	}
	
	return next;
    }

    /**
     * Finds the specified {@code PrefixHashMap} node in the tree and
     * replaces with a {@code CollapsedNode}, updating family
     * references as necessary.  This method should <i>only</i> be
     * called from {@link #split()} after the appropriate conditions
     * have been checked.
     *
     * <p>
     *
     * Performing the collapse operation moves the specified child
     * node into the logical node rooted at this node, by including
     * the child in the serialization through the node replacement.
     *
     * @param depth the depth of the node that needs to be replaced
     *        with a {@code CollapsedNode}
     * @param prefix the prefix that leads to the node that should be
     *        replaced
     *
     * @see split()
     */
    private void collapse(int depth, int prefix) {

	Walkable child = new Walkable(this), parent = null;

	int p = prefix;
	
	// shift off the bits for this node's depth
	prefix <<= this.depth;
	
	// now walk the tree to see what kind of parent node we need
	// to modify
	
	int curDepth = this.depth;

	while (curDepth < depth) {
	    
	    parent = child;
	    
	    child = ((prefix >>> 31) == 1) 
		? child.leftChild() 
		: child.rightChild();

	    curDepth++;
	    prefix <<= 1;
	}

	CollapsedNode<K,V> replacement = new CollapsedNode<K,V>();
	// NOTE: we can directly assign from the Walkable because
	// the child Walkable is guaranteed to be the
	// PrefixHashMap that we are going to replace.
	replacement.leftChild = child.map.leftChild;
	replacement.rightChild = child.map.rightChild;

	DataManager dm = AppContext.getDataManager();
	dm.removeObject(child.map);

	
	// either the parent of the split node is a PrefixHashMap
	// (which will happen for the most immediate notes), or for
	// all other nodes, its parent will already be a CollapsedNode
	if (parent.map != null) {
	    
	    // determine which of this node's children now needs to be
	    // the CollapsedNode
	    if ( ((p << (depth-1)) >>> 31) == 1) {
		// left child needs to be replaced
		parent.map.leftChild = null;
		parent.map.collapsedLeftChild = replacement;
	    } 
	    else { // replace the right child
		parent.map.rightChild = null;
		parent.map.collapsedRightChild = replacement;
	    }
	    
	}
	// else, the parent of the node that needs to be replaced is
	// actually itself a collapsed node
	else {
	    if ( ((p << (depth-1)) >>> 31) == 1) {
		// left child needs to be replaced
		parent.node.leftChild = null;
		parent.node.leftCollapsed = replacement;
	    }
	    else {
		parent.node.rightChild = null;
		parent.node.rightCollapsed = replacement;
	    }
	}

	// lastly, set the new PrefixHashMap leaf nodes from the split
	// to have this node as their parent
	PrefixHashMap<K,V> l = replacement.leftChild.get(PrefixHashMap.class);
	PrefixHashMap<K,V> r = replacement.rightChild.get(PrefixHashMap.class);
	ManagedReference thisRef = dm.createReference(this);
	l.parent = thisRef;
	r.parent = thisRef;

	// NOTE: we do not need to call markForUpdate on the children,
	// as this has already been called on these nodes during the
	// split() operation that led to this call
	dm.markForUpdate(this);
    }


    /**
     * Locates the specified leaf node under the logical node rooted
     * at this node and attempts to merge it with its sibling, after
     * performing any necessary internal replacements.  When merging
     * two siblings, the parent of the nodes may have been replaced
     * with a {@code CollapsedNode}, and therefore needs to be swapped
     * out with a {@code PrefixHashMap} node before the {@code merge}
     * operation can take place.  This method is also responsible for
     * aborting any merge attempts that are invalid due to the sibling
     * still being too large in size, or where the sibling is
     * attempting to merge with a non-leaf. 
     *
     *
     * @param depth the depth of the node that should merge its
     *        children        
     * @param prefix the prefix that leads to the node that leads to
     *        the node that should be merged into its parent
     *
     * @see #merge()
     */
    private void disperse(int depth, int prefix) {


	if (parent == null) // this node is the root!
	    return; // do not merge

	// child = node that will hold its merged children
	Walkable child = new Walkable(this), parent = null;

	int p = prefix;
	
	// shift off the bits for this node's depth
	prefix <<= this.depth;
	
	// now walk the tree to see what kind of parent node we need
	// to modify
	
	// NOTE: This code is nasty.
	int curDepth = this.depth;

	// REMINDER: do bounds checking?
	while (curDepth < depth) {
	    
	    parent = child;
	    
	    child = ((prefix >>> 31) == 1) 
		? child.leftChild() 
		: child.rightChild();

	    curDepth++;
	    prefix <<= 1;
	}

	// check that the child node would have two PrefixHashMap
	// children that could be merged; if the child does, check
	// that the size of the children is of sufficently small to
	// warrant a merge.
	PrefixHashMap<K,V> rightChild_, leftChild_;
	if (child.node != null) {
	    if (child.node.leftChild == null || child.node.rightChild == null) 
		return;	    
	    else if (((leftChild_ = 
		       child.node.leftChild.get(PrefixHashMap.class)).size >
		      mergeThreshold) ||
		     ((rightChild_ = 
		       child.node.rightChild.get(PrefixHashMap.class)).size >
		      mergeThreshold)) 
		return;
	}
	else {
	    if (child.map.leftChild == null || child.map.rightChild == null) 
		return;	    
	    else if (((leftChild_ = 
		       child.map.leftChild.get(PrefixHashMap.class)).size >
		      mergeThreshold) ||
		     ((rightChild_ = 
		       child.map.rightChild.get(PrefixHashMap.class)).size >
		      mergeThreshold)) 
		return;	
	}
	
	// lastly, perform a check that neither of the children are
	// the empty root node of a logical node.  This case happens
	// when a leaf node attempts to merge with its sibling who is
	// a PrefixHashMap, but is not a leaf node.  A non-leaf
	// PrefixHashMap will not have a table.	
	if (leftChild_.table == null || rightChild_.table == null) 
	    return;
	
	// at this point, both chilren are sufficiently small enough
	// that the parent node should merge them.  If the child node
	// is a PrefixHashMap, call merge(), otherwise, the child is
	// an intermediate node and must be replaced before the merge
	// operation can be called
	
	if (child.map != null) {
	    child.map.merge();
	    // the parent does not need to update any state, so we can
	    // return at this point
	    return;
	}
	
	// at this point, an update to this logical node is
	// inevitable, so get the DataManager for update calls
	DataManager dm = AppContext.getDataManager();

	// first, replace the child intermediate node with a
	// PrefixHashMap
	
	// REMINDER: can we speed this up by reusing one of the
	// children nodes?
	
	PrefixHashMap<K,V> replacement = 
	    new PrefixHashMap<K,V>(rightChild_.depth-1, 
				   rightChild_.minConcurrency,
				   rightChild_.splitThreshold,
				   rightChild_.mergeThreshold,
				   rightChild_.maxCollapse);
	if (child.map != null) {
	    replacement.leftChild = child.map.leftChild;
	    replacement.rightChild = child.map.rightChild;
	}
	else {
	    replacement.leftChild = child.node.leftChild;
	    replacement.rightChild = child.node.rightChild;
	}

	ManagedReference replacementRef = dm.createReference(replacement);

	// next, correct the parent link to the child.  either the
	// parent of the newly-merged node is a PrefixHashMap (which
	// will happen for the most immediate notes), or for all other
	// nodes, its parent will already be a CollapsedNode
	if (parent.map != null) {
	    
	    // determine which of this node's children now needs to be
	    // the CollapsedNode
	    if ( ((p << (depth-1)) >>> 31) == 1) {
		// left child needs to be replaced
		parent.map.leftChild = replacementRef;
		parent.map.collapsedLeftChild = null;
	    } 
	    else { // replace the right child
		parent.map.rightChild = replacementRef;
		parent.map.collapsedRightChild = null;
	    }
	    
	}
	// else, the parent of the node that needs to be replaced is
	// actually itself a collapsed node
	else {
	    if ( ((p << (depth-1)) >>> 31) == 1) {
		// left child needs to be replaced
		parent.node.leftChild = replacementRef;
		parent.node.leftCollapsed = null;
	    }
	    else {
		parent.node.rightChild = replacementRef;
		parent.node.rightCollapsed = null;
	    }
	}
	
	// since we modified the parent reference, mark this node as
	// having changed
	dm.markForUpdate(this);

	// NOTE: we don't need to re-establish any further family
	// links because we inherit these from the children after the
	// merge
	
	// once the parent and children links have been updated, call
	// merge() on the child to have it subsume its children
	replacement.merge();
	
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

 	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
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

	int hash = (key == null) ? 0 : hash(key.hashCode());
	PrefixHashMap<K,V> leaf = lookup(hash);
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
	for (K k : m.keySet())
	    put(k, m.get(k));
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
     * Returns whether this map has no mappings.  This implemenation
     * runs in {@code O(1)} time.
     *
     * @return {@code true} if this map contains no mappings
     */
    public boolean isEmpty() {
	return leftChild != null || size == 0;
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

	// leaf node, short-circuit case
 	if (leftChild == null && collapsedLeftChild == null) 
 	    return size;

 	int totalSize = 0;
 	PrefixHashMap<K,V> cur = leftMost();
  	totalSize += cur.size;
  	while(cur.rightLeaf != null) {
	    totalSize += (cur = cur.rightLeaf.get(PrefixHashMap.class)).size;
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
	PrefixHashMap<K,V> leaf = lookup(hash);
	
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
		
		// lastly, if the leaf size is less than the size
		// threshold, attempt a merge
		if ((--leaf.size) <= mergeThreshold && leaf.depth > minDepth && 
		    leaf.parent != null) {
		    
		    leaf.parent.get(PrefixHashMap.class).
			disperse(leaf.depth-1, hash);
		}
		
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
    private PrefixHashMap<K,V> leftMost() {
	// NOTE: the left-most node will have a bit prefix of all
	// ones, which is what we use when searching for it
	return lookup(~0x0);
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
     * Returns a string represntation of all the elements under the
     * tree rooted at this node.  Each subtree's elements are denoted
     * by parentheses.
     *
     * @return a string represenation of the elements under the tree
     *         rooted at this node.
     */
    String treeString() {
	if (leftChild == null) {
	    String s = "(";
	    for (PrefixEntry e : table) {
		if (e != null) {
		    do {
			s += (e + ((e.next == null) ? "" : ", "));
			e = e.next;
		    } while (e != null);
		    s += ", ";
		}
	    }
	    return s.substring(0, s.length() - 2) + ")";
	}
	else {
	    PrefixHashMap l = leftChild.get(PrefixHashMap.class);
	    PrefixHashMap r = rightChild.get(PrefixHashMap.class);
	    return "(" + l.treeString() + ", " + r.treeString() + ")";
	}
    }


    /**
     * Returns a tree-like string representation of the map, including
     * all internal nodes.  An example string representation for a
     * full tree appears as follows:
     *
     * <pre>
     * ROOT:
     * |--CollapsedNode
     * o--|--(id: 13) contents: ("key"="value", etc.)
     * o--|--(id: 14) contents: ("foo"="bar")
     * |--(id: 12) contents: (...)
     * </pre>
     *
     * The id displayed is the same as the id associated with the
     * {@code ManagedReference} for that node in the tree.  Collapsed
     * nodes do not have any ids.
     *
     * @return a string representation of the tree
     */
    String treeDiagram() {
	String s = "ROOT: ";
	if (leftChild == null && collapsedLeftChild == null) 
	    s += "conents: " + treeString() + "\n";
	else {
	    s += "(id: " 
		+ AppContext.getDataManager().createReference(this).getId() 
		+ ")\n";
	    
	    s += (leftChild != null)
		? leftChild.get(PrefixHashMap.class).treeDiagram(1) 
		: collapsedLeftChild.treeDiagram(1);
	    s += (rightChild != null) 
		? rightChild.get(PrefixHashMap.class).treeDiagram(1)
		: collapsedRightChild.treeDiagram(1);
	}
	return s;
    }

    /**
     * Recursive helper method for {@link #treeDiagram()}.
     *
     * @param depth the indentation level for the string
     *        representation
     */
    private String treeDiagram(int depth) {
	String s; int i = 0;
	for (s = "|--"; ++i < depth; s = "o--" + s)
	    ;

	s += "(id: " 
	    + AppContext.getDataManager().createReference(this).getId() 
	    + ")";       

	if (leftChild == null && collapsedLeftChild == null) 
	    s += "contents: " + treeString() + "\n";
	else {
	    s += s + "\n";
	    s += (leftChild != null)
		? leftChild.get(PrefixHashMap.class).treeDiagram(depth+1) 
		: collapsedLeftChild.treeDiagram(depth+1);
	    s += (rightChild != null) 
		? rightChild.get(PrefixHashMap.class).treeDiagram(depth+1)
		: collapsedRightChild.treeDiagram(depth+1);
	}

	return s;
    }

    /**
     * An implementation of {@code Map.Entry} that incorporates
     * information about the prefix at which it is stored, as well as
     * whether the {@link PrefixHashMap} is responsible for the
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
    private static class PrefixEntry<K,V> implements Map.Entry<K,V>, Serializable {

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
	 * The hash value for this entry
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
	 * Constructs this {@code PrefixEntry}
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

	PrefixEntry(PrefixEntry<K,V> clone, PrefixEntry<K,V> next) { 
	    this.hash = clone.hash;
	    this.keyRef = clone.keyRef;
	    this.valueRef = clone.valueRef;
 	    this.keyValuePairRef = clone.keyValuePairRef;
	    this.isValueWrapped = clone.isValueWrapped;
	    this.isKeyWrapped = clone.isKeyWrapped;
 	    this.isKeyValueCombined = clone.isKeyValueCombined;
	    this.next = next;
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
		    K key = pair.getKey();
		    keyRef = dm.createReference(
		        new ManagedSerializable<K>(pair.getKey()));
		    dm.removeObject(pair);
		    isKeyWrapped = true;
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
	 * PrefixHashMap#clear()} and {@link
	 * PrefixHashMap#remove(Object)} under the condition that this
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
    private abstract class PrefixTreeIterator<E> 
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
	PrefixHashMap<K,V> curTable;

	/**
	 * Constructs the prefix table iterator.
	 *
	 * @param start the left-most leaf in the prefix tree
	 */
	PrefixTreeIterator(PrefixHashMap<K,V> start) {

	    curTable = start;
	    index = 0;
	    next = null;

	    // load in the first table that has an element
	    while (curTable.size == 0 && curTable.rightLeaf != null) 
		curTable = curTable.rightLeaf.get(PrefixHashMap.class);
		
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
  			curTable = curTable.rightLeaf.get(PrefixHashMap.class);
			
  			if (curTable.size == 0) 
 			    continue;
	
			// iterate to the next element
			for (index = 0; index < curTable.table.length &&
				 (next = curTable.table[index]) == null; ++index) 
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
	extends PrefixTreeIterator<Map.Entry<K,V>> {
	
	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	EntryIterator(PrefixHashMap<K,V> leftMostLeaf) {
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
    private final class KeyIterator extends PrefixTreeIterator<K> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	KeyIterator(PrefixHashMap<K,V> leftMostLeaf) {
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
    private final class ValueIterator extends PrefixTreeIterator<V> {

	/**
	 * Constructs the iterator
	 *
	 * @param leftModeLeaf the left mode leaf node in the prefix
	 *        tree
	 */
	ValueIterator(PrefixHashMap<K,V> leftMostLeaf) {
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
	private final PrefixHashMap<K,V> root;

	EntrySet(PrefixHashMap<K,V> root) {
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
	private final PrefixHashMap<K,V> root;

	KeySet(PrefixHashMap<K,V> root) {
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
	private final PrefixHashMap<K,V> root;

	public Values(PrefixHashMap<K,V> root) {
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
     * The internal, comressed node representation for storing child
     * references under a logical node.  When a leaf node splits and
     * becomes an intermediate node, it no longer requires all of the
     * state it once had.  Furthermore, serialization and locking
     * performance can be increased by loading the intermediate node
     * with its parent.  Therefore, a {@code CollapsedNode} is used in
     * place of a {@code PrefixHashMap} to hold the intermediate
     * state.
     *
     * <p>
     *
     * Internally, each {@code CollapsedNode} holds a reference to
     * both of its children.  A child may be either a {@link
     * ManagedReference} or another {@code CollapsedNode}.  At no
     * point should both of these fields for a child be set to
     * non-{@code null} values; either one or the other will be valid.
     *
     * @see PrefixHashMap#bypassIntermediate(CollapsedNode,int)
     * @see PrefixHashMap#disperse(int,int)
     * @see PrefixHashMap#collapse(int,int)
     *
     */
    private static class CollapsedNode<K,V> implements Serializable {

	private static final long serialVersionUID = 0x2L;

	/*
	 * The left child under this node.  Either one or the other of
	 * these fields should be null, never both.
	 */
	ManagedReference leftChild;
	CollapsedNode<K,V> leftCollapsed;
	

	/*
	 * The right child under this node.  Either one or the other of
	 * these fields should be null, never both.
	 */
	ManagedReference rightChild;
	CollapsedNode<K,V> rightCollapsed;
	
	public CollapsedNode() {

	}

	/**
	 * Recursively calls {@code clear()} on the children under it.
	 * This method is needed by {@link PrefixHashMap#clear()} to
	 * efficiently and cleanly walk the tree while removing its
	 * elements.
	 */
	void clear() {
	    if (leftChild != null)
		leftChild.get(PrefixHashMap.class).clear();
	    else
		leftCollapsed.clear();
	    if (rightChild != null)
		rightChild.get(PrefixHashMap.class).clear();
	    else
		rightCollapsed.clear();
	}

	/**
	 * Returns a string representation of the tree under this
	 * intermediate node.  This method is needed by {@code
	 * PrefixHashMap#treeDiagram(int)} to recursively and cleanly
	 * build up a string representation of the tree.  See {@code
	 * treeDiagram()} for a description of the string output.
	 *
	 * @param depth the depth of this node in the tree
	 *
	 * @return a string representation of the subtree rooted at
	 *         this node
	 */
	String treeDiagram(int depth) {
	    String s; int i = 0;
	    for (s = "|--"; ++i < depth; s = "o--" + s)
		;
	    
	    s += "Collapsed Node\n";

	    s += (leftChild != null)
		? leftChild.get(PrefixHashMap.class).treeDiagram(depth+1) 
		: leftCollapsed.treeDiagram(depth+1);
	    s += (rightChild != null) 
		? rightChild.get(PrefixHashMap.class).treeDiagram(depth+1)
		: rightCollapsed.treeDiagram(depth+1);
	    
	    return s;
	}
    }

    /**
     * An internal utilty class for wrapping the code necessary to
     * traverse the tree when both {@code CollapsedNode} and {@code
     * PrefixHashMap} nodes are present.
     *
     * @see PrefixHashMap#collapse(int,int)
     */
    private static class Walkable {

	/*
	 * Either of these is the internal state of the Walkable.  At
	 * any time, only one of these should be set to a non-null
	 * value.  The only way that both should be null is if a
	 * leftChild() or rightChild() call traverses too deep in the
	 * tree
	 */
	PrefixHashMap map;
	CollapsedNode node;	

	/**
	 * Constructs a Walkable starting with the provided map.
	 *
	 * @param map the root of the walkable tree
	 */
	public Walkable(PrefixHashMap map) {
	    this.map = map;
	    this.node = null;	    
	}

	/**
	 * Constructs a Walkable starting with the provided
	 * intermediate node.
	 *
	 * @param node the root of the walkable tree
	 */
	public Walkable(CollapsedNode node) {
	    this.node = node;
	    this.map = null;
	}

	/**
	 * Returns {@code true} if this Walkable represents a
	 * traversal that has gone past the depth of the leaf node.
	 * 
	 * @return {@code true} if this Walkable represents a
	 * traversal that has gone past the depth of the leaf node.
	 */
	public boolean isInvalid() {
	    return map == null && node == null;
	}

	/**
	 * Returns the left child under this {@code Walkable}.  If
	 * this instance is a leaf node, calling {@link #isInvalid()}
	 * on child will return true.
	 *
	 * @return the left child under this {@code Walkable}
	 */
	public Walkable leftChild() {
	    if (map != null) {
		return (map.leftChild != null)
		    ? new Walkable(map.leftChild.get(PrefixHashMap.class))
		    : new Walkable(map.collapsedLeftChild);
	    }
	    else { 
		return (node.leftChild != null)
		    ? new Walkable(node.leftChild.get(PrefixHashMap.class))
		    : new Walkable(node.leftCollapsed);
	    }
	}

	/**
	 * Returns the right child under this {@code Walkable}.  If
	 * this instance is a leaf node, calling {@link #isInvalid()}
	 * on child will return true.
	 *
	 * @return the right child under this {@code Walkable}
	 */
	public Walkable rightChild() {
	    if (map != null) {
		return (map.rightChild != null)
		    ? new Walkable(map.rightChild.get(PrefixHashMap.class))
		    : new Walkable(map.collapsedRightChild);
	    }
	    else {
		return (node.rightChild != null)
		    ? new Walkable(node.rightChild.get(PrefixHashMap.class))
		    : new Walkable(node.rightCollapsed);
	    }
	}
    }

    /**
     * Saves the state of this {@code PrefixHashMap} instance to the
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
	    for (int i = 0, j = 0; j < size; ++i) {
		if ((e = table[i]) != null) {
		    j++;
		    s.writeObject(table[i]);
		    for (; (e = e.next) != null; ++j)
			;
		}
	    }
	}
    }

    /**
     * Reconstructs the {@code PrefixHashMap} from the provided
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
