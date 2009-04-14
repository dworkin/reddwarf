package com.sun.sgs.test.app.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.ConcurrentModificationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.util.ConcurrentQuadTree;
import com.sun.sgs.app.util.CurrentConcurrentRemovedException;
import com.sun.sgs.app.util.QuadTreeIterator;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;

@RunWith(FilteredNameRunner.class)
public class TestConcurrentQuadTree extends Assert {

    private static final double DELTA = Double.MIN_VALUE;
    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;
    private final Random random = new Random(System.currentTimeMillis());

    /**
     * Test management.
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        serverNode =
                new SgsTestNode("TestConcurrentQuadTree", null,
                createProps("TestConcurrentQuadTree"));
        txnScheduler =
                serverNode.getSystemRegistry().getComponent(
                TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        serverNode.shutdown(true);
    }

    private static Properties createProps(String appName) throws Exception {
        Properties props =
                SgsTestNode.getDefaultProperties(appName, null,
                SgsTestNode.DummyAppListener.class);
        props.setProperty("com.sun.sgs.txn.timeout", "10000000");
        return props;
    }

    @Test
    public void testConstructorFourArgs() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree;
                try {
                    tree = new ConcurrentQuadTree<String>(1, 0, 0, 100, 100);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(1, 0, 0, 0, 0);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(1, 0, 0, -1, -1);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
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
        }, taskOwner);
    }

    @Test
    public void testConstructorFiveArgs() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree;
                try {
                    tree = new ConcurrentQuadTree<String>(3, 0, 0, 1, 1);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(3, 1, 1, 0, 0);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(3, 0, 0, 0, 0);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(3, 0, 0, 1, 1);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
                }
                try {
                    tree = new ConcurrentQuadTree<String>(3, -1, 1, 1, -1);
                } catch (Exception e) {
                    fail("Not expecting an exception: " +
                            e.getLocalizedMessage());
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
        }, taskOwner);
    }

    public void testCopyConstructor()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                //Test copying empty tree
                ConcurrentQuadTree<String> tree =
                        new ConcurrentQuadTree<String>(2, 0, 0, 100, 100);
                ConcurrentQuadTree<String> treeCopy =
                        new ConcurrentQuadTree<String>(tree);
                assertEquals(tree.getBoundingBox()[tree.X_MAX],
                        treeCopy.getBoundingBox()[tree.X_MAX], DELTA);
                assertEquals(tree.getBoundingBox()[tree.X_MIN],
                        treeCopy.getBoundingBox()[tree.X_MIN], DELTA);
                assertEquals(tree.getBoundingBox()[tree.Y_MAX],
                        treeCopy.getBoundingBox()[tree.Y_MAX], DELTA);
                assertEquals(tree.getBoundingBox()[tree.Y_MIN],
                        treeCopy.getBoundingBox()[tree.Y_MIN], DELTA);
                assertTrue(tree.isEmpty());
                assertTrue(treeCopy.isEmpty());

                //Test copying non empty tree
                tree.put(80, 80, "A");
                tree.put(40, 40, "B");
                tree.put(10, 10, "C");
                tree.put(45, 45, "D");
                tree.put(43, 43, "E");

                treeCopy = new ConcurrentQuadTree<String>(tree);
                QuadTreeIterator<String> treeCopyIter = treeCopy.iterator();

                while (treeCopyIter.hasNext()) {
                    String s = treeCopyIter.next();
                    assertTrue(s.equals("A") || s.equals("B") || s.equals("C") || s.equals("D") || s.equals("E"));
                    treeCopyIter.remove();
                }
                assertTrue(tree.isEmpty());
            }
        }, taskOwner);
    }

    private ConcurrentQuadTree<String> makeEmptyTree() {
        return makeEmptyTree(1);
    }

    private ConcurrentQuadTree<String> makeEmptyTree(int bucketSize) {
        return new ConcurrentQuadTree<String>(bucketSize, 0, 0, 100, 100);
    }

    @Test
    public void testAdd() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
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
                assertTrue(tree.contains(0, 0));
                assertFalse(tree.isEmpty());
                // this should cause a split
                tree.put(100, 100, "B");
                assertTrue(tree.contains(100, 100));
                assertFalse(tree.isEmpty());
                // fill the children representing the other quadrants
                tree.put(100, 0, "C");
                assertTrue(tree.contains(100, 0));
                assertFalse(tree.isEmpty());
                tree.put(0, 100, "D");
                assertTrue(tree.contains(0, 100));
                assertFalse(tree.isEmpty());
                // this should cause another split (SW quadrant)
                tree.put(40, 40, "E");
                assertTrue(tree.contains(40, 40));
                assertFalse(tree.isEmpty());
                // this should cause another split (SWSW quadrant)
                tree.put(20, 20, "F");
                assertTrue(tree.contains(20, 20));
                assertFalse(tree.isEmpty());
            }
        }, taskOwner);
    }

    @Test
    public void testIsEmpty() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                assertTrue(tree.isEmpty());

                // test simple one-level add and remove
                tree.put(5, 5, "A");
                assertFalse(tree.isEmpty());
                assertTrue(tree.removeAll(5, 5));

                assertTrue(tree.isEmpty());

                // test multi-level add and removal. This addition
                // should cause the tree to go to three levels
                tree.put(1, 1, "B");
                assertFalse(tree.isEmpty());
                tree.put(99, 1, "C");
                assertFalse(tree.isEmpty());
                // begin removing
                assertTrue(tree.removeAll(1, 1));
                assertFalse(tree.isEmpty());
                assertTrue(tree.removeAll(99, 1));

                assertTrue(tree.isEmpty());
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_BucketSize1() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(1, 5);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_BucketSize2() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(2, 10);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_BucketSize3() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(3, 25);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_BucketSize4() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(4, 50);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_StressTest_BucketSize1()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(1, 100);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_StressTest_BucketSize2()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(2, 100);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_StressTest_BucketSize3()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(3, 100);
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveWithRandomEntries_StressTest_BucketSize10()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                removeUsingRandomEntries(10, 100);
            }
        }, taskOwner);
    }

    private TestContainer addToTree(final int bucketSize, final int timesToRun)
            throws Exception {
        final ConcurrentQuadTree<String> tree;

        tree = new ConcurrentQuadTree<String>(bucketSize, 0, 0, 100, 100);
        double x, y, element;
        double[][] values = new double[timesToRun][3];

        // add elements to the list, while keeping track of what they
        // were
        for (int i = 0; i < timesToRun; i++) {
            x = Math.abs(random.nextDouble() * 100);
            y = Math.abs(random.nextDouble() * 100);
            element = random.nextInt(99999);

            tree.put(x, y, Double.toString(element));
            values[i][TestContainer.X] = x;
            values[i][TestContainer.Y] = y;
            values[i][TestContainer.VALUE] = element;
        }

        return new TestContainer(tree, values);
    }

    private void removeUsingRandomEntries(final int bucketSize,
            final int timesToRun) throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                TestContainer container = addToTree(bucketSize, timesToRun);
                ConcurrentQuadTree<String> tree = container.getTree();
                double values[][] = container.getTable();

                // now remove all the elements.
                for (int i = 0; i < timesToRun; i++) {
                    double x = values[i][TestContainer.X];
                    double y = values[i][TestContainer.Y];
                    boolean b = tree.removeAll(x, y);
                    assertTrue(b);
                }
                assertTrue(tree.isEmpty());
            }
        }, taskOwner);
    }

    @Test
    public void testAddingAtSamePoint() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                // Create a tree with a bucket size larger than 1
                ConcurrentQuadTree<String> tree =
                        new ConcurrentQuadTree<String>(5, 0, 0, 100, 100);
                assertTrue(tree.isEmpty());
                final double x1 = 1.0;
                final double y1 = 2.0;
                tree.put(x1, y1, "A");
                tree.put(x1, y1, "B");
                tree.put(x1, y1, "C");

                QuadTreeIterator<String> iter = tree.iterator();
                String s;
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    s = iter.next();
                    assertTrue(s == "A" || s == "B" || s == "C");
                    assertEquals(x1, iter.currentX(), DELTA);
                    assertEquals(y1, iter.currentY(), DELTA);
                }

                //Check there is a total of 3 elements in the quadtree
                assertEquals(3, i);
                assertTrue(tree.removeAll(x1, y1));
                assertTrue(tree.isEmpty());

                // try with a few more elements this time
                final double x2 = 99;
                final double y2 = 97;
                tree.put(x2, y2, "D");
                tree.put(x2, y2, "E");
                tree.put(x2, y2, "F");
                tree.put(x2, y2, "G");
                tree.put(x2, y2, "H");
                i = 0;
                iter = tree.iterator();
                while (iter.hasNext()) {
                    i++;
                    s = iter.next();
                    assertTrue(s == "D" || s == "E" || s == "F" || s == "G" ||
                            s == "H");
                    assertEquals(x2, iter.currentX(), DELTA);
                    assertEquals(y2, iter.currentY(), DELTA);
                }

                //Check there is a total of 5 elements in the quadtree
                assertEquals(5, i);
                assertTrue(tree.removeAll(x2, y2));
                assertTrue(tree.isEmpty());
            }
        }, taskOwner);
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

    private void getUsingRandomEntries(final int maxDepth,
            final int iterations) throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                TestContainer container = addToTree(maxDepth, iterations);
                ConcurrentQuadTree<String> tree = container.getTree();
                double[][] values = container.getTable();

                for (int i = 0; i < iterations; i++) {
                    double x = values[i][TestContainer.X];
                    double y = values[i][TestContainer.Y];
                    assertTrue(tree.contains(x, y));
                }
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_TreeDepth0() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                getUsingRandomEntries(2, 2);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_TreeDepth1() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                getUsingRandomEntries(1, 2);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_TreeDepth2() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                getUsingRandomEntries(2, 9);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_TreeDepth3() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                getUsingRandomEntries(2, 33);
            }
        }, taskOwner);
    }

    @Test
    public void testEnvelopeIterator_TreeDepth0() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(50, 50, "A");
                assertFalse(tree.isEmpty());

                Iterator<String> iter =
                        tree.boundingBoxIterator(0, 0, 100, 100);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    assertEquals(iter.next(), "A");
                }
                assertEquals(1, i);
            }
        }, taskOwner);
    }

    @Test
    public void testEnvelopeIterator_TreeDepth1() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(25, 25, "A");
                tree.put(49, 51, "B");
                tree.put(51, 49, "C");
                tree.put(50, 50, "D");
                assertFalse(tree.isEmpty());
                Iterator<String> iter =
                        tree.boundingBoxIterator(0, 0, 50, 50);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    String s = iter.next();
                    assertTrue(s.equals("A") || s.equals("D"));
                }
                // only "A" and "D" should be the only ones that exist
                assertEquals(2, i);
            }
        }, taskOwner);
    }

    @Test
    public void testEnvelopeIterator_TreeDepth2() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(25, 25, "A");
                tree.put(49, 51, "B");
                tree.put(51, 49, "C");
                tree.put(50, 50, "D");
                tree.put(5, 5, "E");
                assertFalse(tree.isEmpty());

                Iterator<String> iter =
                        tree.boundingBoxIterator(0, 0, 50, 50);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    String s = iter.next();
                    assertTrue(s.equals("A") || s.equals("D") ||
                            s.equals("E"));
                }
                // only "A", "D", "E" should be in this envelope
                assertEquals(3, i);
            }
        }, taskOwner);
    }

    @Test
    public void testPointIterator() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(25, 25, "A");
                tree.put(25, 25, "D");
                tree.put(25, 35, "E");
                assertFalse(tree.isEmpty());
                QuadTreeIterator<String> iter =
                        tree.pointIterator(25, 25);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    String s = iter.next();
                    assertTrue(s.equals("A") || s.equals("D"));
                }
                // only "A", "D" should be in this envelope
                assertEquals(2, i);
            }
        }, taskOwner);
    }

    @Test
    public void testBoundingBoxIterator() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                //Test some elements on the edge of the iterator's boundingBox
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(25, 25, "A");
                tree.put(0, 0, "D");
                tree.put(35, 35, "E");
                assertFalse(tree.isEmpty());
                QuadTreeIterator<String> iter =
                        tree.boundingBoxIterator(25, 25, 0, 0);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    String s = iter.next();
                    assertTrue(s.equals("A") || s.equals("D"));
                }
                // only "A", "D" should be in this envelope
                assertEquals(2, i);

                //Test no elements inside the iterator's boundingBox
                tree = makeEmptyTree();
                tree.put(35, 0, "A");
                tree.put(0, 35, "D");
                tree.put(35, 35, "E");
                assertFalse(tree.isEmpty());
                iter = tree.boundingBoxIterator(25, 25, 0, 0);
                assertFalse(iter.hasNext());
                try {
                    iter.next();
                    fail("Expecting NoSuchElementException");
                } catch (NoSuchElementException nsee) {
                }

            }
        }, taskOwner);
    }

    @Test
    public void testGetDirectionalEnvelopeBound() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                double x1 = random.nextInt() % 100;
                double y1 = random.nextInt() % 100;
                double x2 = random.nextInt() % 100;
                double y2 = random.nextInt() % 100;
                ConcurrentQuadTree<String> tree =
                        new ConcurrentQuadTree<String>(1, x1, y1, x2, y2);

                assertEquals(
                        Math.min(x1, x2),
                        tree.getBoundingBox()[ConcurrentQuadTree.X_MIN],
                        DELTA);
                assertEquals(
                        Math.max(x1, x2),
                        tree.getBoundingBox()[ConcurrentQuadTree.X_MAX],
                        DELTA);
                assertEquals(
                        Math.min(y1, y2),
                        tree.getBoundingBox()[ConcurrentQuadTree.Y_MIN],
                        DELTA);
                assertEquals(
                        Math.max(y1, y2),
                        tree.getBoundingBox()[ConcurrentQuadTree.Y_MAX],
                        DELTA);
            }
        }, taskOwner);
    }

    /*--------------- test iterator operations ----------------------*/
    @Test
    public void testIteratorHasNext() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                Iterator<String> iter;

