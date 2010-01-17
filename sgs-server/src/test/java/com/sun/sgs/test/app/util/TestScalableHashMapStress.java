/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ScalableHashMap;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A stress test for the {@link ScalableHashMap} class.
 */
@IntegrationTest
@RunWith(FilteredNameRunner.class)
public class TestScalableHashMapStress extends Assert {

    /**
     * The seed for the random number generator.  This number depends on the
     * current time by default, but should be set explicitly to repeat a
     * previous test run.
     */
    private static final long seed = Long.getLong(
	"test.seed", new Date().getTime());

    /** Whether to print debugging information. */
    static final boolean debug = Boolean.getBoolean("test.debug");

    /** The maximum number of entries to store in the map. */
    private static final int maxEntries = Integer.getInteger(
	"test.entries", 2000);

    /** The number of operations to perform. */
    private static final int operations = Integer.getInteger(
	"test.operations", 20000);

    /**
     * The number of objects that should share each hash code, to test hash
     * collisions.
     */
    private static final int collisions = Integer.getInteger(
	"test.collisions", 3);

    /** The minimum number of operations per transaction. */
    private static final int minOpsPerTxn = 2;

    /** The maximum number of operations per transaction. */
    private static final int maxOpsPerTxn = 10;

    /** The random number generator that drives the test. */
    static final Random random = new UndoableRandom(seed);

    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    /** A list of the operations to perform. */
    final List<Op> ops = new ArrayList<Op>();

    /** A set that records the entries that should appear in the map. */
    final UndoableBitSet control = new UndoableBitSet(maxEntries);

    /** The number of objects before creating the map. */
    private int initialObjectCount;

    /** The map under test. */
    ScalableHashMap<Key, Value> map;

    /** An iterator over the keys of the map. */
    ManagedSerializable<Iterator<Key>> msKeys;

    /** The entries already seen by the keys iterator. */
    final UndoableBitSet keysSeen = new UndoableBitSet(maxEntries);

    /** The current entry of the keys iterator or -1. */
    int currentKey = -1;

    /** An iterator over the values of the map. */
    ManagedSerializable<Iterator<Value>> msValues;

    /** The entries already seen by the values iterator. */
    final UndoableBitSet valuesSeen = new UndoableBitSet(maxEntries);

    /** The current entry of the values iterator or -1. */
    int currentValue = -1;

    /** An iterator over the entries of the map. */
    ManagedSerializable<Iterator<Entry<Key, Value>>> msEntries;

    /** The entries already seen by the entries iterator. */
    final UndoableBitSet entriesSeen = new UndoableBitSet(maxEntries);

    /** The current entry of the entries iterator or -1. */
    int currentEntry = -1;

    /** A serializable int to store in the map. */
    private static class Int implements Serializable {
	private static final long serialVersionUID = 1;
	final int i;
	Int(int i) {
	    this.i = i;
	}
	public boolean equals(Object object) {
	    return object instanceof Int && i == ((Int) object).i;
	}
	public int hashCode() {
	    return hash(i);
	}
	public String toString() {
	    String classname = getClass().getName();
	    int dot = classname.lastIndexOf('.');
	    if (dot > 0) {
		classname = classname.substring(dot + 1);
	    }
	    return classname + "[" + i + "]";
	}
    }

    /** A serializable key to store in the map. */
    private static class Key extends Int {
	private static final long serialVersionUID = 1;
	static Key create(int i) {
	    return random.nextBoolean() ? new Key(i) : new ManagedKey(i);
	}
	Key(int i) {
	    super(i);
	}
	public boolean equals(Object object) {
	    return object instanceof Key && super.equals(object);
	}
    }

    /** A managed key to store in the map. */
    private static class ManagedKey extends Key implements ManagedObject {
	private static final long serialVersionUID = 1;
	ManagedKey(int i) {
	    super(i);
	}
    }

