package com.sun.sgs.test.app.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.sun.sgs.app.util.ConcurrentQuadTree;
import com.sun.sgs.app.util.QuadTreeIterator;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.app.ObjectNotFoundException;


@RunWith(NameRunner.class)
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
                ConcurrentQuadTree<String> tree = new ConcurrentQuadTree<String>(2, 0, 0, 100, 100);
                ConcurrentQuadTree<String> treeCopy = new ConcurrentQuadTree<String>(tree);
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
                QuadTreeIterator <String> treeCopyIter = treeCopy.iterator();

                while (treeCopyIter.hasNext()) {
                    String s = treeCopyIter.next();
                    assertTrue(s.equals("A") || s.equals("B") || s.equals("C") ||
                            s.equals("D") || s.equals("E"));
                    treeCopyIter.remove();
                }
                assertTrue(tree.isEmpty());
            }
        }, taskOwner);
    }

    private ConcurrentQuadTree<String> makeEmptyTree() {
        return new ConcurrentQuadTree<String>(1, 0, 0, 100, 100);
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
                getUsingRandomEntries(0, 1);
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
                getUsingRandomEntries(2, 5);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_TreeDepth3() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                getUsingRandomEntries(3, 10);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_StressTest_TreeDepth0()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                getUsingRandomEntries(0, 2);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_StressTest_TreeDepth1()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                getUsingRandomEntries(1, 7);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_StressTest_TreeDepth2()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                getUsingRandomEntries(2, 25);
            }
        }, taskOwner);
    }

    @Test
    public void testGetElementUsingCoords_StressTest_TreeDepth3()
            throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                getUsingRandomEntries(3, 100);
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
                tree.printTree(null, true);
                QuadTreeIterator<String> iter =
                        tree.pointIterator(25,25);
                int i = 0;
                while (iter.hasNext()) {
                    i++;
                    String s = iter.next();
                    assertTrue(s.equals("A") || s.equals("D")
                            );
                }
                // only "A", "D", "E" should be in this envelope
                assertEquals(2, i);
         /*  */ }
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
                        new ConcurrentQuadTree<String>(0, x1, y1, x2, y2);

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
                    fail("Expecting ObjectNotFoundException");
                } catch (ObjectNotFoundException onfe) {
                }
                iter.next();
                iter.remove();
                assertFalse(iter.hasNext());
                assertTrue(tree.isEmpty());
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
                try {
                    iter.current();
                    fail("Expecting IllegalStateException");
                } catch (IllegalStateException ise) {
                }

                // test current having not called next()
                tree = makeEmptyTree();
                tree.put(1, 1, "A");
                iter = tree.iterator();
                try {
                    iter.current();
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
                    fail("Expecting ObjectNotFoundException");
                } catch (ObjectNotFoundException onfe) {
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
                iter= tree.iterator();
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
}