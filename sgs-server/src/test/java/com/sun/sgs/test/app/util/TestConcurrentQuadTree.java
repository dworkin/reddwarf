package com.sun.sgs.test.app.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import junit.framework.TestCase;

import com.sun.sgs.app.util.ConcurrentQuadTree;

public class TestConcurrentQuadTree extends TestCase {
    private ConcurrentQuadTree<String> quadtree;
    private final Random random = new Random(System.currentTimeMillis());

    protected void setUp() throws Exception {
	System.out.println("Testcase: " + getName());
	quadtree = new ConcurrentQuadTree<String>(1, 0, 0, 100, 100);
    }


    public void testConstructorFourArgs() {
	ConcurrentQuadTree<String> tree;
	try {
	    tree = new ConcurrentQuadTree<String>(1, 0, 0, 100, 100);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(1, 0, 0, 0, 0);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(1, 0, 0, -1, -1);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(-1, 0, 0, 0, 0);
	    fail("Expecting an IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    // Expected
	}
	try {
	    tree = new ConcurrentQuadTree<String>(-100, 0, 1, 2, 3);
	    fail("Expecting an IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    // Expected
	}

    }

    public void testConstructorFiveArgs() {
	ConcurrentQuadTree<String> tree;
	try {
	    tree = new ConcurrentQuadTree<String>(3, 0, 0, 1, 1);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(3, 1, 1, 0, 0);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(3, 0, 0, 0, 0);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(3, 0, 0, 1, 1);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(3, -1, 1, 1, -1);
	} catch (Exception e) {
	    fail("Not expecting an exception: " + e.getLocalizedMessage());
	}
	try {
	    tree = new ConcurrentQuadTree<String>(-1, 0, 0, 1, 1);
	    fail("Expecting an IllegalArgumentException");
	} catch (Exception e) {
	    // Expected
	}
	try {
	    tree = new ConcurrentQuadTree<String>(-1, -1, -1, -1, -1);
	    fail("Expecting an IllegalArgumentException");
	} catch (Exception e) {
	    // Expected
	}
    }

    private ConcurrentQuadTree<String> makeEmptyTree(int maxDepth) {
	return new ConcurrentQuadTree<String>(maxDepth, 1, 0, 0, 100, 100);
    }

    public void testAdd() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(3);
	assertTrue(tree.isEmpty());

	// testing adding elements out of bounds. Recall
	// that makeEmptyTree(int) constructs a tree comprising the
	// coordinate region (0,0) to (100,100)
	try {
	    assertFalse(tree.add(0, -1, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    assertFalse(tree.add(-1, 0, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    assertFalse(tree.add(-1, -1, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    assertFalse(tree.add(101, 100, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    assertFalse(tree.add(100, 101, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    assertFalse(tree.add(101, 101, "A"));
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());

	// start adding legal entries
	int size = 0;
	assertTrue(tree.add(0, 0, "A"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	// this should cause a split
	assertTrue(tree.add(100, 100, "B"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	assertTrue(tree.add(100, 0, "C"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	assertTrue(tree.add(0, 100, "D"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	// this should cause another split (SW quadrant)
	assertTrue(tree.add(40, 40, "E"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	// this should cause another split (SWSW quadrant)
	assertTrue(tree.add(20, 20, "F"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
    }

    public void testTryAddingPastMaximum() {
	// test depth of 0
	ConcurrentQuadTree<String> tree = makeEmptyTree(0);
	assertTrue(tree.add(20, 20, "A"));
	assertFalse(tree.isEmpty());
	assertEquals(1, tree.size());

	// since the depth is already 0, we should not be able to add another
	// element anywhere. Assert these all these adds result in false.
	assertFalse(tree.add(90, 90, "B"));
	assertFalse(tree.isEmpty());
	assertEquals(1, tree.size());
	assertFalse(tree.add(21, 21, "B"));
	assertFalse(tree.isEmpty());
	assertEquals(1, tree.size());
	assertFalse(tree.add(20, 20, "B"));
	assertFalse(tree.isEmpty());
	assertEquals(1, tree.size());

	// test depth of 3: crowd all elements in the SW corners
	int size = 0;
	tree = makeEmptyTree(3);
	assertTrue(tree.isEmpty());
	assertTrue(tree.add(10, 10, "A"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	assertTrue(tree.add(20, 20, "B"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	// we should now be at a depth of 3. Try adding another element
	// nearby. This should result in tree.add( ) returning false
	assertFalse(tree.add(5, 5, "C"));
	assertFalse(tree.isEmpty());
	assertEquals(size, tree.size());
	assertFalse(tree.add(15, 15, "C"));
	assertFalse(tree.isEmpty());
	assertEquals(size, tree.size());
	// But, we should be able to add elements in adjacent quadrants
	// in the parent's level (level 2). Try NW, NE, and SE:
	assertTrue(tree.add(10, 40, "NW"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	assertTrue(tree.add(40, 40, "NE"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
	assertTrue(tree.add(40, 20, "SE"));
	assertFalse(tree.isEmpty());
	assertEquals(++size, tree.size());
    }

    public void testFillToCapacity() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(2);
	// keep adding elements so that the entire space is consumed.
	// All elements should be added successfully because they are
	// equidistant from one another.
	int size = 0;
	for (double x = 12.5; x < 100; x += 25) {
	    for (double y = 12.5; y < 100; y += 25) {
		assertTrue(tree.add(x, y, Double.toString(x) + "," +
			Double.toString(y)));
		assertEquals(++size, tree.size());
	    }
	}
    }

    public void testIsEmpty() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(3);
	assertTrue(tree.isEmpty());

	// test simple one-level add and remove
	assertTrue(tree.add(5, 5, "A"));
	assertFalse(tree.isEmpty());
	assertNotNull(tree.remove(5, 5));

	assertTrue(tree.isEmpty());

	// test multi-level add and removal. This addition
	// should cause the tree to go to three levels
	assertTrue(tree.add(1, 1, "B"));
	assertFalse(tree.isEmpty());
	assertTrue(tree.add(15, 1, "C"));
	assertFalse(tree.isEmpty());
	// begin removing
	assertNotNull(tree.remove(1, 1));
	assertFalse(tree.isEmpty());
	assertNotNull(tree.remove(15, 1));

	assertTrue(tree.isEmpty());
    }

    public void testRemoveWithRandomEntries_TreeDepth0() {
	removeUsingRandomEntries(0, 1);
    }

    public void testRemoveWithRandomEntries_TreeDepth1() {
	removeUsingRandomEntries(1, 2);
    }

    public void testRemoveWithRandomEntries_TreeDepth2() {
	removeUsingRandomEntries(2, 5);
    }

    public void testRemoveWithRandomEntries_TreeDepth3() {
	removeUsingRandomEntries(3, 20);
    }

    public void testRemoveWithRandomEntries_StressTest_Depth0() {
	removeUsingRandomEntries(0, 5);
    }

    public void testRemoveWithRandomEntries_StressTest_Depth1() {
	removeUsingRandomEntries(1, 10);
    }

    public void testRemoveWithRandomEntries_StressTest_Depth2() {
	removeUsingRandomEntries(2, 30);
    }

    public void testRemoveWithRandomEntries_StressTest_Depth3() {
	removeUsingRandomEntries(3, 100);
    }

    private TestContainer addToTree(int maxDepth, int timesToRun) {
	ConcurrentQuadTree<String> tree =
		new ConcurrentQuadTree<String>(maxDepth, 0, 0, 100, 100);
	double x, y, element;
	double[][] values = new double[timesToRun][3];

	// add elements to the list, while keeping track of what they were
	for (int i = 0; i < timesToRun; i++) {
	    x = Math.abs(random.nextDouble() * 100);
	    y = Math.abs(random.nextDouble() * 100);
	    element = random.nextInt(99999);
	    values[i][TestContainer.X] = x;
	    values[i][TestContainer.Y] = y;
	    values[i][TestContainer.VALUE] = element;

	    if (tree.add(x, y, Double.toString(element))) {
		assertFalse(tree.isEmpty());
	    }
	}
	return new TestContainer(tree, values);
    }

    private void removeUsingRandomEntries(final int maxDepth,
	    final int timesToRun) {
	TestContainer container = addToTree(maxDepth, timesToRun);
	ConcurrentQuadTree<String> tree = container.getTree();
	double values[][] = container.getTable();

	// now remove all the elements. We won't care if the
	// remove returns false because the element might not
	// have been added in the first place
	for (int i = 0; i < timesToRun; i++) {
	    double x = values[i][TestContainer.X];
	    double y = values[i][TestContainer.Y];
	    String s = tree.remove(x, y);
	    if (s != null) {
		assertEquals(Double.toString(values[i][TestContainer.VALUE]),
			s);
	    }
	}
	assertTrue(tree.isEmpty());
	assertEquals(0, tree.size());
    }

    public void testSize() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(2);
	int size = 0;
	// test legal values
	tree.add(1, 1, "A");
	assertEquals(++size, tree.size());
	tree.add(99, 99, "B");
	assertEquals(++size, tree.size());
	tree.add(1, 99, "C");
	assertEquals(++size, tree.size());
	tree.add(99, 1, "D");
	assertEquals(++size, tree.size());

	// test some outside cases
	tree.remove(33, 33);
	assertEquals(size, tree.size());
	tree.remove(100, 100);
	assertEquals(size, tree.size());
	tree.remove(0, 0);
	assertEquals(size, tree.size());
	tree.remove(1, 0);
	assertEquals(size, tree.size());

	// remove and add some entries
	tree.remove(1, 1);
	assertEquals(--size, tree.size());
	tree.add(1, 1, "E");
	assertEquals(++size, tree.size());
	tree.remove(1, 1);
	assertEquals(--size, tree.size());
	tree.remove(1, 1); // already was removed
	assertEquals(size, tree.size());
	tree.remove(1, 99);
	assertEquals(--size, tree.size());
	tree.remove(99, 1);
	assertEquals(--size, tree.size());
	tree.remove(99, 99);
	assertEquals(--size, tree.size());

	assertTrue(tree.isEmpty());
    }
    
    
    /**
     * The {@code TestContainer} inner class is a simple object which stores a
     * quadtree and a {@code double} array. The {@code double} array holds
     * onto the coordinates and values of the entries in the tree for
     * verification purposes
     */
    private static class TestContainer {
	static final int X = 0;
	static final int Y = 1;
	static final int VALUE = 2;

	final ConcurrentQuadTree<String> tree;
	final double[][] table;

	TestContainer(ConcurrentQuadTree<String> tree, double[][] table) {
	    this.tree = tree;
	    this.table = table;
	}

	ConcurrentQuadTree<String> getTree() {
	    return tree;
	}

	double[][] getTable() {
	    return table;
	}
    }

    private void setUsingRandomEntries(int maxDepth, int iterations) {
	TestContainer container = addToTree(maxDepth, iterations);
	ConcurrentQuadTree<String> tree = container.getTree();
	double values[][] = container.getTable();

	int sizeBefore = tree.size();
	for (int i = 0; i < values.length; i++) {
	    String old =
		    tree.set(values[i][TestContainer.X],
			    values[i][TestContainer.Y], Integer.toString(i));
	    assertEquals(Double.toString(values[i][TestContainer.VALUE]), old);
	    assertEquals(sizeBefore, tree.size());
	}
    }

    public void testSetWithRandomEntries_TreeDepth0() {
	setUsingRandomEntries(0, 1);
    }

    public void testSetWithRandomEntries_TreeDepth1() {
	setUsingRandomEntries(1, 2);
    }

    public void testSetWithRandomEntries_TreeDepth2() {
	setUsingRandomEntries(2, 7);
    }

    public void testSetWithRandomEntries_TreeDepth3() {
	setUsingRandomEntries(3, 20);
    }

    public void testSetWithRandomEntries_StressTest_TreeDepth0() {
	setUsingRandomEntries(0, 2);
    }

    public void testSetWithRandomEntries_StressTest_TreeDepth1() {
	setUsingRandomEntries(1, 10);
    }

    public void testSetWithRandomEntries_StressTest_TreeDepth2() {
	setUsingRandomEntries(2, 30);
    }

    public void testSetWithRandomEntries_StressTest_TreeDepth3() {
	setUsingRandomEntries(3, 100);
    }

    public void testSetCornerCases() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(1);
	int sizeBefore = tree.size();
	assertEquals(0, sizeBefore);
	assertTrue(tree.isEmpty());

	// This set command should not have changed the tree
	String s = tree.set(1, 1, "A");
	assertEquals(null, s);
	assertTrue(tree.isEmpty());
	assertEquals(sizeBefore, tree.size());

	// should not change tree; tree is still empty
	s = tree.set(1, 1, null);
	assertEquals(null, s);
	assertTrue(tree.isEmpty());
	assertEquals(sizeBefore, tree.size());
    }

    private void getUsingRandomEntries(int maxDepth, int iterations) {
	TestContainer container = addToTree(maxDepth, iterations);
	ConcurrentQuadTree<String> tree = container.getTree();
	double[][] values = container.getTable();

	for (int i = 0; i < iterations; i++) {
	    double x = values[i][TestContainer.X];
	    double y = values[i][TestContainer.Y];
	    assertEquals(Double.toString(values[i][TestContainer.VALUE]), tree.get(x, y));
	}
    }

    public void testGetElementUsingCoords_TreeDepth0() {
	getUsingRandomEntries(0, 1);
    }

    public void testGetElementUsingCoords_TreeDepth1() {
	getUsingRandomEntries(1, 2);
    }

    public void testGetElementUsingCoords_TreeDepth2() {
	getUsingRandomEntries(2, 5);
    }

    public void testGetElementUsingCoords_TreeDepth3() {
	getUsingRandomEntries(3, 10);
    }

    public void testGetElementUsingCoords_StressTest_TreeDepth0() {
	getUsingRandomEntries(0, 2);
    }

    public void testGetElementUsingCoords_StressTest_TreeDepth1() {
	getUsingRandomEntries(1, 7);
    }

    public void testGetElementUsingCoords_StressTest_TreeDepth2() {
	getUsingRandomEntries(2, 25);
    }

    public void testGetElementUsingCoords_StressTest_TreeDepth3() {
	getUsingRandomEntries(3, 100);
    }

    
    
    
    public void testEnvelopeIterator_TreeDepth0() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(0);
	tree.add(50, 50, "A");
	assertFalse(tree.isEmpty());
	
	Iterator<String> iter = tree.envelopeIterator(0, 0, 100, 100);
	int i=0;
	while (iter.hasNext()) {
	    i++;
	    iter.next();
	}
	assertEquals(1, tree.size());
	assertEquals(tree.size(), i);
    }
    
    public void testEnvelopeIterator_TreeDepth1() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(1);
	tree.add(25, 25, "A");
	tree.add(49, 51, "B");
	tree.add(51, 49, "C");
	tree.add(50, 50, "D");
	assertFalse(tree.isEmpty());
	assertEquals(4, tree.size());
	
	Iterator<String> iter = tree.envelopeIterator(0, 0, 50, 50);
	int i=0;
	while (iter.hasNext()) {
	    i++;
	    String s = iter.next();
	    assertTrue(s.equals("A") || s.equals("D"));
	}
	// only "A" and "D" should be the only ones that exist
	assertEquals(2, i);
    }
    
    
    public void testEnvelopeIterator_TreeDepth2() {
	ConcurrentQuadTree<String> tree = makeEmptyTree(2);
	tree.add(25, 25, "A");
	tree.add(49, 51, "B");
	tree.add(51, 49, "C");
	tree.add(50, 50, "D");
	tree.add(5, 5, "E");
	assertFalse(tree.isEmpty());
	assertEquals(5, tree.size());
	
	Iterator<String> iter = tree.envelopeIterator(0, 0, 50, 50);
	int i=0;
	while (iter.hasNext()) {
	    i++;
	    String s = iter.next();
	    assertTrue(s.equals("A") || s.equals("D") || s.equals("E"));
	}
	// only "A", "D", "E" should be in this envelope
	assertEquals(3, i);
    }
    
    
    public void testGetDirectionalEnvelopeBound() {
	double x1 = random.nextInt() % 100;
	double y1 = random.nextInt() % 100;
	double x2 = random.nextInt() % 100;
	double y2 = random.nextInt() % 100;
	ConcurrentQuadTree<String> tree = new ConcurrentQuadTree<String>(0, x1, y1, x2, y2);
	
	assertEquals(Math.min(x1, x2), tree.getDirectionalEnvelopeBound(ConcurrentQuadTree.Coordinate.X_MIN));
	assertEquals(Math.max(x1, x2), tree.getDirectionalEnvelopeBound(ConcurrentQuadTree.Coordinate.X_MAX));
	assertEquals(Math.min(y1, y2), tree.getDirectionalEnvelopeBound(ConcurrentQuadTree.Coordinate.Y_MIN));
	assertEquals(Math.max(y1, y2), tree.getDirectionalEnvelopeBound(ConcurrentQuadTree.Coordinate.Y_MAX));
    }
    
    /*--------------- test iterator operations ----------------------*/
    
    public void testIteratorHasNext() {
	Iterator<String> iter;
	
	// try an empty tree
	ConcurrentQuadTree<String> tree = makeEmptyTree(5);
	iter = tree.iterator();
	assertFalse(iter.hasNext());
	
	// try with one element
	tree = makeEmptyTree(5);
	tree.add(1, 1, "A");
	iter = tree.iterator();
	assertTrue(iter.hasNext());
	iter.next();
	assertFalse(iter.hasNext());
    }
    
    
    public void testIteratorHasNextWithRandomElements() {
	final int MAX = 10;
	ConcurrentQuadTree<String> tree;
	Iterator<String> iter;
	
	for (int i=0 ; i<5 ; i++) {
	    int count = 0;
	    int numElements = random.nextInt(MAX);
	    TestContainer container = addToTree(MAX, numElements);
	    tree = container.getTree();
	    iter = tree.iterator();
	    
	    while (iter.hasNext() || count > MAX) {
		count++;
		iter.next();
	    }
	    assertEquals(numElements, count);
	}
    }
    
    public void testIteratorNext() {
	// test with an empty tree
	ConcurrentQuadTree<String> tree = makeEmptyTree(5);
	Iterator<String> iter = tree.iterator();
	try {
	    iter.next();
	    fail ("Expecting NoSuchElementException");
	} catch (NoSuchElementException nsee) {
	}
	
	// try with one element
	final String s = "A";
	tree = makeEmptyTree(5);
	tree.add(1, 1, s);
	iter = tree.iterator();
	assertEquals(s, iter.next());
	try {
	    iter.next();
	    fail ("Expecting NoSuchElementException");
	} catch (NoSuchElementException nsee) {
	}
    }
    
    
    public void testIteratorNextWithRandomElements() {
	final int MAX = 10;
	ConcurrentQuadTree<String> tree;
	List<String> shadow;
	Iterator<String> iter;
	
	for (int i=0 ; i<5 ; i++) {
	    shadow = new ArrayList<String>();
	    tree = makeEmptyTree(10);
	    int count = 0;
	    int numElements = random.nextInt(MAX);
	    
	    // add elements randomly to the tree
	    for (int j=0 ; j<numElements ; j++) {
		String val = Integer.toString(j);
		shadow.add(val);
		tree.add(random.nextInt(100), random.nextInt(100), val);
	    }
	    iter = tree.iterator();
	    
	    // check that we can remove all items we get from the iterator
	    while (iter.hasNext() || count > MAX) {
		count++;
		String s = iter.next();
		assertTrue(shadow.remove(s));
	    }
	    // check that the shadow list is empty; we should have removed
	    // all its elements
	    assertTrue(shadow.isEmpty());
	}
    }
    
    
    public void testIteratorRemove() {
	// test with an empty tree
	ConcurrentQuadTree<String> tree = makeEmptyTree(5);
	Iterator<String> iter = tree.iterator();
	try {
	    iter.remove();
	    fail ("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	}
	
	// test removing having not called next()
	tree = makeEmptyTree(5);
	tree.add(1, 1, "A");
	iter = tree.iterator();
	try {
	    iter.remove();
	    fail ("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	}
	
	// test removing after calling next()
	tree = makeEmptyTree(5);
	tree.add(1, 1, "A");
	iter = tree.iterator();
	assertTrue(iter.hasNext());
	iter.next();
	iter.remove();
	assertFalse(iter.hasNext());
	assertTrue(tree.isEmpty());
	
	// test successive removes
	tree = makeEmptyTree(5);
	tree.add(1, 1, "A");
	tree.add(99, 99, "B");
	iter = tree.iterator();
	assertTrue(iter.hasNext());
	iter.next();
	iter.remove();
	try {
	    iter.remove();
	    fail ("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	}
	iter.next();
	iter.remove();
	assertFalse(iter.hasNext());
	assertTrue(tree.isEmpty());
    }
    
    /*--------------- test some boundary cases ----------------------*/
    
    
}
