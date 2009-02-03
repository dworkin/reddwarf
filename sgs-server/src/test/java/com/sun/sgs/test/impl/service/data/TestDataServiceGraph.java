/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Properties;
import java.util.Random;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test performance operating on a graph data structure, to provide a test for
 * object caching.
 */
@RunWith(NameRunner.class)
public class TestDataServiceGraph {

    /** The random seed. */
    static final long SEED = 33;

    /** The name of this application. */
    static final String APP_NAME = "TestDataServiceGraph";

    /** The length of random strings. */
    static final int STRING_LENGTH = 20;

    /** The number of operations to perform per task. */
    static final int opsPerTask = 5;

    /** The random number generator. */
    private static Random random;

    /** The number of elements to insert. */
    private static int numElements;

    /** The number of operations to perform. */
    private static int operations;

    /** The number of threads. */
    private static int numThreads;

    /** The number of read operations for each write operation. */
    private static int numReads;

    /** Set to true when the application is done. */
    private static boolean done;

    /** The server node. */
    private SgsTestNode serverNode = null;

    /** A sorted binary tree. */
    private static class Tree<E extends Comparable<E>>
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1;
	private final E value;
	private ManagedReference<Tree<E>> left;
	private ManagedReference<Tree<E>> right;
	Tree(E value) {
	    this.value = value;
	}
	E find(E value, boolean forUpdate) {
	    Tree<E> tree = this;
	    do {
		int compare = tree.value.compareTo(value);
		if (compare == 0) {
		    AppContext.getDataManager().markForUpdate(tree);
		    return tree.value;
		}
		ManagedReference<Tree<E>> childRef =
		    (compare < 0) ? tree.right : tree.left;
		tree = (childRef != null) ? childRef.get() : null;
	    } while (tree != null);
	    return null;
	}
	boolean insert(E value) {
	    Tree<E> tree = this;
	    DataManager dataManager = AppContext.getDataManager();
	    while (true) {
		int compare = tree.value.compareTo(value);
		if (compare == 0) {
		    return false;
		} else if (compare < 0) {
		    if (tree.right == null) {
			dataManager.markForUpdate(tree);
			tree.right =
			    dataManager.createReference(new Tree<E>(value));
			return true;
		    } else {
			tree = tree.right.get();
		    }
		} else {
		    if (tree.left == null) {
			dataManager.markForUpdate(tree);
			tree.left =
			    dataManager.createReference(new Tree<E>(value));
			return true;
		    } else {
			tree = tree.left.get();
		    }
		}
	    }
	}
    }

    /** A comparable object that can be stored in a {@link Tree}. */
    private static class Element implements Comparable<Element>, Serializable {
	private static final long serialVersionUID = 1;
	private final int key;
	private Value value;
	Element(int key, Value value) {
	    this.key = key;
	    this.value = value;
	}
	public int compareTo(Element element) {
	    return Integer.signum(key - element.key);
	}
	public boolean equals(Object object) {
	    return (object instanceof Element) &&
		key == ((Element) object).key;
	}
	public int hashCode() {
	    return key;
	}
	void setValue(Value newValue) {
	    value = newValue;
	}
    }

    /** A serializable value to store within an element. */
    private static class Value implements Serializable {
	private static final long serialVersionUID = 1;
	private final String s;
	private final String t;
	private transient String u;
	private final Value next;
	Value() {
	    this(4);
	}
	Value(int count) {
	    s = randomString();
	    t = randomString();
	    u = randomString();
	    next = (count > 0) ? new Value(count - 1) : null;
	}
	private void readObject(ObjectInputStream in) {
	    u = randomString();
	}
    }

    /** A task that adds random elements to the tree. */
    private static class AddElementsTask
	implements ManagedObject, Serializable, Task
    {
	private static final long serialVersionUID = 1;
	private final int[] keys;
	private int pos;
	AddElementsTask(int count) {
	    System.err.println("AddElementsTask count:" + count);
	    keys = createKeys(count);
	    AppContext.getTaskManager().scheduleTask(this);
	}
	public void run() throws Exception {
	    DataManager dataManager = AppContext.getDataManager();
	    Tree<Element> tree;
	    try {
		tree = (Tree) dataManager.getBinding("tree");
	    } catch (NameNotBoundException e) {
		tree = null;
	    }
	    for (int i = 0; i < opsPerTask; i++) {
		int key = newKey();
		if (key < 0) {
		    TreeElementTask.createTasks();
		    return;
		}
		Element element = new Element(key, new Value());
		if (tree == null) {
		    tree = new Tree<Element>(element);
		    dataManager.setBinding("tree", tree);
		} else {
		    tree.insert(element);
		}
		if (pos > 0 && pos % 100 == 0) {
		    System.err.println("AddElementsTask.run pos:" + pos);
		}
	    }
	    AppContext.getTaskManager().scheduleTask(this);
	}

	/**
	 * Creates an array of keys that represent all of the even, positive
	 * values below the specified value, arranged randomly.
	 */
	private static int[] createKeys(int max) {
	    int num = (max / 2) + 1;
	    int[] keys = new int[num];
	    for (int i = 0; i < num; i++) {
		keys[i] = i * 2;
	    }
	    /*
	     * Randomize by swapping each element for one chosen randomly from
	     * those to the right.
	     */
	    for (int i = 0; i < num - 1; i++) {
		int j = 1 + random.nextInt(num - i - 1);
		int x = keys[i];
		keys[i] = keys[j];
		keys[j] = x;
	    }
	    return keys;
	}

	private int newKey() {
	    if (pos >= keys.length) {
		return -1;
	    }
	    AppContext.getDataManager().markForUpdate(this);
	    return keys[pos++];
	}
    }

    /** A task that performs a random operation on elements in the tree. */
    private static class TreeElementTask implements Serializable, Task {
	private static final long serialVersionUID = 1;
	private final int thread;
	private int remaining;
	/** Creates a task that performs the specified number of operations. */
	private TreeElementTask(int thread, int count) {
	    this.thread = thread;
	    remaining = count;
	    System.err.println("TreeElementTask thread:" + thread +
			       ", count:" + count);
	}
	static void createTasks() {
	    TaskManager taskManager = AppContext.getTaskManager();
	    for (int i = 0; i < numThreads; i++) {
		taskManager.scheduleTask(
		    new TreeElementTask(i, operations/numThreads));
	    }
	    DataManager dataManager = AppContext.getDataManager();
	    dataManager.setBinding(
		"start", new ManagedLong(System.currentTimeMillis()));
	    dataManager.setBinding(
		"remainingTasks", new ManagedLong(numThreads));
	}
	public void run() {
	    DataManager dataManager = AppContext.getDataManager();
	    TaskManager taskManager = AppContext.getTaskManager();
	    if (remaining > 0) {
		@SuppressWarnings("unchecked")
		    Tree<Element> tree =
		    (Tree<Element>) dataManager.getBinding("tree");
		if (random.nextBoolean()) {
		    Element existing =
			tree.find(new Element(missingKey(), null), false);
		    assertNull(existing);
		} else {
		    boolean forUpdate = random.nextInt(numReads) == 0;
		    Element element =
			tree.find(new Element(presentKey(), null), forUpdate);
		    assertNotNull(element);
		    if (forUpdate) {
			element.setValue(new Value());
		    }
		}
		remaining--;
		if (remaining % 100 == 0) {
		    System.err.println("TreeElementTask.run thread:" + thread +
				       ", remaining:" + remaining);
		}
		taskManager.scheduleTask(this);
	    } else {
		ManagedLong remainingTasks = (ManagedLong)
		    dataManager.getBinding("remainingTasks");
		remainingTasks.decrement();
		if (remainingTasks.get() == 0) {
		    ManagedLong start =
			(ManagedLong) dataManager.getBinding("start");
		    long time = System.currentTimeMillis() - start.get();
		    System.err.println(
			"Elapsed time: " + time + " ms\n" +
			"Per operation: " +
			((double) time / operations) + " ms");
		    done();
		}
	    }
	}
	private static int presentKey() {
	    return random.nextInt(numElements/2) * 2;
	}
	private static int missingKey() {
	    return presentKey() + 1;
	}
    }

    private static class ManagedLong implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private long value;
	ManagedLong(long value) {
	    this.value = value;
	}
	long get() {
	    return value;
	}
	void decrement() {
	    AppContext.getDataManager().markForUpdate(this);
	    value--;
	}
    }

    /**
     * The application listener, which runs the test from its {@code
     * initialize} method.
     */
    public static class App implements AppListener, Serializable {
	private static final long serialVersionUID = 1;
	public void initialize(Properties props) {
	    new AddElementsTask(numElements);
	}
	public ClientSessionListener loggedIn(ClientSession session) {
	    return null;
	}
    }

    /** Read and print configuration properties, and create the server node. */
    @Before
    public void setUp() throws Exception {
	random = new Random(SEED);
	numElements = Integer.getInteger("test.elements", 2000);
	operations = Integer.getInteger("test.operations", 10000);
	numThreads = Integer.getInteger("test.threads", 10);
	numReads = Integer.getInteger("test.reads", 15);
	String cacheSizeProperty =
	    System.getProperty("test.cache.size", "DEFAULT");
	System.err.println(
	    "Parameters:" +
	    "\n  test.elements=" + numElements +
	    "\n  test.operations=" + operations +
	    "\n  test.threads=" + numThreads +
	    "\n  test.reads=" + numReads +
	    "\n  test.cache.size=" + cacheSizeProperty);
	Properties props =
	    SgsTestNode.getDefaultProperties(APP_NAME, null, App.class);
	props.setProperty("com.sun.sgs.impl.kernel.profile.level", "max");
	props.setProperty("com.sun.sgs.impl.kernel.profile.listeners",
			  "com.sun.sgs.impl.profile.listener." +
			  "ProfileSummaryListener");
	props.setProperty("com.sun.sgs.profile.listener.window.size", "1000");
	if (!cacheSizeProperty.equals("DEFAULT")) {
	    props.setProperty(
		DataServiceImpl.OBJECT_CACHE_SIZE_PROPERTY, cacheSizeProperty);
	}
        serverNode = new SgsTestNode(APP_NAME, null, props);
    }	

    /** Shut down the server node. */
    @After
    public void tearDown() throws Exception {
	if (serverNode != null) {
	    serverNode.shutdown(true);
	}
    }

    /** Runs the test by waiting for the application to complete. */
    @Test
    public void test() throws Exception {
	awaitDone();
    }

    /** Tells the test that the application is done. */
    static synchronized void done() {
	done = true;
	TestDataServiceGraph.class.notifyAll();
    }

    /** Waits for the application to be done. */
    private static synchronized void awaitDone() throws InterruptedException {
	while (!done) {
	    TestDataServiceGraph.class.wait();
	}
    }

    /** Returns a string with random contents. */
    static String randomString() {
	StringBuilder sb = new StringBuilder(STRING_LENGTH);
	for (int i = 0; i < STRING_LENGTH; i++) {
	    sb.append((char) ('a' + random.nextInt('z' - 'a')));
	}
	return sb.toString();
    }
}	
