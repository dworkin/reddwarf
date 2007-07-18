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
 * replacement for the {@link HashMap} class as needed.  Developers
 * are encouraged to use this class when the size of a {@link HashMap}
 * causes sufficient contention due to serialization overhead.
 *
 * As the number of mappings increases, the mappings are distributed
 * through multiple objects in the datastore, thereby mitigating the
 * cost of serializing the map.  Furthermore, map operations have been
 * implemented to minimize the locality of change.  As the map grows
 * in size, mutable operations change only a small number of managed
 * objects, thereby increasing the concurrency for multiple writes.
 *
 * This implementation supports the contract that all keys and values
 * must be {@link Serializable}.  If a developer provides a {@code
 * Serializable} key or value that is <i>not</i> a {@code
 * ManagedObject}, this implementation will take responsibility for
 * the lifetime of that object in the datastore.  The developer will
 * be responsible for the lifetime of all {@link ManagedObject} stored
 * in this map.
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
 * An instance of {@code PrefixHashMap} offers one parameters for
 * performance tuning: {@code splitFactor}.  The {@code splitFactor}
 * determines at what size to divide a leaf table in the prefix tree
 * into two separate tables.  This parameter is similar to the {@code
 * loadFactor} parameter in {@code HashMap}, except that a division in
 * a leaf node is strictly local and require no tree-global locks.
 *
 * As a general rule, the default {@code splitFactor} (1.0) provides a
 * good tradeoff between datastore contention and serialization
 * cost. Developers may find that performance for highly contended
 * maps may improve if the {@code splitFactor} is lowered, thereby
 * increasing the number of leaves, but also increasing the locality
 * of updates.
 *
 * This class implements all of the optional {@code Map} operations
 * and supports both {@code null} keys and values.  This map provides
 * no guarantees on the order of elements when iterating over the key
 * set, values or entry set.
 * 
 * The iterator for this implemenation will never throw a {@link
 * ConcurrentModificationException}, unlike many of the other {@code
 * Map} implementations.   
 *
 * @since 1.0
 * @version 1.0
 */
