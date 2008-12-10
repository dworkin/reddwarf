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

    private ConcurrentQuadTree<String> makeEmptyTree() {
	return new ConcurrentQuadTree<String>(1, 0, 0, 100, 100);
    }

    public void testAdd() {
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	assertTrue(tree.isEmpty());

	// testing adding elements out of bounds. Recall
	// that makeEmptyTree(int) constructs a tree comprising the
	// coordinate region (0,0) to (100,100)
	try {
	    tree.put(0, -1, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    tree.put(-1, 0, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    tree.put(-1, -1, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    tree.put(101, 100, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    tree.put(100, 101, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());
	try {
	    tree.put(101, 101, "A");
	    fail("Expecting IllegalArgumentException");
	} catch (IllegalArgumentException iae) {
	}
	assertTrue(tree.isEmpty());

	// start adding legal entries
	tree.put(0, 0, "A");
	assertFalse(tree.isEmpty());
	// this should cause a split
	tree.put(100, 100, "B");
	assertFalse(tree.isEmpty());
	tree.put(100, 0, "C");
	assertFalse(tree.isEmpty());
	tree.put(0, 100, "D");
	assertFalse(tree.isEmpty());
	// this should cause another split (SW quadrant)
	tree.put(40, 40, "E");
	assertFalse(tree.isEmpty());
	// this should cause another split (SWSW quadrant)
	tree.put(20, 20, "F");
	assertFalse(tree.isEmpty());
    }

    public void testTryAddingPastMaximum() {
	// test depth of 0
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	tree.put(20, 20, "A");
	assertFalse(tree.isEmpty());

	// since the depth is already 0, we should not be able to add another
	// element anywhere. Assert these all these adds result in false.
	tree.put(90, 90, "B");
	assertFalse(tree.isEmpty());
	tree.put(21, 21, "B");
	assertFalse(tree.isEmpty());
	tree.put(20, 20, "B");
	assertFalse(tree.isEmpty());

	// test depth of 3: crowd all elements in the SW corners
	int size = 0;
	tree = makeEmptyTree();
	assertTrue(tree.isEmpty());
	tree.put(10, 10, "A");
	assertFalse(tree.isEmpty());
	tree.put(20, 20, "B");
	assertFalse(tree.isEmpty());
	// we should now be at a depth of 3. Try adding another element
	// nearby. This should result in tree.put( ) returning false
	tree.put(5, 5, "C");
	assertFalse(tree.isEmpty());
	tree.put(15, 15, "C");
	assertFalse(tree.isEmpty());
	// But, we should be able to add elements in adjacent quadrants
	// in the parent's level (level 2). Try NW, NE, and SE:
	tree.put(10, 40, "NW");
	assertFalse(tree.isEmpty());
	tree.put(40, 40, "NE");
	assertFalse(tree.isEmpty());
	tree.put(40, 20, "SE");
	assertFalse(tree.isEmpty());
    }


    public void testIsEmpty() {
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	assertTrue(tree.isEmpty());

	// test simple one-level add and remove
	tree.put(5, 5, "A");
	assertFalse(tree.isEmpty());
	assertNotNull(tree.remove(5, 5));

	assertTrue(tree.isEmpty());

	// test multi-level add and removal. This addition
	// should cause the tree to go to three levels
	tree.put(1, 1, "B");
	assertFalse(tree.isEmpty());
	tree.put(99, 1, "C");
	assertFalse(tree.isEmpty());
	// begin removing
	assertNotNull(tree.remove(1, 1));
	assertFalse(tree.isEmpty());
	assertNotNull(tree.remove(99, 1));

	assertTrue(tree.isEmpty());
    }

    public void testRemoveWithRandomEntries_BucketSize1() {
	removeUsingRandomEntries(1, 5);
    }

    public void testRemoveWithRandomEntries_BucketSize2() {
	removeUsingRandomEntries(2, 10);
    }

    public void testRemoveWithRandomEntries_BucketSize3() {
	removeUsingRandomEntries(3, 25);
    }

    public void testRemoveWithRandomEntries_BucketSize4() {
	removeUsingRandomEntries(4, 50);
    }

    public void testRemoveWithRandomEntries_StressTest_BucketSize1() {
	removeUsingRandomEntries(1, 100);
    }

    
    public void testRemoveWithRandomEntries_StressTest_BucketSize2() {
	removeUsingRandomEntries(2, 100);
    }
    
    public void testRemoveWithRandomEntries_StressTest_BucketSize3() {
	removeUsingRandomEntries(3, 100);
    }
    
    public void testRemoveWithRandomEntries_StressTest_BucketSize10() {
	removeUsingRandomEntries(10, 100);
    }
    

    private TestContainer addToTree(int bucketSize, int timesToRun) {
	ConcurrentQuadTree<String> tree =
		new ConcurrentQuadTree<String>(bucketSize, 0, 0, 100, 100);
	double x, y, element;
	double[][] values = new double[timesToRun][3];

	// add elements to the list, while keeping track of what they were
	for (int i = 0; i < timesToRun; i++) {
	    boolean retry = false;
	    
	    do {
		x = Math.abs(random.nextDouble() * 100);
		y = Math.abs(random.nextDouble() * 100);
		element = random.nextInt(99999);

		if (tree.put(x, y, Double.toString(element))) {
		    values[i][TestContainer.X] = x;
		    values[i][TestContainer.Y] = y;
		    values[i][TestContainer.VALUE] = element;
		    retry = false;
		} else {
		    retry = true;
		}
	    } while (retry);
	}
	return new TestContainer(tree, values);
    }

    private void removeUsingRandomEntries(final int bucketSize,
	    final int timesToRun) {
	TestContainer container = addToTree(bucketSize, timesToRun);
	ConcurrentQuadTree<String> tree = container.getTree();
	double values[][] = container.getTable();

	// now remove all the elements. We won't care if the
	// remove returns null because the element might not
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
    }

    
    
    public void testAddingAtSamePoint() {
	// Create a tree with a bucket size larger than 1
	ConcurrentQuadTree<String> tree = new ConcurrentQuadTree<String>(5, 0, 0, 100, 100);
	assertTrue(tree.isEmpty());
	tree.put(1, 1, "A");
	tree.put(1, 1, "B");
	tree.put(1, 1, "C");
	assertNotNull(tree.remove(1, 1));
	assertNotNull(tree.remove(1, 1));
	assertNotNull(tree.remove(1, 1));
	
	tree.put(99, 99, "D");
	tree.put(99, 99, "E");
	tree.put(99, 99, "F");
	assertNotNull(tree.remove(99, 99));
	assertNotNull(tree.remove(99, 99));
	assertNotNull(tree.remove(99, 99));
	
    }
    
    
    public void testRemovingAtSamePoint() {
	final int bucketSize = 5;
	ConcurrentQuadTree<String> tree = new ConcurrentQuadTree<String>(bucketSize, 0, 0, 100, 100);
	assertTrue(tree.isEmpty());
	List<String> shadow = new ArrayList<String>();
	
	// add to the tree and shadow
	for (int i=0 ; i<bucketSize ; i++) {
	    String s = Integer.toString(i);
	    tree.put(1, 1, s);
	    shadow.add(s);
	}
	
	// remove from the tree and shadow list
	String val;
	while ((val = tree.remove(1, 1)) != null) {
	    shadow.remove(val);
	}
	assertEquals(shadow.isEmpty(), tree.isEmpty());
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

    private void setUsingRandomEntries(int bucketSize, int iterations) {
	TestContainer container = addToTree(bucketSize, iterations);
	ConcurrentQuadTree<String> tree = container.getTree();
	double values[][] = container.getTable();

	for (int i = 0; i < values.length; i++) {
	    String old =
		    tree.set(values[i][TestContainer.X],
			    values[i][TestContainer.Y], Integer.toString(i));
	    assertEquals(Double.toString(values[i][TestContainer.VALUE]), old);
	}
    }

    public void testSetWithRandomEntries_BucketSize1() {
	setUsingRandomEntries(1, 2);
    }

    public void testSetWithRandomEntries_BucketSize2() {
	setUsingRandomEntries(2, 5);
    }

    public void testSetWithRandomEntries_BucketSize3() {
	setUsingRandomEntries(3, 10);
    }

    public void testSetWithRandomEntries_BucketSize4() {
	setUsingRandomEntries(4, 25);
    }

    public void testSetWithRandomEntries_StressTest_BucketSize1() {
	setUsingRandomEntries(1, 100);
    }

    public void testSetWithRandomEntries_StressTest_BucketSize2() {
	setUsingRandomEntries(2, 100);
    }
    
    public void testSetWithRandomEntries_StressTest_BucketSize3() {
	setUsingRandomEntries(3, 100);
    }
    
    public void testSetWithRandomEntries_StressTest_BucketSize10() {
	setUsingRandomEntries(10, 100);
    }

    public void testSetCornerCases() {
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	assertTrue(tree.isEmpty());

	// This set command should not have changed the tree
	String s = tree.set(1, 1, "A");
	assertEquals(null, s);
	assertTrue(tree.isEmpty());

	// should not change tree; tree is still empty
	s = tree.set(1, 1, null);
	assertEquals(null, s);
	assertTrue(tree.isEmpty());
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
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	tree.put(50, 50, "A");
	assertFalse(tree.isEmpty());
	
	Iterator<String> iter = tree.boundingBoxIterator(0, 0, 100, 100);
	int i=0;
	while (iter.hasNext()) {
	    i++;
	    iter.next();
	}
	assertEquals(1, i);
    }
    
    public void testEnvelopeIterator_TreeDepth1() {
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	tree.put(25, 25, "A");
	tree.put(49, 51, "B");
	tree.put(51, 49, "C");
	tree.put(50, 50, "D");
	assertFalse(tree.isEmpty());
	
	Iterator<String> iter = tree.boundingBoxIterator(0, 0, 50, 50);
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
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	tree.put(25, 25, "A");
	tree.put(49, 51, "B");
	tree.put(51, 49, "C");
	tree.put(50, 50, "D");
	tree.put(5, 5, "E");
	assertFalse(tree.isEmpty());
	
	Iterator<String> iter = tree.boundingBoxIterator(0, 0, 50, 50);
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
	ConcurrentQuadTree<String> tree =
		new ConcurrentQuadTree<String>(0, x1, y1, x2, y2);

	assertEquals(Math.min(x1, x2),
		tree.getDirectionalBoundingBox()[ConcurrentQuadTree.X_MIN]);
	assertEquals(Math.max(x1, x2),
		tree.getDirectionalBoundingBox()[ConcurrentQuadTree.X_MAX]);
	assertEquals(Math.min(y1, y2),
		tree.getDirectionalBoundingBox()[ConcurrentQuadTree.Y_MIN]);
	assertEquals(Math.max(y1, y2),
		tree.getDirectionalBoundingBox()[ConcurrentQuadTree.Y_MAX]);
    }
    
    /*--------------- test iterator operations ----------------------*/
    
    public void testIteratorHasNext() {
	Iterator<String> iter;
	
	// try an empty tree
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	iter = tree.iterator();
	assertFalse(iter.hasNext());
	
	// try with one element
	tree = makeEmptyTree();
	tree.put(1, 1, "A");
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
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	Iterator<String> iter = tree.iterator();
	try {
	    iter.next();
	    fail ("Expecting NoSuchElementException");
	} catch (NoSuchElementException nsee) {
	}
	
	// try with one element
	final String s = "A";
	tree = makeEmptyTree();
	tree.put(1, 1, s);
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
	    tree = makeEmptyTree();
	    int count = 0;
	    int numElements = random.nextInt(MAX);
	    
	    // add elements randomly to the tree
	    for (int j=0 ; j<numElements ; j++) {
		String val = Integer.toString(j);
		
		if (tree.put(random.nextInt(100), random.nextInt(100), val)){
		    shadow.add(val);
		}
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
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	Iterator<String> iter = tree.iterator();
	try {
	    iter.remove();
	    fail ("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	}
	
	// test removing having not called next()
	tree = makeEmptyTree();
	tree.put(1, 1, "A");
	iter = tree.iterator();
	try {
	    iter.remove();
	    fail ("Expecting IllegalStateException");
	} catch (IllegalStateException ise) {
	}
	
	// test removing after calling next()
	tree = makeEmptyTree();
	tree.put(1, 1, "A");
	iter = tree.iterator();
	assertTrue(iter.hasNext());
	iter.next();
	iter.remove();
	assertFalse(iter.hasNext());
	assertTrue(tree.isEmpty());
	
	// test successive removes
	tree = makeEmptyTree();
	tree.put(1, 1, "A");
	tree.put(99, 99, "B");
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
    
    
    public void testContains() {
	ConcurrentQuadTree<String> tree =
		new ConcurrentQuadTree<String>(2, 0, 0, 100, 100);
	final String s = "A";
	tree.put(1, 1, s);
	assertTrue(tree.contains(1, 1));
	// try surrounding area
	assertFalse(tree.contains(1.01, 1));
	assertFalse(tree.contains(0.99, 1));
	assertFalse(tree.contains(1, 1.01));
	assertFalse(tree.contains(1, 0.99));
	
	// remove and check again
	assertNotNull(tree.remove(1, 1));
	assertFalse(tree.contains(1, 1));
	
	// try adding duplicates; this should be fine
	// because bucket size is set to 2
	tree.put(99, 99, s);
	tree.put(99, 99, s);
	assertTrue(tree.contains(99, 99));
	assertNotNull(tree.remove(99, 99));
	assertTrue(tree.contains(99, 99));
	assertNotNull(tree.remove(99, 99));
	assertFalse(tree.contains(99, 99));
    }
    
    /*--------------- test some boundary cases ----------------------*/
    

    public void testRemovingNearbyProducesNull() {
	final String s = "A";
	ConcurrentQuadTree<String> tree = makeEmptyTree();
	tree.put(1, 1, s);
	
	// Try removing nearby. Since these should never be
	// exactly equal to (1, 1), they should return null
	for (int i=0 ; i<10 ; i++) {
	    double decimal = random.nextDouble() + 0.000001;
	    assertEquals(null, tree.remove(1 + decimal, 1));
	    assertEquals(null, tree.remove(1 - decimal, 1));
	    assertEquals(null, tree.remove(1, 1 + decimal));
	    assertEquals(null, tree.remove(1, 1 - decimal));
	}
    }
    
    
}