package com.sun.sgs.test.app.util;

import com.sun.sgs.impl.util.LoggerWrapper;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

class BasicMapStressTest extends TestCase {
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(BasicMapStressTest.class.getName()));
    private static final long seed =
	Long.getLong("test.seed", System.currentTimeMillis());
    Map<String, Object> map;
    Random random;
    private boolean nullKeys;
    private boolean nullValues;
    private int maxValue;
    private BitSet bits;

    BasicMapStressTest(String name, Map<String, Object> map) {
	super(name);
	this.map = map;
    }

    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
    }

    void stress(
	boolean nullKeys, boolean nullValues, int maxValue)
    {
	this.nullKeys = nullKeys;
	this.nullValues = nullValues;
	this.maxValue = maxValue;
	bits = new BitSet(maxValue);
	random = new Random(seed);
	System.err.println("Seed: " + seed);
	increasing();
	decreasing();
    }

    private void increasing() {
	long start = System.currentTimeMillis();
	int i;
	for (i = 0; !full(); i++) {
	    beforeOp(i);
	    switch (random.nextInt(7)) {
	    case 0:
	    case 1:
		putNew();
		break;
	    case 2:
		putReplace();
		break;
	    case 3:
		removeNotFound();
		break;
	    case 4:
		removeFound();
		break;
	    case 5:
		getFound();
		break;
	    case 6:
		getNotFound();
		break;
	    default:
		throw new AssertionError();
	    }
	}
	long stop = System.currentTimeMillis();
	System.err.println(
	    "increase: ops = " + i + ", time = " + (stop - start) + " ms");
    }

    private void decreasing() {
	long start = System.currentTimeMillis();
	int i;
	for (i = 0; !empty(); i++) {
	    beforeOp(i);
	    switch (random.nextInt(7)) {
	    case 0:
		putNew();
		break;
	    case 1:
		putReplace();
		break;
	    case 2:
		removeNotFound();
		break;
	    case 3:
	    case 4:
		removeFound();
		break;
	    case 5:
		getFound();
		break;
	    case 6:
		getNotFound();
		break;
	    default:
		throw new AssertionError();
	    }
	}
	long stop = System.currentTimeMillis();
	System.err.println(
	    "decrease: ops = " + i + ", time = " + (stop - start) + " ms");
    }

    void beforeOp(int i) { }

    private int nextAbsentKey() {
	int n = bits.nextClearBit(random.nextInt(maxValue));
	if (n >= maxValue) {
	    n = bits.nextClearBit(0);
	}
	if (n >= maxValue) {
	    throw new RuntimeException("No more absent keys");
	}
	return n;
    }

    private int nextPresentKey() {
	int r = random.nextInt(maxValue);
	int n = bits.nextSetBit(r);
	if (n < 0) {
	    n = bits.nextSetBit(0);
	}
	if (n < 0) {
	    throw new RuntimeException(
		"No keys present: r:" + r + ", bits:" + bits);
	}
	return n;
    }

    Object createValue(int n) {
	if (nullValues && (n % 17) == 7) {
	    return null;
	} else {
	    return "value-" + n;
	}
    }

    private String createKey(int n) {
	if (n == 0 && nullKeys) {
	    return null;
	} else {
	    return "key-" + n;
	}
    }

    private void putReplace() {
	if (!empty()) {
	    put(nextPresentKey());
	}
    }

    private void putNew() {
	if (!full()) {
	    put(nextAbsentKey());
	}
    }

    private void put(int n) {
	String key = createKey(n);
	Object value = createValue(n);
	Object oldValue = map.put(key, value);
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE, "put({0}, {1}) => {2}", key, value, oldValue);
	}
	if (bits.get(n)) {
	    if (!safeEquals(value, oldValue)) {
		System.err.println(map);
	    }
	    assertEquals(value, oldValue);
	} else {
	    assertNull(oldValue);
	    bits.set(n);
	}
    }

    private void removeFound() {
	if (!empty()) {
	    remove(nextPresentKey());
	}
    }

    private void removeNotFound() {
	if (!full()) {
	    remove(nextAbsentKey());
	}
    }

    private void remove(int n) {
	boolean present = bits.get(n);
	String key = createKey(n);
	Object value = createValue(n);
	Object oldValue;
	String op;
	switch (random.nextInt(3 /*present ? 4 : 3*/)) {
	case 0:
	    op = "";
	    oldValue = map.remove(key);
	    break;
	case 1:
	    op = " [keySet().remove]";
	    oldValue = map.keySet().remove(key) ? value : null;
	    break;
	case 2:
	    op = " [entrySet().remove]";
	    oldValue = map.entrySet().remove(SimpleEntry.create(key, value))
		? value : null;
 	    break;
	case 3:
	    op = " [iterator().remove]";
	    Iterator<Entry<String, Object>> iter = map.entrySet().iterator();
	    while (true) {
		if (!iter.hasNext()) {
		    throw new RuntimeException("Key not found");
		}
		Entry<String, Object> entry = iter.next();
		if (safeEquals(key, entry.getKey())) {
		    iter.remove();
		    oldValue = entry.getValue();
		    break;
		}
	    }
	    break;
	default:
	    throw new AssertionError();
	}
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE, "remove({0}) => {1}{2}", key, oldValue, op);
	}
	if (present) {
	    assertEquals(value, oldValue);
	    bits.clear(n);
	} else {
	    assertNull(oldValue);
	}
    }

    private void getFound() {
	if (!empty()) {
	    get(nextPresentKey());
	}
    }

    private void getNotFound() {
	if (!full()) {
	    get(nextAbsentKey());
	}
    }

    private void get(int n) {
	String key = createKey(n);
	Object value = createValue(n);
	boolean present = bits.get(n);
	Object existing = map.get(key);
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(
		Level.FINE, "get({0}) => {1}", key, existing);
	}
	assertEquals(present ? value : null, existing);
	assertEquals(present, map.containsKey(key));
	assertEquals(present, map.keySet().contains(key));
	assertEquals(
	    present, map.entrySet().contains(SimpleEntry.create(key, value)));
    }

    private boolean empty() {
	return bits.isEmpty();
    }

    private boolean full() {
	return bits.cardinality() >= maxValue;
    }

    static boolean safeEquals(Object x, Object y) {
	return x == y || (x != null && x.equals(y));
    }
}
