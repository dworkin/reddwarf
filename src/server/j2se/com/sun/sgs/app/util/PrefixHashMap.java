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

    // REMINDER: remove me
    private static final int MERGE_THRESHOLD = 16;

    /**
     * The granuality in bytes of the lock size in the persistence
     * mechanism.  Application developers who want to tune this data
     * structure should take this into account when deciding how many
     * elements a leaf table should contain before splitting.
     */
    public static final int PERSISTENCE_LOCK_SIZE = 1 << 13;

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 1.0f;    
    
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
     * @see {@link #split()}
     */
    private int threshold;

    /**
     * The number of {@code PrefixEntry} at leaf node.
     */
    private final int leafCapacity;

    /**
     * The fraction of the leaf capacity that will cause the leaf to
     * split.
     */
    private final float loadFactor;

    /** 
     * Constructs an empty {@code PrefixHashMap} with the specified
     * load factor and specified leaf capacity.  This constructor
     * should be used with reservation, only for advance tuning where
     * the memory overhead of the key set has been determined.  For
     * optimal performace, all of the key sould fit within the memory
     * granularity of the peristence mechanism's lock.  This size is
     * exposed to the developer as {@link #PERSISTENCE_LOCK_SIZE}.
     *
     * @param loadFactor the fraction of the leaf capacity which will
     *        cause the leaf to split
     * @param leafCapacity the size of each of the leaf tables
     *
     * @throws IllegalArgumentException if the load factor is non positive
     * @throws IllegalArgumentException if the leaf capacity is non positive
     *
     * @see #PERSISTENCE_LOCK_SIZE
     */
    public PrefixHashMap(float loadFactor, int leafCapacity) {
	if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
	    throw new IllegalArgumentException("Illeal load factor: " + 
					      loadFactor);
	}
	if (leafCapacity <= 0) {
	    throw new IllegalArgumentException("Illeal leaf capacity: " + 
					      leafCapacity);	    
	}
	table = new PrefixEntry[leafCapacity];
	size = 0;
	parent = null;
	leftLeaf = null;
	rightLeaf = null;
	leftChild = null;
	rightChild = null;
	this.leafCapacity = leafCapacity;
	this.loadFactor = loadFactor;
	this.threshold = (int)(loadFactor * leafCapacity);
    }
    
    /** 
     * Constructs an empty {@code PrefixHashMap} with the specified load
     * factor and default leaf capacity (512).
     *
     * @param loadFactor the fraction of the leaf capacity which will
     *        cause the leaf to split
     *
     * @throws IllegalArgumentException if the load factor is non positive
     */
    public PrefixHashMap(float loadFactor) {
	this(loadFactor, DEFAULT_LEAF_CAPACITY);
    }

    /** 
     * Constructs an empty {@code PrefixHashMap} with the default load
     * factor (1.0) and default leaf capacity (512).
     */
    public PrefixHashMap() {
	this(DEFAULT_LOAD_FACTOR, DEFAULT_LEAF_CAPACITY);
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
		    // if we wrapped the value, we can free it directly
		    if (e.isValueWrapped) {
			dm.removeObject(e.value.get(ManagedWrapper.class));
		    }
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

    public boolean containsKey(Object key) {
	int h = key.hashCode();
	return containsKey(key, h, h);
    }

    private boolean containsKey(Object key, int hash, int prefix) {
	if (leftChild == null) { // is leaf 
	    for (PrefixEntry e = table[indexFor(hash, table.length)]; 
		 e != null; 
		 e = e.next) {
		
		Object k;
		if (e.hash == hash && ((k = e.key) == key || 
				       (k != null && k.equals(key)))) {
		    return true;
		}
	    }
	    return false;
	}
	else {
	    // a leading 1 indicates the left child prefix
	    PrefixHashMap child = ((prefix & 0x80000000) == 0x80000000) 
		? leftChild.get(PrefixHashMap.class)
		: rightChild.get(PrefixHashMap.class);
		
	    return child.containsKey(key, hash, prefix << 1);		    
	}

    }

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
	if ((leftChild_.size + rightChild_.size) / 2 > threshold) {
//  	    System.out.printf("Comined size (%d/%d) would be too big, not "
//  			      + " performing merge()\n",
//  			      (leftChild_.size + rightChild_.size),
// 			      threshold);
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
		addEntry(e.hash, e.key, e.value, i, e.prefix, 
			 e.isValueWrapped);
	    }

 	    for (PrefixEntry<K,V> e = rightChild_.table[i]; e != null; e = e.next) {
		e.prefix.shiftRight();
		addEntry(e.hash, e.key, e.value, i, e.prefix, 
			 e.isValueWrapped);
	    }

// 	    // add all entries from the left child first
// 	    for (PrefixEntry e = leftChild_.table[i]; e != null; e = e.next) {
// 		e.prefix.shiftRight();
// 		size++;
// 	    }
// 	    table[i] = leftChild_.table[i];

// 	    // keep track of the last entry for the right child's
// 	    // bucket so that the previous entries can be chained on
// 	    // from here
// 	    PrefixEntry last = null;
// 	    for (PrefixEntry e = rightChild_.table[i]; e != null; e = e.next) {
// 		e.prefix.shiftRight();
// 		last = e;
// 		size++;
// 	    }

// 	    // if the right child had entries, update the last entry
// 	    // to point to the old start
// 	    if (last != null) 
// 		last.next = table[i];    
// 	    table[i] = rightChild_.table[i];
	}