    /**
     * A serializable value to store in the map.  Maintains a reference to the
     * associated key, if the key is a managed object, so the test can remove
     * the key object.
     */
    private static class Value extends Int {
	private static final long serialVersionUID = 1;
	private ManagedReference<ManagedKey> key;
	static Value create(int i) {
	    return random.nextBoolean() ? new Value(i) : new ManagedValue(i);
	}
	static Value create(int i, Key key) {
	    return random.nextBoolean()
		? new Value(i, key) : new ManagedValue(i, key);
	}
	Value(int i) {
	    this(i, null);
	}
	Value(int i, Key key) {
	    super(i);
	    setKey(key);
	}
	Key getKey() {
	    return (key == null) ? null : key.get();
	}
	void setKey(Key key) {
	    this.key = (key instanceof ManagedKey)
		? AppContext.getDataManager().createReference((ManagedKey) key)
		: null;
	}
	public boolean equals(Object object) {
	    return object instanceof Value && super.equals(object);
	}
    }

    /** A managed value to store in the map. */
    private static class ManagedValue extends Value implements ManagedObject {
	private static final long serialVersionUID = 1;
	ManagedValue(int i) {
	    super(i);
	}
	ManagedValue(int i, Key key) {
	    super(i, key);
	}
    }

    /** An operation to perform. */
    private abstract class Op implements Runnable {
	Op(int count) {
	    for (int i = 0; i < count; i++) {
		ops.add(this);
	    }
	}
    }

    /** Operation for Map.get */
    private class Get extends Op {
	Get(int count) {
	    super(count);
	}
	public void run() {
	    int objnum = getRandomObjectNumber();
	    if (debug) {
		System.err.println("get " + objnum);
	    }
	    boolean present = control.get(objnum);
	    Key key = new Key(objnum);
	    assertEquals(present, map.containsKey(key));
	    assertEquals(present ? new Value(objnum) : null, map.get(key));
	}
    }

    /** Operation for Map.put */
    private class Put extends Op {
	Put(int count) {
	    super(count);
	}
	public void run() {
	    int objnum = getRandomObjectNumber();
	    if (debug) {
		System.err.println("put " + objnum);
	    }
	    Key key = Key.create(objnum);
	    Value newValue = Value.create(objnum, key);
	    Value oldValue = map.put(key, newValue);
	    if (control.get(objnum)) {
		assertEquals(new Value(objnum), oldValue);
		newValue.setKey(oldValue.getKey());
		maybeRemoveObject(oldValue);
		maybeRemoveObject(key);
	    } else {
		assertEquals(null, oldValue);
	    }
	    control.set(objnum);
	}
    }

    /** Operation for Map.remove */
    private class Remove extends Op {
	Remove(int count) {
	    super(count);
	}
	public void run() {
	    int objnum = getRandomObjectNumber();
	    if (debug) {
		System.err.println("remove " + objnum);
	    }
	    Value oldValue = map.remove(new Key(objnum));
	    if (control.get(objnum)) {
		assertEquals(new Value(objnum), oldValue);
		maybeRemoveObject(oldValue.getKey());
		keyRemoved(objnum);
		maybeRemoveObject(oldValue);
	    } else {
		assertEquals(null, oldValue);
	    }
	    control.clear(objnum);
	}
    }

    /** Calls next on the key set iterator */
    private class KeySetNext extends Op {
	KeySetNext(int count) {
	    super(count);
	}
	public void run() {
	    boolean startAgain = !msKeys.get().hasNext();
	    if (startAgain) {
                Iterator<Key> keys = map.keySet().iterator();
                dataService.markForUpdate(msKeys);
		msKeys.set(keys);
		keysSeen.clear();
		currentKey = -1;
	    }
	    if (msKeys.get().hasNext()) {
		if (debug && startAgain) {
		    System.err.println("keySet new iterator");
		}
                dataService.markForUpdate(msKeys);
		Key key = msKeys.get().next();
		currentKey = key.i;
		assertFalse("Already seen " + currentKey,
			    keysSeen.get(currentKey));
		keysSeen.set(currentKey);
		run(key);
	    }
	}
	void run(Key key) {
	    if (debug) {
		System.err.println("keySet next " + key.i);
	    }
	    assertTrue(control.get(key.i));
	}
    }