@SuppressWarnings({"unchecked"})
public class PrefixHashMap<K,V> 
    extends AbstractMap<K,V>
    implements Map<K,V>, Serializable, ManagedObject {

    private static final long serialVersionUID = 1337;

    /**
     * The granuality in bytes of the lock size in the persistence
     * mechanism.  
     */
    public static final int PERSISTENCE_LOCK_SIZE_BYTES = 8192;

    /**
     * The split factor used when none is specified in the constructor.
     */
    private static final float DEFAULT_SPLIT_FACTOR = 1.0f;    

    /**
     * The split factor used when none is specified in the
     * constructor.
     */
    private static final float DEFAULT_MERGE_FACTOR = .25f;

    
    /**
     * The default number of {@code ManagedReference} entries per
     * array for a leaf table.
     */
    // NOTE: this should almost certainly be updated to include class
    //       overhead for the object that contains this array
    private static final int DEFAULT_LEAF_CAPACITY = 512;
    
    /**
     * The parent node directly above this.  For the root node, this
     * should always be null.
     */
    ManagedReference parent;


    // NOTE: the leftLeaf and rightLeaf references allow us to quickly
    //       iterate over the tree without needing to touch all of the
    //       intermediate nodes.

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


    // NOTE: either both the left and right child will be present, or
    //       neither will be
    
    /**
     * The leaf table, if any, under this table to the left
     */
    ManagedReference leftChild;

    /**
     * The leaf table, if any, under this table to the right
     */
    ManagedReference rightChild;
	
    /**
     * The fixed-size table for storing all Map entries.
     */
    PrefixEntry[] table;    

    /**
     * The number of elements in this table.  Note that this is
     * <i>not</i> the total number of elements in the entire tree.
     */
    private int size;

    /**
     * The maximum number of elements in this table before it will
     * split this table into two leaf tables.
     *
     * @see #split()
     */
    private int splitThreshold;

    /**
     * The minimum number of elements in this table before it will
     * attempt to merge itself with its sibling.
     *
     * @see #merge()
     */
    private int mergeThreshold;


    /**
     * The number of {@code PrefixEntry} at leaf node.
     */
    private final int leafCapacity;

    /**
     * The fraction of the leaf capacity that will cause the leaf to
     * split.
     *
     * @see #split()
     */
    private final float splitFactor;

    /**
     * The fraction of the leaf capacity that will cause the leaf to
     * merge.
     *
     * @see #merge()
     */
    private final float mergeFactor;

    
    /** 
     * Constructs an empty {@code PrefixHashMap} with the specified load
     * factor.
     *
     * @param splitFactor the fraction of the leaf capacity which will
     *        cause the leaf to split
     *
     * @throws IllegalArgumentException if the load factor is non positive
     */
    public PrefixHashMap(float splitFactor) {
	if (splitFactor <= 0) {
	    throw new IllegalArgumentException("Illegal split factor: " + 
					      splitFactor);	    
	}
	size = 0;
	parent = null;
	leftLeaf = null;
	rightLeaf = null;
	leftChild = null;
	rightChild = null;
	this.leafCapacity = DEFAULT_LEAF_CAPACITY;
	table = new PrefixEntry[leafCapacity];
	this.splitFactor = splitFactor;
	this.mergeFactor = leafCapacity * DEFAULT_MERGE_FACTOR;
	this.splitThreshold = Math.max((int)(splitFactor * leafCapacity), 1);
	this.mergeThreshold = Math.max((int)(splitFactor * leafCapacity), 0);
    }

    /** 
     * Constructs an empty {@code PrefixHashMap} with the default load
     * factor (1.0).
     */
    public PrefixHashMap() {
	this(DEFAULT_SPLIT_FACTOR);
    }

    /**
     * Constructs a new {@code PrefixHashMap} with the same mappings
     * as the specified {@code Map}, and the default {@code
     * splitFactor} (1.0).
     *
     * @param m the mappings to include
     *
     * @throws NullPointerException if the provided map is null
     */
    public PrefixHashMap(Map<? extends K, ? extends V> m) {
	this(DEFAULT_SPLIT_FACTOR);
	if (m == null)
	    throw new NullPointerException("The provided map is null");
	putAll(m);
    }


    /**
     * Clears the map of all entries in {@code O(n log(n))} time.
     * When clearing, all values managed by this map will be removed
     * from the persistence mechanism.
     */
    public void clear() {
	DataManager dm = AppContext.getDataManager();
	dm.markForUpdate(this);
	// go through and remove all the leaves
	if (leftChild == null) { // leaf node
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
	    PrefixHashMap l = leftChild.get(PrefixHashMap.class);
	    PrefixHashMap r = rightChild.get(PrefixHashMap.class);
	    l.clear();
	    r.clear();
	    dm.removeObject(l);
	    dm.removeObject(r);
	}
	if (parent == null) { // root node	    
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
	}
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(Object key) {
	int hash = (key == null) ? 0 : key.hashCode();
// 	return containsKey(key, hash, hash);
	
	Prefix prefix = new Prefix(hash);
	PrefixHashMap<K,V> leaf = lookup(prefix);
	for (PrefixEntry e = leaf.table[indexFor(hash, leaf.table.length)]; 
	     e != null; 
	     e = e.next) {
	    
	    Object k;
	    if (e.hash == hash && ((k = e.getKey()) == key || 
				   (k != null && k.equals(key)))) {
		return true;
	    }
	}
	return false;

    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(Object value) {
	// short circuit for empty maps
	if (size() == 0) 
	    return false;

	for (V v : values()) {
	    if (v == value || (v != null && v.equals(value)))
		return true;
	}
	return false;
    }

    /**
     * Merges the children nodes into this node and removes
     * them.
     */
    private void merge() {	   
	DataManager dataManager = AppContext.getDataManager();
	if (parent == null) // this node is the root!
	    return; // do not merge

	PrefixHashMap<K,V> leftChild_ = leftChild.get(PrefixHashMap.class);
	PrefixHashMap<K,V> rightChild_ = 
	    rightChild.get(PrefixHashMap.class);
	    
	// check that we are merging two leaf nodes
	if (leftChild_.leftChild != null ||
	    rightChild_.leftChild != null) 
	    // either one has children, so do not perform the
	    // merge
	    return;

	// check that their comibined sizes is less than half of the
	// split threshold, to ensure we don't split soon after the
	// merge takes place
	if ((leftChild_.size + rightChild_.size) / 2 > splitThreshold) {
	    return;
	}
	    
	dataManager.markForUpdate(this);

	// recreate our table, as it was made null in split()
	table = new PrefixEntry[leftChild_.table.length];

	// iterate over each child's table, combining the entries into
	// this node's table.
	for (int i = 0; i < table.length; ++i) {

 	    for (PrefixEntry<K,V> e = leftChild_.table[i]; e != null; e = e.next) {
		e.prefix.shiftRight();
		addEntry(e, i);
	    }

 	    for (PrefixEntry<K,V> e = rightChild_.table[i]; e != null; e = e.next) {
		e.prefix.shiftRight();
		addEntry(e, i);
	    }
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
     * @see #addEntry(int,Object,Object,int,Prefix)
     */
    private void split() {
	    
	if (leftChild != null) {  // can't split an intermediate node!
	    return;
	}

	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	PrefixHashMap<K,V> leftChild_ = 
	    new PrefixHashMap<K,V>(splitFactor);
	PrefixHashMap<K,V> rightChild_ = 
	    new PrefixHashMap<K,V>(splitFactor);

	// iterate over all the entries in this table and assign
	// them to either the right child or left child
	for (int i = 0; i < table.length; ++i) {

	    // go over each entry in the bucket since each might
	    // have a different prefix next
	    for (PrefixEntry<K,V> e = table[i]; e != null; e = e.next) {
		
		e.prefix.shiftLeft(); 		
		((e.prefix.leadingBit() == 1) ? leftChild_ : rightChild_).
		    addEntry(e, i);
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
	leftChild_.rightLeaf = rightChild;
	leftChild_.leftLeaf = leftLeaf;
	leftChild_.parent = dataManager.createReference(this);
	rightChild_.leftLeaf = leftChild;
	rightChild_.rightLeaf = rightLeaf;
	rightChild_.parent = leftChild_.parent;

	// invalidate this node's leaf references
	leftLeaf = null;
	rightLeaf = null;
    }

    /**
     * Locates the leaf node that is associated with the provided
     * prefix.  Upon return, the provided prefix will have been
     * shifted left according to the depth of the leaf.
     *
     * @param prefix the initial prefix for which to search 
     *
     * @return the leaf table responsible for storing all entries with
     *         the specified prefix
     */
    private PrefixHashMap<K,V> lookup(Prefix prefix) {
	// a leading 1 indicates the left child prefix
	PrefixHashMap<K,V> leaf;
	for (leaf = this; leaf.leftChild != null; 
	     leaf = (prefix.leadingBit() == 1)
		 ? leaf.leftChild.get(PrefixHashMap.class)
		 : leaf.rightChild.get(PrefixHashMap.class))
	     prefix.shiftLeft();
	return leaf;
    }

    public V get(Object key) {

 	int hash = (key == null) ? 0 : key.hashCode();
 	Prefix prefix = new Prefix(hash);
	PrefixHashMap<K,V> leaf = lookup(prefix);
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

    static int hash(int h) {
	// This function ensures that hashCodes that differ only
	// by constant multiples at each bit position have a
	// bounded number of collisions (approximately 8 at
	// default load factor).
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

	int hash = (key == null) ? 0 : key.hashCode();
	Prefix prefix = new Prefix(hash);
	PrefixHashMap<K,V> leaf = lookup(prefix);
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
	leaf.addEntry(hash, key, value, i, prefix);

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
     * @param prefix the value of the prefix at the time the subtable
     *        was identified.
     */
    private void addEntry(int hash, K key, V value, int index, Prefix prefix) {
	PrefixEntry<K,V> prev = table[index];
	table[index] = new PrefixEntry<K,V>(hash, key, value, prev, prefix);

	// ensure that the prefix has enough precision to support
	// another split operation	    
	if ((size++) >= splitThreshold && !prefix.isAtMaximum())
	    split();
    }
    
    /**
     * Copies the values of the specified entry, excluding the {@code
     * PrefixEntry#next} reference, and adds to the the current leaf,
     * but does not perform the size check for splitting.  This should
     * only be called from {@link #split()} or {@link #merge()} when
     * adding children entries.
     *
     * @param copy the entry whose fields should be copied and added
     *        as a new entry to this leaf.
     * @param index the index where the new entry should be put
     */
    private void addEntry(PrefixEntry copy, int index) {
 	PrefixEntry<K,V> prev = table[index];
	table[index] = new PrefixEntry<K,V>(copy, prev); 
 	size++;
    }
    
    /**
     * Returns whether this map has no mappings.  This implemenation
     * runs in {@code O(1)} time.
     */
    public boolean isEmpty() {
	return leftChild != null || size == 0;
    }
     
     /**
     *  Returns the size of the tree.  Note that this implementation
     *  runs in {@code O(n + n*log(n))} time, where {@code n} is the
     *  number of nodes in the tree (<i>not</i> the number of elements).
     *
     * @return the size of the tree
     */
    public int size() {

 	if (leftChild == null) // leaf node, short-circuit case
 	    return size;

 	int totalSize = 0;
 	PrefixHashMap cur = leftMost();
  	totalSize += cur.size;
  	while(cur.rightLeaf != null)
	    totalSize += (cur = cur.rightLeaf.get(PrefixHashMap.class)).size;
	
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
	int hash = (key == null) ? 0x0 : key.hashCode();
	Prefix prefix = new Prefix(hash);

	PrefixHashMap<K,V> leaf = lookup(prefix);

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
		if ((leaf.size--) < mergeThreshold && leaf.parent != null) {
		    PrefixHashMap parent_ = leaf.parent.get(PrefixHashMap.class);
		    parent_.merge();
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
	return (leftChild == null)
	    ? this : (leftChild.get(PrefixHashMap.class)).leftMost();
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

    public String treeString() {
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
	    return s + ")";
	}
	else {
	    PrefixHashMap l = leftChild.get(PrefixHashMap.class);
	    PrefixHashMap r = rightChild.get(PrefixHashMap.class);
	    return "(" + l.treeString() + ", " + r.treeString() + ")";
	}
    }



    /**
     * A utility class for keeping track of the full prefix for all of
     * the PrefixEntry objects as they are split merged;
     *
     * This class is necessary to maintain all of the prefix bits.
     * During successive {@link PrefixHashMap#merge()} and {@code
     * PrefixHashMap#split()} operations, shifting just the prefix
     * bits would cause the higher order bits to become
     * permanently lost, which could cause ill-formed distribution
     * of the keys.
     *
     * Note that it is up to the user to ensure that the bits are not
     * shifted past their resolution, or incorrectly shifted.  The
     * {@link Prefix#isAtMaximum()} function allows developers to
     * decide whether any further shift is possible.  Furthermore,
     * developers should ensure that no right shift occurs before a
     * left shift tables occurs, as this would override the current
     * prefix with incorrect values from the buffer.
     */
    private static class Prefix implements Serializable {

	private static final long serialVersionUID = 1;
	
	/**
	 * The former leading bits that have been shifted left, off of
	 * the original prefix
	 */
	private int buffer;
	    
	/**
	 * The current prefix value
	 */
	private int prefix;
	    
	/**
	 * The current offset of the original highest order bit.  A
	 * positive value indicates that the original higest order bit
	 * is current in the buffered bits.
	 */
	private byte shift;
	    
	/**
	 * Constructs a {@code Prefix} with the provided starting
	 * value.
	 *
	 * @param prefix the prefix value
	 */
	public Prefix(int prefix) {
	    this.prefix = prefix;
	    this.buffer = 0x0;
	    this.shift = 0;
	}
	    
	/**
	 * Shifts the prefix right and then shifts the lowest
	 * ordered bit from the buffered bits onto the highest
	 * ordered bit of the prefix.
	 */
	public void shiftRight() {
	    // find the lowest order bit in the buffered bits,
	    // then prepend it to the prefix
	    prefix = (prefix >>> 1) | ((buffer & 0x1) << 31);
	    buffer >>>= 1;
	    shift--;
	}

	/**
	 * Shifts the highest order bit off the prefix value onto the
	 * the buffered bits
	 */
	public void shiftLeft() {
	    buffer = (buffer << 1) | ((prefix & 0x80000000) >>> 31);
	    prefix <<= 1;
	    shift++;
	}

	/**
	 * Returns the current value of the prefix
	 */
	public int prefix() {
	    return prefix;
	}

	/**
	 * Returns the value of the highest-order bit on the prefix
	 */
	public byte leadingBit() {
	    return (byte)((prefix & 0x80000000) >>> 31);
	}

	/**
	 * Returns whether this prefix has been shifted to its maximum
	 * precision.
	 */
	public boolean isAtMaximum() {
	    return shift == 31;
	}

	/**
	 * Returns a binary represntation of this prefix
	 */
	public String toString() {
	    return Integer.toBinaryString(prefix);
	}

    }

    /**
     * An implementation of {@code Map.Entry} that incorporates
     * information about the prefix at which it is stored, as well as
     * whether the {@link PrefixHashMap} is responsible for the
     * persistent lifetime of the value.
     *
     * If an object that does not implement {@link ManagedObject} is
     * stored in the map, then it is wrapped using the {@link
     * ManagedWrapper} utility class so that the entry may have a
     * {@code ManagedReference} to the value, rather than a Java
     * reference.  This causes accesses to the entries to only
     * deserialize the keys.
     *
     * @see ManagedWrapper
     */	
    static class PrefixEntry<K,V> implements Map.Entry<K,V>, Serializable {

	private static final long serialVersionUID = 1;
	    
	/**
	 * The a reference to key for this entry. The class type of
	 * this reference will depend on whether the map is managing
	 * the key
	 */	
	private final ManagedReference keyRef;

	/**
	 * A reference to the value.  The class type of this reference
	 * will depend on whether this map is managing the value
	 */ 
	private ManagedReference valueRef;

	/**
	 * The next chained entry in this entry's bucket
	 */
	PrefixEntry<K,V> next;

	/**
	 * The hash value for this entry
	 */
	final int hash;

	/**
	 * The current prefix for where this entry is stored.
	 */
	Prefix prefix;


	/**
	 * Whether the key stored in this entry is actually stored
	 * as a {@link ManagedWrapper}
	 */
	boolean isKeyWrapped;

	/**
	 * Whether the value stored in this entry is actually stored
	 * as a {@link ManagedWrapper}
	 */
	boolean isValueWrapped;

	/**
	 * Constructs this {@code PrefixEntry}
	 *
	 * @param h the hash code for the key
	 * @param k the key
	 * @param v the value
	 * @param next the next {@link PrefixEntry} in this bucked
	 * @param prefix the prefix value for when the entry was
	 *        originally created
	 */
	PrefixEntry(int h, K k, V v, PrefixEntry<K,V> next, Prefix prefix) {

	    if (k instanceof ManagedObject) {
		// if k is already a ManagedObject, then put it in the
		// datastore
		keyRef = AppContext.getDataManager().
		    createReference((ManagedObject)k);
		isKeyWrapped = false;
	    }
	    else {
		// otherwise, we need to wrap it in a ManagedObject
		keyRef = AppContext.getDataManager().
		    createReference(new ManagedWrapper<K>(k));
		isKeyWrapped = true;
	    }

	    if (v instanceof ManagedObject) {
		// if v is already a ManagedObject, then put it in the
		// datastore
		valueRef = AppContext.getDataManager().
		    createReference((ManagedObject)v);
		isValueWrapped = false;
	    }
	    else {
		// otherwise, we need to wrap it in a ManagedObject
		valueRef = AppContext.getDataManager().
		    createReference(new ManagedWrapper<V>(v));
		isValueWrapped = true;
	    }

	    this.next = next;
	    this.hash = h;
	    this.prefix = prefix;
	}

	PrefixEntry(PrefixEntry<K,V> clone, PrefixEntry<K,V> next) {
	    this.hash = clone.hash;
	    this.keyRef = clone.keyRef;
	    this.next = next;
	    this.prefix = clone.prefix;
	    this.valueRef = clone.valueRef;
	    this.isValueWrapped = clone.isValueWrapped;
	    this.isKeyWrapped = clone.isKeyWrapped;
	}
  
	/**
	 * {@inheritDoc}
	 */
	public final K getKey() {
	    return (isKeyWrapped)
		? ((ManagedWrapper<K>)(keyRef.get(ManagedWrapper.class))).object
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
	    return (isValueWrapped) 
		? ((ManagedWrapper<V>)(valueRef.get(ManagedWrapper.class))).object
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
	    if (isValueWrapped) {
		// unpack the value from the wrapper prior to
		// returning it
		ManagedWrapper<V> wrapper = valueRef.get(ManagedWrapper.class);
		oldValue = wrapper.object;
		AppContext.getDataManager().removeObject(wrapper);
	    }
	    else {
		oldValue = (V)(valueRef.get(Object.class));
	    } 

	    if (newValue instanceof ManagedObject) {
		// if v is already a ManagedObject, then do not put it
		// in the datastore, and instead get a reference to it
		valueRef = AppContext.getDataManager().
		    createReference((ManagedObject)newValue);
		isValueWrapped = false;
	    }
	    else {
		// otherwise, we need to wrap it in a ManagedObject
		valueRef = AppContext.getDataManager().
		    createReference(new ManagedWrapper<V>(newValue));
		isValueWrapped = true;
	    }
	    return oldValue;
	}

	/**
	 * {@inheritdoc}
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
	 * {@inheritdoc}
	 */
	public final int hashCode() {
	    return (keyRef==null   ? 0 : keyRef.hashCode()) ^
		(valueRef==null ? 0 : valueRef.hashCode());
	}
	
	/**
	 * Returns the string form of this entry as [{@code entry},
	 * {@code value}]-&gt;<i>next</i>.
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
	    if (isKeyWrapped) {
		// unpack the key from the wrapper 
		ManagedWrapper<V> wrapper = keyRef.get(ManagedWrapper.class);
		AppContext.getDataManager().removeObject(wrapper);
	    }
	    if (isValueWrapped) {
		// unpack the value from the wrapper 
		ManagedWrapper<V> wrapper = valueRef.get(ManagedWrapper.class);
		AppContext.getDataManager().removeObject(wrapper);
	    }
	}
    }


    /**
     *
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
	 * {@inheritdoc}
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
	 * {@inheritdoc}
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
	 * {@inheritdoc}
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
	 * {@inheritdoc}
	 */
	public V next() {
	    return nextEntry().getValue();
	}
    }


    public Set<Entry<K,V>> entrySet() {
	return new EntrySet(this);
    }

    private final class EntrySet extends AbstractSet<Entry<K,V>> {

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
	    return root.containsKey(o);
	}

	public void clear() {
	    root.clear();
	}
	
    }

    public Set<K> keySet() {
	return new KeySet(this);
    }
        
    private final class KeySet extends AbstractSet<K> {
	    
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
	
    public Collection<V> values() {
	return new Values(this);
    }
    
    private final class Values extends AbstractCollection<V> {

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
     * A wrapper and marker class for holding {@code Serializable}
     * objects for which this map is responsible.  When a value is put
     * into the map that is not a {@link ManagedObject}, the value is
     * stored in the data store by means of this class.  Upon its
     * removal, this object is removed from the data store.
     *
     * @see PrefixEntry
     */
    private static class ManagedWrapper<T> 
	implements ManagedObject, Serializable {
	
	private static final long serialVersionUID = 1;
	
	/**
	 * The serializable object being wrapped by this instance
	 */
	public final T object;
	
	/**
	 * Constructs a managed wrapper around this object
	 *
	 * @param object the object to be stored in the datastore
	 */
	public ManagedWrapper(T object) {
	    this.object = object;
	}
    }
}
