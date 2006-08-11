package com.sun.sgs.kernel.scheduling;

import com.sun.sgs.kernel.*;

import org.junit.Assert.*;

import java.util.*;

/**
 * A JUnit 4 test suite for {@link Priority} operations and
 * implementations.
 *
 *
 * @since  1.0
 * @author David Jurgens
 */
public class PriorityTests {

    private final List<NumericPriority> numericPriorities;

    public PriorityTests() {
	numericPriorities = new ArrayList();
    }

    /**
     * Clears the numericPriorities list and adds all of the standard
     * {@link NumericPrioity} constants.
     */
    @Before public void resetNumericPriorities() {
	numericPriorites.clear();
	numericPriorites.add(NumericPriority.REAL_TIME);
	numericPriorites.add(NumericPriority.HIGHEST);
	numericPriorites.add(NumericPriority.HIGH);
	numericPriorites.add(NumericPriority.NORMAL);
	numericPriorites.add(NumericPriority.LOW);
	numericPriorites.add(NumericPriority.LOWEST);
	numericPriorites.add(NumericPriority.OPTIONAL);
    }
    

    /**
     * Uses a set of priorities and tests whether the ordering is
     * consistent after a series of shuffles and followed by a sort.
     */
    @Test public void testNumericPriorityOrdering() {
	List<NumericPriority> sorted = new ArrayList<NumericPriority>(numericPriorities);
	Collections.sort(sorted);
	for (int i = 0; i < numericPriorities.size(); ++i) { // arbitrary times
	    Collections.shuffle(numericPriorities);
	    Collections.sort(numericPriorities);
	    assertTrue(numericPriorities.equals(sorted));
	}
    }

    /**
     * Creates a new type of {@link Priority} and then verifies that
     * priority implementations throw an error if {@link
     * Priority#getWeightedComparison(Priority)} is called using it.
     */
    @Test(expected= Error.class) public void testIncomparablePriorities {
	// create an anonymous inner class
	Priority p = new Priority() { 
		public int compareTo(Object other) { return -1; }
		public double getWeightedComparison(Priority other) { return -1; }
	    };
	for (Priority numeric : numericPriorities) {
	    numeric.getWeightedComparison(p); // should throw an Error the first time it is called.
	}
    }

    /**
     * For priorities <code>a</code> and <code>b</code>, tests that
     * <code>Math.abs(a.getWeightedComparison(b)) ==
     * Math.abs(b.getWeightedComparison(a))</code>.
     */
    @Test public void testNumericPriorityWeightedComparison() {
	for (Priority p : numericPriorities) {
	    for (Priority q : numericPriorities) {
		assertTrue(Math.abs(p.getWeightedComparison(q)) == Math.abs(q.getWeightedComparison(p)));
	    }
	}
    }

}