    /** Calls next and remove on the key set iterator */
    private class KeySetNextRemove extends KeySetNext {
	KeySetNextRemove(int count) {
	    super(count);
	}
	void run(Key key) {
	    if (debug) {
		System.err.println("keySet next remove " + key.i);
	    }
	    Value value = map.get(key);
            dataService.markForUpdate(msKeys);
            msKeys.get().remove();
	    assertTrue(control.get(key.i));
	    control.clear(key.i);
	    maybeRemoveObject(key);
	    keyRemoved(key.i);
	    maybeRemoveObject(value);
	}
    }

    /** Calls next on the values iterator */
    private class ValuesNext extends Op {
	ValuesNext(int count) {
	    super(count);
	}
	public void run() {
	    boolean startAgain = !msValues.get().hasNext();
	    if (startAgain) {
		Iterator<Value> values = map.values().iterator();
                dataService.markForUpdate(msValues);
		msValues.set(values);
		valuesSeen.clear();
		currentValue = -1;
	    }
	    if (msValues.get().hasNext()) {
		if (debug && startAgain) {
		    System.err.println("values new interator");
		}
                dataService.markForUpdate(msValues);
		Value value = msValues.get().next();
		currentValue = value.i;
		assertFalse("Already seen " + currentValue,
			    valuesSeen.get(currentValue));
		valuesSeen.set(currentValue);
		run(value);
	    }
	}
	void run(Value value) {
	    if (debug) {
		System.err.println("values next " + value.i);
	    }
	    assertTrue(control.get(value.i));
	}
    }

    /** Calls next and remove on the values iterator */
    private class ValuesNextRemove extends ValuesNext {
	ValuesNextRemove(int count) {
	    super(1);
	}
	void run(Value value) {
	    if (debug) {
		System.err.println("values next remove " + value.i);
	    }
            dataService.markForUpdate(msValues);
	    msValues.get().remove();
	    assertTrue(control.get(value.i));
	    control.clear(value.i);
	    maybeRemoveObject(value.getKey());
	    keyRemoved(value.i);
	    maybeRemoveObject(value);
	}
    }

    /** Calls next on the entry set iterator */
    private class EntrySetNext extends Op {
	EntrySetNext(int count) {
	    super(count);
	}
	public void run() {
	    boolean startAgain = !msEntries.get().hasNext();
	    if (startAgain) {
		Iterator<Entry<Key, Value>> entries = map.entrySet().iterator();
                dataService.markForUpdate(msEntries);
		msEntries.set(entries);
		entriesSeen.clear();
		currentEntry = -1;
	    }
	    if (msEntries.get().hasNext()) {
		if (debug && startAgain) {
		    System.err.println("entrySet new iterator");
		}
                dataService.markForUpdate(msEntries);
		Entry<Key, Value> entry = msEntries.get().next();
		currentEntry = entry.getKey().i;
		assertFalse("Already seen " + currentEntry,
			    entriesSeen.get(currentEntry));
		entriesSeen.set(currentEntry);
		run(entry);
	    }
	}
	void run(Entry<Key, Value> entry) {
	    if (debug) {
		System.err.println("entrySet next " + entry.getKey().i);
	    }
	    Key key = entry.getKey();
	    Value value = entry.getValue();
	    assertTrue(control.get(key.i));
	    assertEquals(key.i, value.i);
	    entry.setValue(Value.create(key.i, key));
	    maybeRemoveObject(value);
	}
    }

    /** Calls next and remove on the entry set iterator */
    private class EntrySetNextRemove extends EntrySetNext {
	EntrySetNextRemove(int count) {
	    super(count);
	}
	void run(Entry<Key, Value> entry) {
	    if (debug) {
		System.err.println("entrySet next remove " +
				   entry.getKey().i);
	    }
	    Key key = entry.getKey();
	    Value value = entry.getValue();
	    assertTrue(control.get(key.i));
	    assertEquals(key.i, value.i);
            dataService.markForUpdate(msEntries);
	    msEntries.get().remove();
	    control.clear(key.i);
	    maybeRemoveObject(key);
	    keyRemoved(key.i);
	    maybeRemoveObject(value);
	}
    }