                // try an empty tree
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                iter = tree.iterator();
                assertFalse(iter.hasNext());

                // try with one element
                tree.put(1, 1, "A");
                iter = tree.iterator();
                assertTrue(iter.hasNext());
                iter.next();
                assertFalse(iter.hasNext());
            }
        }, taskOwner);
    }

    @Test
    public void testIteratorHasNextWithRandomElements() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                final int MAX = 10;
                ConcurrentQuadTree<String> tree;
                Iterator<String> iter;

                for (int i = 0; i < 5; i++) {
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
        }, taskOwner);
    }

    @Test
    public void testIteratorNext() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                // test with an empty tree
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                Iterator<String> iter = tree.iterator();
                try {
                    iter.next();
                    fail("Expecting NoSuchElementException");
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
                    fail("Expecting NoSuchElementException");
                } catch (NoSuchElementException nsee) {
                }
            }
        }, taskOwner);
    }

    @Test
    public void testIteratorNextNoReturn() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                // test with an empty tree
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                QuadTreeIterator<String> iter = tree.iterator();
                try {
                    iter.nextNoReturn();
                    fail("Expecting NoSuchElementException");
                } catch (NoSuchElementException nsee) {
                }

                // try with one element
                final String s = "A";
                tree = makeEmptyTree();
                tree.put(1, 1, s);
                iter = tree.iterator();
                iter.nextNoReturn();
                assertEquals(s, iter.current());
                try {
                    iter.nextNoReturn();
                    fail("Expecting NoSuchElementException");
                } catch (NoSuchElementException nsee) {
                }
            }
        }, taskOwner);
    }

    @Test
    public void testIteratorNextWithRandomElements() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                final int MAX = 10;
                ConcurrentQuadTree<String> tree;
                List<String> shadow;
                Iterator<String> iter;

                tree = makeEmptyTree();
                shadow = new ArrayList<String>();
                for (int i = 0; i < 5; i++) {
                    shadow = new ArrayList<String>();
                    tree = makeEmptyTree();
                    int count = 0;
                    int numElements = random.nextInt(MAX);

                    // add elements randomly to the tree
                    for (int j = 0; j < numElements; j++) {
                        String val = Integer.toString(j);

                        int x = random.nextInt(100);
                        int y = random.nextInt(100);
                        tree.put(x, y, val);
                        shadow.add(val);
                    }
                    count = shadow.size();
                    iter = tree.iterator();
                    // check that we can remove all items we get from the
                    // iterator
                    while (iter.hasNext()) {

                        count--;
                        String s = iter.next();
                        assertTrue(shadow.remove(s));
                        assertFalse(count < 0);
                    }
                    // check that the shadow list is empty; we should have
                    // removed
                    // all its elements
                    assertTrue(shadow.isEmpty());
                }
            }
        }, taskOwner);
    }

    @Test
    public void testIteratorRemove() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                // test with an empty tree
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                Iterator<String> iter = tree.iterator();
                try {
                    iter.remove();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                // test removing having not called next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                iter = tree.iterator();
                try {
                    iter.remove();
                    fail("Expecting IllegalStateException");
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

                // test successive removes different points
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                tree.put(99, 99, "B");

                iter = tree.iterator();
                assertTrue(iter.hasNext());
                iter.next();
                iter.remove();
                iter.next();
                iter.remove();

                assertFalse(iter.hasNext());
                assertTrue(tree.isEmpty());

                // test successive removes same point
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                tree.put(1, 1, "B");

                iter = tree.iterator();
                assertTrue(iter.hasNext());
                iter.next();
                iter.remove();
                iter.next();
                iter.remove();
                assertFalse(iter.hasNext());
                assertTrue(tree.isEmpty());

                //Test right element is removed
                tree = makeEmptyTree();
                tree.put(1, 1, "D");
                tree.put(1, 1, "A");
                tree.put(1, 1, "R");
                tree.put(1, 1, "K");
                iter = tree.iterator();
                String current = iter.next();
                iter.remove();
                iter = tree.iterator();
                int count = 0;
                while (iter.hasNext()) {
                    count++;
                    assertFalse(current.equals(iter.next()));
                }
            }
        }, taskOwner);
    }

    @Test
    public void testIteratorCurrent() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                // test with an empty tree
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                QuadTreeIterator<String> iter = tree.iterator();
                assertFalse(iter.hasCurrent());
                try {
                    iter.current();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                // test current before calling next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                iter = tree.iterator();
                try {
                    iter.current();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                //test currentX before calling next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                iter = tree.iterator();
                try {
                    iter.currentX();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }


                //test currentY before calling next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                iter = tree.iterator();
                try {
                    iter.currentY();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                // test current after calling next() and then remove()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");

                iter = tree.iterator();
                iter.next();
                iter.remove();
                try {
                    iter.current();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                // test current after calling next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");

                iter = tree.iterator();
                assertTrue(iter.hasNext());
                iter.next();
                Assert.assertEquals(iter.currentX(), 1, 0);
                Assert.assertEquals(iter.currentY(), 1, 0);
                Assert.assertEquals(iter.current(), "A");
                assertFalse(iter.hasNext());

                // test current after calling nextNoReturn()
                tree = makeEmptyTree();
                tree.put(80, 80, "B");
                iter = tree.iterator();
                assertTrue(iter.hasNext());
                iter.nextNoReturn();
                Assert.assertEquals(iter.currentX(), 80, 0);
                Assert.assertEquals(iter.currentY(), 80, 0);
                Assert.assertEquals(iter.current(), "B");
                assertFalse(iter.hasNext());
            }
        }, taskOwner);
    }

    @Test
    public void testContains() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

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
                assertTrue(tree.removeAll(1, 1));
                assertFalse(tree.contains(1, 1));

                // try adding duplicates; this should be fine
                // because bucket size is set to 2
                tree.put(99, 99, s);
                tree.put(99, 99, s);
                assertTrue(tree.contains(99, 99));
                assertTrue(tree.removeAll(99, 99));
                assertFalse(tree.contains(99, 99));

                //try point out of bounding box
                try {
                    tree.contains(1000, 1000);
                    fail("Expecting IllegalArgumentException");
                } catch (IllegalArgumentException iae) {
                }
            }
        }, taskOwner);
    }

    @Test
    public void testRemoveAll() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree =
                        new ConcurrentQuadTree<String>(4, 0, 0, 100, 100);
                final String s = "A";
                tree.put(1, 1, s);
                tree.put(1, 5, "B");
                tree.put(8, 4, "C");
                tree.put(6, 7, "D");
                assertTrue(tree.contains(6, 7));
                assertTrue(tree.removeAll(1, 5));
                // try surrounding area
                assertTrue(tree.contains(1, 1));
                assertTrue(tree.contains(6, 7));
                assertTrue(tree.contains(8, 4));

            }
        }, taskOwner);
    }

    /*--------------- test some boundary cases ----------------------*/
    @Test
    public void testRemovingNearbyProducesNull() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                final String s = "A";
                ConcurrentQuadTree<String> tree = makeEmptyTree();
                tree.put(1, 1, s);

                // Try removing nearby. Since these should never be
                // exactly equal to (1, 1), they should return null
                for (int i = 0; i < 10; i++) {
                    double decimal = random.nextDouble() + 0.000001;
                    assertFalse(tree.removeAll(1 + decimal, 1));
                    assertFalse(tree.removeAll(1 - decimal, 1));
                    assertFalse(tree.removeAll(1, 1 + decimal));
                    assertFalse(tree.removeAll(1, 1 - decimal));
                }

            }
        }, taskOwner);
    }

    /*--------------- Serialization Test Cases ----------------------*/
    private byte[] serialize(Iterator iter) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(iter);
        return baos.toByteArray();
    }

    private QuadTreeIterator deserialize(byte[] serializedForm)
            throws Exception {
        ByteArrayInputStream bais =
                new ByteArrayInputStream(serializedForm);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (QuadTreeIterator) ois.readObject();
    }

    private QuadTreeIterator serializeDeserializeIterator(Iterator iter) throws
            Exception {
        return deserialize(serialize(iter));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorSerialization()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {

                        //Test empty tree
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        QuadTreeIterator iter = tree.iterator();
                        iter = serializeDeserializeIterator(iter);
                        assertFalse(iter.hasNext());

                        //Test 2 elements, serialize after iterating through 1
                        tree = makeEmptyTree();
                        tree.put(25, 25, "D");
                        tree.put(50, 50, "A");
                        ArrayList<String> shadow = new ArrayList<String>();
                        shadow.add("D");
                        shadow.add("A");

                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        iter = serializeDeserializeIterator(iter);
                        assertFalse(shadow.contains(iter.current()));
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());

                        //Test serialization of tree with nodes that contain
                        //multiple points
                        tree = makeEmptyTree(2);
                        tree.put(1, 1, "D");
                        tree.put(2, 2, "A");

                        shadow = new ArrayList<String>();
                        shadow.add("D");
                        shadow.add("A");

                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        iter = serializeDeserializeIterator(iter);
                        assertFalse(shadow.contains(iter.current()));
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());

                        //Test consistency after serializing and deserializing
                        //3 times
                        tree = makeEmptyTree();
                        tree.put(25, 25, "D");
                        tree.put(50, 50, "A");
                        shadow.add("D");
                        shadow.add("A");

                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        iter = serializeDeserializeIterator(iter);
                        iter = serializeDeserializeIterator(iter);
                        iter = serializeDeserializeIterator(iter);
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());

                        //Test serialize after removing current element
                        tree = makeEmptyTree();
                        tree.put(25, 25, "D");
                        tree.put(25, 25, "A");
                        shadow.add("D");
                        shadow.add("A");

                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        iter.remove();
                        iter = serializeDeserializeIterator(iter);
                        try {
                            iter.current();
                            fail("Expecting IllegalStateException");
                        } catch (IllegalStateException ise) {
                        }
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());
                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorSerializationModify()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {

                        //Test empty tree
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        QuadTreeIterator iter = tree.iterator();
                        ArrayList<String> shadow = new ArrayList<String>();

                        //Test removing current element the iterator was on
                        //while iterator is serialized
                        tree.put(25, 25, "D");
                        tree.put(50, 50, "A");
                        shadow.add("D");
                        shadow.add("A");
                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        byte[] serializedForm = serialize(iter);
                        iter.remove();
                        iter = deserialize(serializedForm);
                        assertFalse(iter.hasCurrent());
                        try {
                            iter.current();
                            fail("Expecting CurrentConcurrentRemovedException");
                        } catch (CurrentConcurrentRemovedException ccre) {
                        }
                        assertTrue(shadow.remove(iter.next()));

                        //Test removing the next element while iterator is
                        //serialized
                        tree = makeEmptyTree();
                        tree.put(25, 25, "D");
                        tree.put(50, 50, "A");
                        tree.put(75, 75, "R");
                        shadow.add("D");
                        shadow.add("A");
                        shadow.add("R");
                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        serializedForm = serialize(iter);
                        assertTrue(shadow.remove(iter.next()));
                        iter.remove();
                        iter = deserialize(serializedForm);
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());

                        //Test adding a new next element while iterator is
                        //serialized
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(99, 99, "R");
                        shadow.add("D");
                        shadow.add("A");
                        shadow.add("R");
                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        serializedForm = serialize(iter);
                        tree.put(80, 40, "A");
                        iter = deserialize(serializedForm);
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.isEmpty());

                        //Test splitting the leaf the iterator was on
                        //when it is serialized
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "R");
                        iter = tree.iterator();
                        iter.next();
                        serializedForm = serialize(iter);
                        tree.put(80, 40, "A");
                        iter = deserialize(serializedForm);
                        try {
                            iter.next();
                            fail("Expecting ConcurrentModificaitonException " +
                                    "cme");
                        } catch (ConcurrentModificationException cme) {
                        }
                    }
                }, taskOwner);
    }

    /*--------------- Concurrent Iterator Test Cases ----------------------*/
    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorConcurrentCurrentElementRemoved()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {
                        //Test getting next element in list when current
                        //element is removed
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "A");
                        QuadTreeIterator iter = tree.iterator();
                        iter.next();
                        iter.remove();
                        assertEquals(iter.next(), "A");

                        //Try removing current element when it is the last
                        //element left in the iteration
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "A");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "D");
                        assertEquals(iter.next(), "A");
                        iter.remove();
                        assertFalse(iter.hasNext());

                        //Test getting next element in the list of a
                        //different point when current element is removed
                        tree = makeEmptyTree(2);
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "A");
                        tree.put(2, 2, "R");

                        iter = tree.iterator();
                        Iterator iter2 = tree.iterator();
                        assertEquals(iter.next(), "D");
                        assertEquals(iter.next(), "A");
                        while (!"A".equals(iter2.next())) {
                        }
                        iter2.remove();
                        assertFalse(iter.hasCurrent());
                        assertEquals(iter.next(), "R");

                        //Test getting next element in the list of a
                        //different point on a different leaf when current
                        //element is removed
                        tree = makeEmptyTree();
                        tree.put(99, 99, "D");
                        tree.put(99, 99, "A");
                        tree.put(2, 2, "R");
                        iter = tree.iterator();
                        iter2 = tree.iterator();
                        assertEquals(iter.next(), "D");
                        assertEquals(iter.next(), "A");
                        while (!"A".equals(iter2.next())) {
                        }
                        iter2.remove();
                        assertFalse(iter.hasCurrent());
                        assertEquals(iter.next(), "R");

                        //Test changing the index of the current element while
                        //iterator is serialized
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "A");
                        tree.put(1, 1, "R");
                        tree.put(1, 1, "K");
                        iter = tree.iterator();
                        while (!"R".equals(iter.next())) {
                        }

                        //Use a different iterator to remove elements
                        //before and after "R" in the list of elements at (1,1)
                        iter2 = tree.iterator();
                        iter2.next();
                        iter2.remove();
                        while (!"R".equals(iter2.next())) {
                        }
                        iter2.next();
                        iter2.remove();

                        //Add some new elements after "R" in the list of
                        //elements at (1,1)
                        tree.put(1, 1, "S");
                        tree.put(1, 1, "T");
                        assertEquals(iter.current(), "R");
                        assertEquals(iter.next(), "S");
                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorConcurrentCurrentPointRemoved()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {
                        //Test getting next element in a different point
                        //when current point
                        ConcurrentQuadTree<String> tree = makeEmptyTree(2);
                        tree.put(1, 1, "D");
                        tree.put(2, 2, "A");
                        QuadTreeIterator iter = tree.iterator();
                        iter.next();
                        iter.remove();
                        assertEquals(iter.next(), "A");

                        //Try removing current point when it is the last
                        //element left in the iteration
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(1, 1, "A");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "D");
                        iter.remove();
                        assertEquals(iter.next(), "A");
                        iter.remove();
                        assertFalse(iter.hasNext());

                        //Test getting next element in the list of a
                        //different point on a different leaf when current
                        //point is removed
                        tree = makeEmptyTree(2);
                        tree.put(70, 80, "D");
                        tree.put(99, 99, "A");
                        tree.put(2, 2, "R");
                        iter = tree.iterator();
                        QuadTreeIterator iter2 = tree.iterator();
                        assertEquals(iter.next(), "D");
                        assertEquals(iter.next(), "A");
                        while (!"A".equals(iter2.next())) {
                        }
                        iter2.remove();
                        assertFalse(iter.hasCurrent());
                        assertEquals(iter.next(), "R");
                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorConcurrentCurrentLeafRemoved()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {
                        //Test getting next element when current node
                        //has been removed and its parent node has become
                        //a leaf node
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        //Create a parent node whose children are "A", "R", "K"
                        //and a sibling leaf node "D"
                        tree.put(1, 1, "D");
                        tree.put(70, 80, "A");
                        tree.put(99, 99, "R");
                        QuadTreeIterator iter = tree.iterator();
                        iter = tree.iterator();

                        while (!"R".equals(iter.next())) {
                            iter.remove();
                        }
                        iter.remove();
                        assertTrue(iter.hasNext());
                        assertEquals(iter.next(), "D");

                        //Test getting next element when current node
                        //has been removed and its parent node still has
                        //children
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(70, 80, "A");
                        tree.put(99, 99, "R");
                        tree.put(80, 70, "K");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "A");
                        assertEquals(iter.next(), "R");
                        iter.remove();
                        assertEquals(iter.next(), "K");
                        iter.remove();
                        assertEquals(iter.next(), "D");

                        //Test removing current leaf and there are no more
                        //elements left to iterate over
                        tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(70, 80, "A");
                        tree.put(99, 99, "R");
                        tree.put(80, 70, "K");
                        iter = tree.iterator();
                        while (!"D".equals(iter.next())) {
                        }
                        iter.remove();
                        assertFalse(iter.hasNext());
                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorConcurrentNextElementRemoval()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {

                        //Test removing the next leaf node
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(45, 45, "A");
                        tree.put(99, 99, "R");
                        QuadTreeIterator iter = tree.iterator();
                        assertEquals(iter.next(), "R");
                        assertTrue(iter.hasNext());
                        tree.removeAll(45, 45);
                        assertEquals(iter.next(), "D");

                        //Test removing the next point
                        tree = makeEmptyTree(2);
                        tree.put(99, 99, "D");
                        tree.put(98, 98, "A");
                        tree.put(1, 1, "R");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "A");
                        assertTrue(iter.hasNext());
                        QuadTreeIterator iter2 = tree.iterator();
                        while (!"D".equals(iter2.next())) {
                        }
                        iter2.remove();
                        assertEquals(iter.next(), "R");

                        //Test removing the next element in a list of
                        //elements corresponding to a point
                        tree = makeEmptyTree();
                        tree.put(99, 99, "D");
                        tree.put(99, 99, "A");
                        tree.put(1, 1, "R");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "D");
                        assertTrue(iter.hasNext());
                        iter2 = tree.iterator();
                        while (!"A".equals(iter2.next())) {
                        }
                        iter2.remove();
                        assertEquals(iter.next(), "R");

                        //Test removing the next element which is
                        //also the last element
                        tree = makeEmptyTree();
                        tree.put(99, 99, "D");
                        tree.put(99, 99, "A");
                        iter = tree.iterator();
                        iter.next();
                        assertTrue(iter.hasNext());
                        iter2 = tree.iterator();
                        iter2.next();
                        iter2.next();
                        iter2.remove();
                        assertFalse(iter.hasNext());
                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorEdgeCases()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {

                        ArrayList<String> shadow = new ArrayList<String>();
                        //Test adding a new next leaf node
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        tree.put(99, 99, "R");
                        shadow.add("D");
                        shadow.add("A");
                        shadow.add("R");

                        QuadTreeIterator iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(iter.hasNext());
                        tree.put(45, 45, "A");
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.remove(iter.next()));

                        //Test adding a new next point on the same leaf
                        //as the current point
                        tree = makeEmptyTree(2);
                        tree.put(98, 98, "D");
                        tree.put(45, 45, "A");
                        tree.put(1, 1, "R");
                        shadow.add("D");
                        shadow.add("A");
                        shadow.add("R");
                        shadow.add("K");
                        iter = tree.iterator();
                        assertTrue(shadow.remove(iter.next()));
                        tree.put(99, 99, "K");
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.remove(iter.next()));
                        assertTrue(shadow.remove(iter.next()));

                    }
                }, taskOwner);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIteratorConcurrentExceptions()
            throws Exception {
        txnScheduler.runTask(
                new TestAbstractKernelRunnable() {

                    public void run() throws Exception {
                        //Test if the original node split while iterator is
                        //serialized
                        ConcurrentQuadTree<String> tree = makeEmptyTree();
                        tree.put(1, 1, "D");
                        QuadTreeIterator iter = tree.iterator();
                        assertEquals(iter.next(), "D");
                        byte[] serializedForm = serialize(iter);
                        tree.put(2, 2, "A");
                        iter = deserialize(serializedForm);
                        try {
                            iter.next();
                            fail("Expecting ConcurrentModificationException");
                        } catch (ConcurrentModificationException cme) {
                        }

                        //Test having concurrently removing the current element
                        //the iterator is on
                        tree = makeEmptyTree();
                        tree.put(1, 1, "A");
                        iter = tree.iterator();
                        assertEquals(iter.next(), "A");
                        assertTrue(iter.hasCurrent());
                        tree.removeAll(1, 1);
                        assertFalse(iter.hasCurrent());
                        try {
                            iter.current();
                            fail("Expecting CurrentConcurrentRemovedException");
                        } catch (CurrentConcurrentRemovedException ccre) {
                        }
                    }
                }, taskOwner);
    }


    /*--------------- Tests involving Multiple Transactions -----------------*/
    /**
     * Casts the object to the desired type in order to avoid unchecked cast
     * warnings
     *
     * @param <T> the type to cast to
     * @param object the object to cast
     * @return the casted version of the object
     */
    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    /**
     * Tests retrieving the tree after adding elements
     *
     * @throws Exception
     */
    @Test
    public void testQuadTreeAddingTransactions()
            throws Exception {
        final String name = "testConcurrentQuadTree";
        final ArrayList<String> xValue = new ArrayList<String>();
        final ArrayList<String> yValue = new ArrayList<String>();
        final ArrayList<String> value = new ArrayList<String>();

        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree = makeEmptyTree();
                for (int i = 0; i < 10; ++i) {
                    int x = random.nextInt(100);
                    int y = random.nextInt(100);
                    xValue.add(Integer.toString(x));
                    yValue.add(Integer.toString(y));
                    value.add(Integer.toString(i));
                    tree.put(x, y, Integer.toString(i));
                }
                AppContext.getDataManager().setBinding(name, tree);
            }
        }, taskOwner);

        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {

                ConcurrentQuadTree<String> tree =
                        uncheckedCast((AppContext.getDataManager().getBinding(name)));
                QuadTreeIterator iter = tree.iterator();
                while (iter.hasNext()) {
                    int index = value.indexOf(iter.next());
                    assertTrue(index >= 0);
                    assertEquals(xValue.get(index), Integer.toString((int) iter.currentX()));
                    assertEquals(yValue.get(index), Integer.toString((int) iter.currentY()));
                }

                AppContext.getDataManager().removeBinding(name);
                AppContext.getDataManager().removeObject(tree);
            }
        }, taskOwner);
    }

    private int getObjectCount() throws Exception {
        GetObjectCountTask task = new GetObjectCountTask();
        txnScheduler.runTask(task, taskOwner);
        return task.count;
    }

    private class GetObjectCountTask extends TestAbstractKernelRunnable {

        volatile int count = 0;

        GetObjectCountTask() {
        }

        public void run() {
            count = 0;
            BigInteger last = null;
            while (true) {
                BigInteger next = dataService.nextObjectId(last);
                if (next == null) {
                    break;
                }
                // NOTE: this count is used at the end of the test to make
                // sure
                // that no objects were leaked in stressing the structure but
                // any given service (e.g., the task service) may accumulate
                // managed objects, so a more general way to exclude these
                // from
                // the count would be nice but for now the specific types that
                // are accumulated get excluded from the count
                ManagedReference<?> ref =
                        dataService.createReferenceForId(next);
                String name = ref.get().getClass().getName();
                if (!name.equals("com.sun.sgs.impl.service.task.PendingTask")) {
                    if (name.equals("com.sun.sgs.app.util.ManagedSerializable")) {
                        // System.err.println(count + ": " + ref.get());
                        count++;
                    }
                }
                last = next;
            }
        }
    }

    /**
     * Test clearing and removal {@code ConcurrentQuadTree}
     */
    @Test
    public void testClearLeavesNoArtifactsBucketSize1() throws Exception {
        coreClearTest(5, 1);
    }

    @Test
    public void testClearLeavesNoArtifactsBucketSize2() throws Exception {
        coreClearTest(10, 2);
    }

    @Test
    public void testClearLeavesNoArtifactsRoot() throws Exception {
        coreClearTest(3, 3);
    }

    @Test
    public void testClearLeavesNoArtifactsStress() throws Exception {
        coreClearTest(100, 3);
    }

    /**
     * Test clearing and removal {@code ConcurrentQuadTree}
     */
    @Test
    public void testRemovalLeavesNoArtifacts() throws Exception {
        coreRemovalTest(5, 2);
    }

    @Test
    public void testRemovalLeavesNoArtifactsRoot() throws Exception {
        coreRemovalTest(3, 3);
    }

    @Test
    public void testClearLeavesNoArtifactsStress1() throws Exception {
        coreClearTest(0, 1);
    }

    /**
     * Method which can be reused to test clearing of a given number of items
     *
     * @param elementsToAdd the number of elements to add
     * @param bucketSize    the bucketSize of the tree which will be used in
     *                      the test
     */
    private void coreRemovalTest(final int elementsToAdd, final int bucketSize)
            throws Exception {
        final String name =
                "tree" + Long.toString(System.currentTimeMillis());


        int countBeforeCreate = getObjectCount();
        System.err.println("countBeforeCreate: " + countBeforeCreate);
        // create quad tree
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                ConcurrentQuadTree<String> tree = makeEmptyTree(bucketSize);
                AppContext.getDataManager().setBinding(name, tree);

            }
        }, taskOwner);
        // randomly add some objects
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() {
                ConcurrentQuadTree<String> tree =
                        uncheckedCast(AppContext.getDataManager().getBinding(
                        name));
                for (int i = 0; i < elementsToAdd; i++) {
                    int x = random.nextInt(100);
                    int y = random.nextInt(100);
                    tree.put(x, y, Integer.toString(i));
                }
            }
        }, taskOwner);
        int countAfterAdds = getObjectCount();
        System.err.println("countAfterAdds: " + countAfterAdds);

        // clear the quad tree
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() {
                DataManager dm = AppContext.getDataManager();
                ConcurrentQuadTree<Integer> tree =
                        uncheckedCast(dm.getBinding(name));
                dm.removeObject(tree);
                dm.removeBinding(name);
            }
        }, taskOwner);

        // removal is asynchronous, so wait. When we compare, there should
        // be as many objects as there were immediately after the tree
        // was created.
        Thread.sleep(50 * elementsToAdd);
        int countAfterClear = getObjectCount();
        System.err.println("countAfterClear: " + countAfterClear);
        assertEquals(countBeforeCreate, countAfterClear);
        Thread.sleep(100);
    }

    /**
     * Method which can be reused to test clearing of a given number of items
     *
     * @param elementsToAdd the number of elements to add
     * @param bucketSize    the bucketSize of the tree which will be used in
     *                      the test
     */
    private void coreClearTest(final int elementsToAdd, final int bucketSize)
            throws Exception {
        final String name =
                "tree" + Long.toString(System.currentTimeMillis());

        // create quad tree
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() throws Exception {
                ConcurrentQuadTree<String> tree = makeEmptyTree(bucketSize);
                AppContext.getDataManager().setBinding(name, tree);

            }
        }, taskOwner);

        int countAfterCreate = getObjectCount();
        System.err.println("countAfterCreate: " + countAfterCreate);
        // randomly add some objects
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() {
                ConcurrentQuadTree<String> tree =
                        uncheckedCast(AppContext.getDataManager().getBinding(
                        name));
                for (int i = 0; i < elementsToAdd; i++) {
                    int x = random.nextInt(100);
                    int y = random.nextInt(100);
                    tree.put(x, y, Integer.toString(i));
                }
                System.err.println("Before");
            }
        }, taskOwner);
        int countAfterAdds = getObjectCount();
        System.err.println("countAfterAdds: " + countAfterAdds);

        // clear the quad tree
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() {
                DataManager dm = AppContext.getDataManager();
                ConcurrentQuadTree<Integer> tree =
                        uncheckedCast(dm.getBinding(name));
                tree.clear();
            }
        }, taskOwner);

        // removal is asynchronous, so wait. When we compare, there should
        // be as many objects as there were immediately after the tree
        // was created.
        Thread.sleep(50 * elementsToAdd);
        int countAfterClear = getObjectCount();
        System.err.println("countAfterClear: " + countAfterClear);
        assertEquals(countAfterCreate, countAfterClear);

        //Delete object
        txnScheduler.runTask(new TestAbstractKernelRunnable() {

            public void run() {
                DataManager dm = AppContext.getDataManager();
                ConcurrentQuadTree<Integer> tree =
                        uncheckedCast(dm.getBinding(name));
                System.err.println("After");
                dm.removeObject(tree);
                dm.removeBinding(name);
            }
        }, taskOwner);
        Thread.sleep(100);
    }
}