//  	System.out.printf("merged %d from the left and %d from the right to"
//  			  + " %d entries\n",
//  			  leftChild_.size, rightChild_.size, size);

	// update the remaining family references
	leftLeaf = leftChild_.leftLeaf;
	rightLeaf = rightChild_.rightLeaf;

// 	System.out.printf("left leaf: %s, right leaf: %s\n",
// 			  leftLeaf, rightLeaf);
	
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

    private void split() {
	    
	DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

	PrefixHashMap<K,V> leftChild_ = 
	    new PrefixHashMap<K,V>(loadFactor, table.length);
	PrefixHashMap<K,V> rightChild_ = 
	    new PrefixHashMap<K,V>(loadFactor, table.length);

	// iterate over all the entries in this table and assign
	// them to either the right child or left child
	for (int i = 0; i < table.length; ++i) {

	    // go over each entry in the bucket since each might
	    // have a different prefix next
	    for (PrefixEntry<K,V> e = table[i]; e != null;) {
		
		// cache this value at the start to keep a reference
		// to it after we reassign the next pointer when
		// storing in a child
		PrefixEntry<K,V> next = e.next;
		
		if (e.prefix.leadingBit() == 1) { // 1 == left
		    PrefixEntry<K,V> prev = leftChild_.table[i];
		    leftChild_.table[i] = e;
		    e.next = prev;
		    leftChild_.size++;
		}
		else {
		    PrefixEntry<K,V> prev = rightChild_.table[i];
		    rightChild_.table[i] = e;
		    e.next = prev;
		    rightChild_.size++;
		}

		// shift the prefix down, for the next time
		e.prefix.shiftLeft(); 

		table[i] = null; // null out our entry
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
	leftChild_.rightLeaf = rightChild;
	leftChild_.leftLeaf = leftLeaf;
	leftChild_.parent = dataManager.createReference(this);
	rightChild_.leftLeaf = leftChild;
	rightChild_.rightLeaf = rightLeaf;
	rightChild_.parent = leftChild_.parent;

	leftLeaf = null;
	rightLeaf = null;
    }

    public V get(Object key) {
	if (key == null)
	    return getForNullKey();
	int h = key.hashCode();
	return get(key, h, new Prefix(h));
    }

    // NOTE: we can use the integer prefix here (rather than the
    //       class Prefix), because we don't need need to store
    //       the bits in a PrefixEntry
    private V get(Object key, int hash, Prefix prefix) {
	if (leftChild == null) { // is leaf 
	    for (PrefixEntry<K,V> e = table[indexFor(hash, table.length)]; 
		 e != null; 
		 e = e.next) {
		
		Object k;
		if (e.hash == hash && 
		    ((k = e.key) == key || (k != null && k.equals(key)))) {
		    return e.getValue();
		}
	    }
	    return null;
	}
	else {
	    // a leading 1 indicates the left child prefix
	    PrefixHashMap<K,V> child = (prefix.leadingBit() == 1)
		? leftChild.get(PrefixHashMap.class)
		: rightChild.get(PrefixHashMap.class);
	    prefix.shiftLeft();
	    return child.get(key, hash, prefix);		    
	}
    }

    /**
     * An off-loaded version of {@link get(Object)} for the {@code
     * null} key, specifically added for improving the performance of
     * the common operation by removing the rare case.
     *
     * @return the value mapped to the {@code null} key, if any
     */
    private V getForNullKey() {
	PrefixHashMap<K,V> leftMost = leftMost();
	for (PrefixEntry<K,V> e = leftMost.table[0]; e != null; e = e.next) {
	    if (e.key == null)
		return e.getValue();
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
	if (key == null)
	    return putForNullKey(value);
	int h = key.hashCode();
	return put(key, value, h, new Prefix(h));
    }

    /**
     * Recursively searches the tree for the specified key using the
     * provided prefix, and then returns the value.  This
     * implementation uses the hash code as the initial prefix.  At
     * each recursive call, the prefix is shifted to identify at which
     * subtree the key may be stored
     *
     * @param key the key
     * @param value the value to be mapped to the key
     * @param hash the hash code of the key
     * @param prefix the current prefix at the time of a recursive call.
     * @return the previous value mapped to the provided key, if any
     */
    private V put(K key, V value, int hash, Prefix prefix) {
	// find the subtable with the appropriate prefix
	if (leftChild == null) { // is leaf
	    int i = indexFor(hash, table.length);
	    for (PrefixEntry<K,V> e = table[i]; e != null; e = e.next) {
		
		Object k;
		if (e.hash == hash && 
		    ((k = e.key) == key || (k != null && k.equals(key)))) {
		    
		    // if the keys and hash match, swap the values
		    // and return the old value
		    return e.setValue(value);
		}
	    }
	    
	    // we found no key match, so add an entry
	    addEntry(hash, key, value, i, prefix);
	    
	    return null;
	}
	else {
	    PrefixHashMap<K,V> child = (prefix.leadingBit() == 1) 
		? leftChild.get(PrefixHashMap.class)
		: rightChild.get(PrefixHashMap.class);
		
	    prefix.shiftLeft();
	    return child.put(key, value, hash, prefix);
	}
    }

    /**
     * An offloaded version of {@code put} that specifically deals
     * with updating the {@code null} key.
     */
    private V putForNullKey(V value) {
	PrefixHashMap<K,V> leftMost = leftMost();
	for (PrefixEntry<K,V> e = leftMost.table[0]; e != null; e = e.next) {
	    if (e.key == null)
		return e.setValue(value);
	}
	leftMost.addEntry(0, null, value, 0, new Prefix(0));
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
    private void addEntry(int hash, K key, V value, int index, 
			  Prefix prefix) {
	PrefixEntry<K,V> prev = table[index];
//  	System.out.printf("Added entry for <%s,%s> \tat index %d," +
//  			  "\twith prefix %32s, \tnext: %s\n",
//  			  key, value, index, prefix, prev);
	
	table[index] = new PrefixEntry<K,V>(hash, key, value, prev, prefix);

	// ensure that the prefix has enough precision to support
	// another split operation	    
	if (size++ >= threshold && !prefix.isAtMaximum())
	    split();
    }
    
    /**
     * Adds a new entry but does not perform the size check for
     * splitting.  This should only be called from {@link #merge()}
     * when adding children entries.
     */
    private void addEntry(int hash, K key, ManagedReference ref,
			  int index, Prefix prefix, boolean isValueWrapped) {
 	PrefixEntry<K,V> prev = table[index];
 	table[index] = new PrefixEntry<K,V>(hash, key, ref, prev, prefix, 
					    isValueWrapped);
 	size++;
    }
    
    /**
     * {@inheritdoc}
     */
    public boolean isEmpty() {
	return (leftChild == null) ? size == 0 : size() == 0;
    }
     
     /**
     *  Returns the size of the tree in {@code n + log(n)} time.
     *
     * @return the size of the tree
     */
    public int size() {
// 	if (leftChild == null)
// 	    return size;

// 	int totalSize = 0;
// 	PrefixHashMap m = leftMost();
//  	totalSize += m.size;
//  	for (; m.rightLeaf != null; m = m.rightLeaf.get(PrefixHashMap.class)) {
//  	    totalSize += m.size;
// 	}
//  	return totalSize;
	

 	if (leftChild == null) { // leaf node, short-circuit case
 	    return size;
 	}
 	else {
 	    // iterate over the keyset, which is faster than querying
 	    // all the children
 	    int totalSize = 0;
 	    for (K k : keySet())
 		totalSize++;
 	    return totalSize;
 	}
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
	int h = (key == null) ? 0x0 : key.hashCode();
	return remove(key, h, h);
    }

    /**
     * Recursively traverses the tree based on the prefix, then
     * removes the mapping for the specified key from the final leaf
     * table with the desired prefix if the mapping is present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    private V remove(Object key, int hash, int prefix) {
	if (leftChild == null) { // leaf node
	    int i = indexFor(hash, table.length);
	    PrefixEntry<K,V> e = table[i]; 
	    PrefixEntry<K,V> prev = e;
	    while (e != null) {
		PrefixEntry<K,V> next = e.next;
		Object k;
		if (e.hash == hash && 
		    ((k = e.key) == key || (k != null && k.equals(key)))) {
			
		    // remove the value and reorder the chained keys
		    if (e == prev) // if this was the first element
			table[i] = next;
		    else 
			prev.next = next;

		    // mark that this table's state has changed
		    AppContext.getDataManager().markForUpdate(this);
		    
		    V v = e.getValue();
		    
		    // see whether this data structure is responsible for the
		    // persistence lifetime of the value
		    if (e.isValueWrapped) {
			AppContext.getDataManager().
			    removeObject(e.value.get(ManagedWrapper.class));
		    }	
		    
// 		    System.out.printf("size: %s, subtree: %s\n", size-1,
// 				      treeString());

		    // lastly, if the leaf size is less than the size
		    // threshold, attempt a merge
		    if (--size < MERGE_THRESHOLD && parent != null) {
			PrefixHashMap parent_ = parent.get(PrefixHashMap.class);
  			parent_.merge();
		    }
		    
		    return v;
		    
		}		
		prev = e;
		e = e.next;
	    }
	    return null;
	}
	else {
	    // leading bit of 1 -> left child
	    PrefixHashMap<K,V> child = ((prefix & 0x80000000) == 0x80000000)
		? leftChild.get(PrefixHashMap.class)
		: rightChild.get(PrefixHashMap.class);
		
	    prefix <<= 1; // shift the leading bit
	    return child.remove(key, hash, prefix);		
	}
    }

//     /**
//      * Returns the value contained by this entry and removes the value
//      * from the data store if it was managed by this map.
//      *
//      * @param e the entry to be removed
//      * @return the value contained by the entry
//      */
//     private V removeEntry(PrefixEntry<K,V> e) {

// 	V v = e.getValue();

// 	// see whether this data structure is responsible for the
// 	// persistence lifetime of the value
// 	if (e.isValueWrapped) {
// 	    AppContext.getDataManager().
// 		removeObject(e.value.get(ManagedWrapper.class));
// 	}	
	    
// 	// if the local size is less than the size
// 	// threshold, attempt a merge
// 	if (--size < MERGE_THRESHOLD && parent != null) {
//  	    PrefixHashMap parent_ = parent.get(PrefixHashMap.class);
//   	    parent_.merge();
// 	}

// 	return v;
//     }

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
		if (e != null)
		    s += e;
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
	 * The key for this entry
	 */	
	final K key;

	/**
	 * A reference to the value.  The type of this reference will
	 * depend on whether this map is managing the object or not
	 */ 
	ManagedReference value;

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

	    if (v instanceof ManagedObject) {
		// if v is already a ManagedObject, then put it in the
		// datastore
		value = AppContext.getDataManager().
		    createReference((ManagedObject)v);
		isValueWrapped = false;
	    }
	    else {
		// otherwise, we need to wrap it in a ManagedObject
		value = AppContext.getDataManager().
		    createReference(new ManagedWrapper<V>(v));
		isValueWrapped = true;
	    }
	    this.next = next;
	    this.key = k;
	    this.hash = h;
	    this.prefix = prefix;
	}

 	PrefixEntry(int h, K k, ManagedReference ref, PrefixEntry<K,V> next,
 		    Prefix prefix, boolean isValueWrapped) {
	    this.hash = h;
	    this.key = k;
	    this.next = next;
	    this.prefix = prefix;
	    this.value = ref;
	    this.isValueWrapped = isValueWrapped;
 	}
	    
	/**
	 * {@inheritDoc}
	 */
	public final K getKey() {
	    return key;
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
		? ((ManagedWrapper<V>)(value.get(ManagedWrapper.class))).object
		: (V)(value.get(Object.class));
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
		ManagedWrapper<V> wrapper = value.get(ManagedWrapper.class);
		oldValue = wrapper.object;
		AppContext.getDataManager().removeObject(wrapper);
	    }
	    else {
		oldValue = (V)(value.get(Object.class));
	    } 

	    if (newValue instanceof ManagedObject) {
		// if v is already a ManagedObject, then do not put it
		// in the datastore, and instead get a reference to it
		value = AppContext.getDataManager().
		    createReference((ManagedObject)newValue);
		isValueWrapped = false;
	    }
	    else {
		// otherwise, we need to wrap it in a ManagedObject
		value = AppContext.getDataManager().
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
	    return (key==null   ? 0 : key.hashCode()) ^
		(value==null ? 0 : value.hashCode());
	}
	
	/**
	 * Returns the string form of this entry as [{@code entry},
	 * {@code value}]-&gt;<i>next</i>.
	 */
	public String toString() {
	    return "[" + key + "," + getValue() + "]->" + next;
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