    /** Setup. */
    @Before public void setUp() throws Exception {
	/* Register the operations. */
	new Get(1);
	/* Do more puts to make up for the different kinds of removes. */
	new Put(4);
	new Remove(1);
	new KeySetNext(1);
	new KeySetNextRemove(1);
	new ValuesNext(1);
	new ValuesNextRemove(1);
	new EntrySetNext(1);
	new EntrySetNextRemove(1);

	serverNode = new SgsTestNode("TestScalableHashMapStress", null, null);
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();

	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    initialObjectCount = getObjectCount();
		    map = new ScalableHashMap<Key, Value>();
		    dataService.setBinding("map", map);
		    Iterator<Key> keys = map.keySet().iterator();
		    dataService.
			setBinding("keys",
				   new ManagedSerializable<Iterator<Key>>
				   (keys));
		    Iterator<Value> values = map.values().iterator();
		    dataService.
			setBinding("values",
				   new ManagedSerializable<Iterator<Value>>
				   (values));
		    Iterator<Entry<Key, Value>> entries =
                            map.entrySet().iterator();
		    dataService.
			setBinding("entries",
				   new ManagedSerializable<Iterator<Entry
				   <Key, Value>>>(entries));
		}
	    }, taskOwner);
    }

    /** Teardown. */
    @After public void tearDown() throws Exception {
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		private int attempts = 0;
		public void run() throws Exception {
		    initTxnState(++attempts);
		    dataService.removeObject(
			dataService.getBinding("entries"));
		    Iterator<Entry<Key, Value>> entries =
                            map.entrySet().iterator();
		    dataService.setBinding("entries",
		        new ManagedSerializable<Iterator<Entry<Key, Value>>>
					   (entries));

		}
	    }, taskOwner);
	final AtomicBoolean isDone = new AtomicBoolean(false);
	while (! isDone.get()) {
	    txnScheduler.runTask(
	        new TestAbstractKernelRunnable() {
		    private int attempts = 0;
		    public void run() throws Exception {
			initTxnState(++attempts);
			int count = 0;
			while (msEntries.get().hasNext()) {
			    if (++count % 50 == 0)
				return;
                            dataService.markForUpdate(msEntries);
			    Entry<Key, Value> entry = msEntries.get().next();
			    maybeRemoveObject(entry.getKey());
			    maybeRemoveObject(entry.getValue());
			}
			isDone.set(true);
		    }
	    }, taskOwner);
	}
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		private int attempts = 0;
		public void run() throws Exception {
		    initTxnState(++attempts);
		    DoneRemoving.init();
		    dataService.removeObject(map);
		    dataService.removeObject(dataService.getBinding("keys"));
		    dataService.removeObject(dataService.getBinding("values"));
		    dataService.removeObject(
			dataService.getBinding("entries"));
		}
	    }, taskOwner);
	DoneRemoving.await(1);
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    assertEquals(initialObjectCount, getObjectCount());
		}
	    }, taskOwner);
        serverNode.shutdown(true);
    }

    /* Tests */

    /** Performs a random stress test on the scalable hash map. */
    @Test public void testStress() throws Exception {
	final long start = System.currentTimeMillis();
	System.err.println("test.entries=" + maxEntries);
	System.err.println("test.operations=" + operations);
	System.err.println("test.collisions=" + collisions);
	System.err.println("test.seed=" + seed);

	final AtomicBoolean isDone = new AtomicBoolean(false);
	final AtomicInteger opsPerTxn = new AtomicInteger(getRandomOpsPerTxn());
	final AtomicInteger opnum = new AtomicInteger(0);
	while (! isDone.get()) {
	    txnScheduler.runTask(
	        new TestAbstractKernelRunnable() {
		    private int attempts = 0;
		    public void run() throws Exception {
			initTxnState(++attempts);
                        getRandomOp().run();
			int num;
			while ((num = opnum.getAndIncrement()) < operations) {
			    if (num > 0 && num % 5000 == 0) {
				System.err.println("opnum=" + num);
			    }
			    if (opsPerTxn.get() == 0) {
				opsPerTxn.set(getRandomOpsPerTxn());
				return;
			    } else {
				opsPerTxn.decrementAndGet();
			    }
                            getRandomOp().run();
			}
			isDone.set(true);
		    }
	    }, taskOwner);
	}
	long stop = System.currentTimeMillis();
	System.err.println(
	    "ops/sec=" + Math.round((1000.0d * operations) / (stop - start)));
	System.err.println("map.size=" + control.cardinality());
    }

    /* Utilities */

    /**
     * Updates fields from data manager bindings for a new transaction.
     */
    @SuppressWarnings("unchecked")
    private void initTxnState(int attempts) throws Exception {
	if (attempts == 1) {
	    AbstractUndoable.clearAllUndos();
	} else {
	    AbstractUndoable.undoAll();
	}
	map = (ScalableHashMap) dataService.getBinding("map");
        msKeys = uncheckedCast(dataService.getBinding("keys"));
        msValues = uncheckedCast(dataService.getBinding("values"));
        msEntries = uncheckedCast(dataService.getBinding("entries"));
	if (debug) {
	    System.err.println("new transaction");
	}
    }

    /** Returns a random value for the number of operations per transaction. */
    private static int getRandomOpsPerTxn() {
	return random.nextInt(maxOpsPerTxn - minOpsPerTxn) + minOpsPerTxn;
    }

    /** Returns a random object number. */
    static int getRandomObjectNumber() {
	return random.nextInt(maxEntries);
    }

    /** Returns a random operation. */
    private Op getRandomOp() {
	return ops.get(random.nextInt(ops.size()));
    }

    /** Notes that the key for the specified object number has been removed. */
    void keyRemoved(int objnum) {
	/*
	 * Clear the items seen by the iterators if they are positioned at a
	 * key with the same hash code.  If an entry with the same key hash
	 * code is removed and added again, it might get placed after the
	 * current position of the iterator and get seen again.
	 */
	int hash = hash(objnum);
	if (hash == hash(currentKey)) {
	    keysSeen.clear(objnum);
	}
	if (hash == hash(currentValue)) {
	    valuesSeen.clear(objnum);
	}
	if (hash == hash(currentEntry)) {
	    entriesSeen.clear(objnum);
	}
    }

    /**
     * Returns the hash code for the specified value, using the value of the
     * collisions field.
     */
    static int hash(int n) {
	return n / collisions;
    }

    /**
     * Removes the argument from the data manager if it is a managed object.
     */
    static void maybeRemoveObject(Object object) {
	if (object instanceof ManagedObject) {
	    AppContext.getDataManager().removeObject(object);
	}
    }

    /** Returns the current number of objects. */
    private int getObjectCount() {
	int count = 0;
	BigInteger last = null;
	while (true) {
	    BigInteger next = dataService.nextObjectId(last);
	    if (next == null) {
		break;
	    }
            // NOTE: this count is used at the end of the test to make sure
            // that no objects were leaked in stressing the structure but
            // any given service (e.g., the task service) may accumulate
            // managed objects, so a more general way to exclude these from
            // the count would be nice but for now the specific types that
            // are accumulated get excluded from the count
            String name = dataService.createReferenceForId(next).get().
                getClass().getName();
            if (! name.equals("com.sun.sgs.impl.service.task.PendingTask"))
                count++;
	    last = next;
	}
	return count;
    }

    /** Prints the current objects above the specified value, for debugging. */
    private void printObjects(BigInteger id) {
	while (true) {
	    id = dataService.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    try {
		Object obj = dataService.createReferenceForId(id).get();
		System.err.println(id + ": (" + obj.getClass().getName() +
				   ") " + obj);
	    } catch (Exception e) {
		System.err.println(id + ": " + e);
	    }
	}
    }

    /** Defines an interface for objects whose operations can be undone. */
    interface Undoable {

	/** Undo the current set of operations. */
	void undo();

	/**
	 * Clear the current set of operations, so that they will not be undone
	 * by the next call to undo.
	 */
	void clearUndo();
    }

    /** A base class for undoable objects. */
    static abstract class AbstractUndoable implements Undoable {

	/** All undoable objects. */
	private static final List<Undoable> allUndoable =
	    new ArrayList<Undoable>();

	/** Create an instance. */
	AbstractUndoable() {
	    register(this);
	}

	/** Register an undoable object on the list of all undoable objects. */
	static void register(Undoable undoable) {
	    allUndoable.add(undoable);
	}

	/** Undo all operations on all registered undoable objects. */
	static void undoAll() {
	    for (Undoable undoable : allUndoable) {
		undoable.undo();
	    }
	}

	/** Clear the set of operations for all registered undoable objects. */
	static void clearAllUndos() {
	    for (Undoable undoable : allUndoable) {
		undoable.clearUndo();
	    }
	}	    
    }

    /** An undoable random number generator. */
    private static class UndoableRandom extends Random implements Undoable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The seed for the current set of operations. */
	private long seed;

	/** Create an instance with the specified seed. */
	UndoableRandom(long seed) {
	    super(seed);
	    this.seed = seed;
	    AbstractUndoable.register(this);
	}

	/** Reset to the current seed. */
	public void undo() {
	    setSeed(seed);
	}

	/** Set the seed for the next set of operations. */
	public void clearUndo() {
	    seed = nextLong();
	    setSeed(seed);
	}
    }

    /** An undoable bit set. */
    private static class UndoableBitSet extends AbstractUndoable {

	/** The backing bit set. */
	private BitSet bitSet;

	/** The set of operations. */
	private final List<Op> undoOps = new ArrayList<Op>();

	/** An operation. */
	private abstract class Op {
	    Op() {
		undoOps.add(0, this);
	    }
	    abstract void undo();
	}

	/** The operation for setting a bit. */
	private class SetOp extends Op {
	    private final int bit;
	    private boolean wasClear;
	    SetOp(int bit) {
		this.bit = bit;
		if (!bitSet.get(bit)) {
		    wasClear = true;
		    bitSet.set(bit);
		}
	    }
	    void undo() {
		if (wasClear) {
		    bitSet.clear(bit);
		}
	    }
	}

	/** The operation for clearing a bit. */
	private class ClearOp extends Op {
	    private final int bit;
	    private boolean wasSet;
	    ClearOp(int bit) {
		this.bit = bit;
		if (bitSet.get(bit)) {
		    wasSet = true;
		    bitSet.clear(bit);
		}
	    }
	    void undo() {
		if (wasSet) {
		    bitSet.set(bit);
		}
	    }
	}

	/** The operation for clearing all bits. */
	private class ClearAllOp extends Op {
	    private BitSet saved;
	    ClearAllOp() {
		saved = (BitSet) bitSet.clone();
		bitSet.clear();
	    }
	    void undo() {
		bitSet = saved;
	    }
	}

	/** Create an instance with the specified initial number of bits. */
	UndoableBitSet(int nbits) {
	    bitSet = new BitSet(nbits);
	}

	/** Check if the specified bit is set. */
	boolean get(int b) {
	    return bitSet.get(b);
	}

	/** Set the specified bit. */
	void set(int b) {
	    new SetOp(b);
	}

	/** Clear the specified bit. */
	void clear(int b) {
	    new ClearOp(b);
	}

	/** Clear all bits. */
	void clear() {
	    new ClearAllOp();
	}

	/** Get the number of bits set. */
	int cardinality() {
	    return bitSet.cardinality();
	}

	/* -- Implement Undoable -- */

	public void undo() {
	    for (Op op : undoOps) {
		op.undo();
	    }
	    undoOps.clear();
	}

	public void clearUndo() {
	    undoOps.clear();
	}
    }
}
