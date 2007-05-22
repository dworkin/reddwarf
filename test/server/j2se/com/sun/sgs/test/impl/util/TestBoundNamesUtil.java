package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import junit.framework.TestCase;

/** Test the BoundNamesUtil class. */
public class TestBoundNamesUtil extends TestCase {

    /** A data service to supply service name bindings. */
    private DummyDataService service;

    /** Creates an instance. */
    public TestBoundNamesUtil(String name) {
	super(name);
    }

    /** Prints the test case and sets the service field to a new instance. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	service = new DummyDataService();
    }

    /* -- Tests -- */

    /* -- Test getServiceBoundNamesIterable -- */

    public void testGetServiceBoundNamesIterableNullArgs() {
	try {
	    BoundNamesUtil.getServiceBoundNamesIterable(null, "");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    BoundNamesUtil.getServiceBoundNamesIterable(service, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetServiceBoundNamesIterableNoMatch() {
	testNoMatch(new CheckIterable());
    }

    public void testGetServiceBoundNamesIterableMatch() {
	testMatch(new CheckIterable());
    }

    /* -- Test getServiceBoundNamesIterator -- */

    public void testGetServiceBoundNamesIteratorNullArgs() {
	try {
	    BoundNamesUtil.getServiceBoundNamesIterator(null, "");
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    BoundNamesUtil.getServiceBoundNamesIterator(service, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetServiceBoundNamesIteratorNoMatch() {
	testNoMatch(new CheckIterator());
    }

    public void testGetServiceBoundNamesIteratorMatch() {
	testMatch(new CheckIterator());
    }

    public void testGetServiceBoundNamesRemoveEmpty() {
	testRemoveEmpty("");
	testRemoveEmpty("able", "able");
	testRemoveEmpty("able", "baker", "charlie");
	testRemoveEmpty("bob", "baker", "charlie");
	testRemoveEmpty("don", "baker", "charlie");
    }

    /**
     * Test that an iterator created with the specified prefix on a service
     * with the specified names, which should result in an iteration that
     * returns no names, responds properly to the remove method.
     */
    private void testRemoveEmpty(String prefix, String... names) {
	List<String> list = Arrays.asList(names);
	service.clear();
	service.addAll(list);
	Iterator<String> iter =
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix);
	assertRemoveFails(iter);
	assertNextFails(iter);
	assertRemoveFails(iter);
    }	

    public void testGetServiceBoundNamesRemoveOneElement() {
	testRemoveOneElement("", "ann");
	testRemoveOneElement("ann", "anne");
	testRemoveOneElement("ann", "ann", "anne");
	testRemoveOneElement("ann", "anne", "bill");
	testRemoveOneElement("bob", "ann", "bobby", "charlie");
	testRemoveOneElement("c", "baker", "charlie");
    }

    /**
     * Test that an iterator created with the specified prefix on a service
     * with the specified names, which should result in an iteration that
     * returns one name, responds properly to the remove method.
     */
    private void testRemoveOneElement(String prefix, String... names) {
	List<String> list = Arrays.asList(names);
	service.clear();
	service.addAll(list);
	Iterator<String> iter =
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix);
	assertRemoveFails(iter);
	iter.next();
	assertNextFails(iter);
	iter.remove();
	assertRemoveFails(iter);
	assertNextFails(iter);
	assertContents(
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix));
    }	

    public void testGetServiceBoundNamesRemoveTwoElements() {
	testRemoveTwoElements("", "ann", "bill");
	testRemoveTwoElements("ann", "anne", "annie");
	testRemoveTwoElements("ann", "ann", "anne", "annie", "bob");
    }

    /**
     * Test that an iterator created with the specified prefix on a service
     * with the specified names, which should result in an iteration that
     * returns two names, responds properly to the remove method.
     */
    private void testRemoveTwoElements(String prefix, String... names) {
	List<String> list = Arrays.asList(names);
	service.clear();
	service.addAll(list);
	List<String> matchingNames = new ArrayList<String>();
	for (String name :
		 BoundNamesUtil.getServiceBoundNamesIterable(service, prefix))
	{
	    matchingNames.add(name);
	}
	Iterator<String> iter =
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix);
	assertRemoveFails(iter);
	/* Remove first */
	iter.next();
	iter.remove();
	assertRemoveFails(iter);
	assertContents(
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix),
	    matchingNames.get(1));
	/* Also remove second */
	iter.next();
	iter.remove();
	assertRemoveFails(iter);
	assertContents(
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix));
	service.clear();
	service.addAll(list);
	iter = BoundNamesUtil.getServiceBoundNamesIterator(service, prefix);
	/* Just remove second */
	iter.next();
	iter.next();
	assertNextFails(iter);
	iter.remove();
	assertRemoveFails(iter);
	assertContents(
	    BoundNamesUtil.getServiceBoundNamesIterator(service, prefix),
	    matchingNames.get(0));
    }	

    /* -- Other methods and classes -- */

    /** Checks that the iterator supplies the specified names. */
    static void assertContents(Iterator<String> iter, String... names) {
	for (String name : names) {
	    assertTrue("Name not found: " + name, iter.hasNext());
	    try {
		assertEquals("Wrong name found: ", name, iter.next());
	    } catch (NoSuchElementException e) {
		fail("Name not found: " + name);
	    }
	}
	assertNextFails(iter);
    }

    /**
     * Checks that calling remove on the iterator throws IllegalStateException.
     */
    static void assertRemoveFails(Iterator<String> iter) {
	try {
	    iter.remove();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /**
     * Checks that calling next on the iterator throws NoSuchElementException.
     */
    static void assertNextFails(Iterator<String> iter) {
	try {
	    String n = iter.next();
	    fail("Unexpected next element: " + n);
	} catch (NoSuchElementException e) {
	    System.err.println(e);
	}
	assertFalse("Unexpected next element", iter.hasNext());
    }

    /** Tests iterations that return no names. */
    private void testNoMatch(CheckIterator check) {
	check.checkContents("");
	service.addAll(Arrays.asList("bill", "bob"));
	check.checkContents("a");
	check.checkContents("ba");
	check.checkContents("billy");
	check.checkContents("bit");
	check.checkContents("boa");
	check.checkContents("bob");
	check.checkContents("bobby");
	check.checkContents("carl");
    }

    /** Tests iterations that return names. */
    private void testMatch(CheckIterator check) {
	service.addAll(
	    Arrays.asList("able", "al", "alan", "alex", "ann", "bill",
			  "billy", "bob", "bobby"));
	check.checkContents("", "able", "al", "alan", "alex", "ann", "bill",
			    "billy", "bob", "bobby");
	check.checkContents("a", "able", "al", "alan", "alex", "ann");
	check.checkContents("al", "alan", "alex");
	check.checkContents("ale", "alex");
	check.checkContents("alex");
	check.checkContents("b", "bill", "billy", "bob", "bobby");
	check.checkContents("bill", "billy");
	check.checkContents("billy");
    }

    /** Defines a class for checking the contents of an iterator. */
    class CheckIterator {
	/**
	 * Checks that the contents of an iterator created with the specified
	 * prefix returns the specified names.  This implementation creates an
	 * Iterator directly.
	 */
	void checkContents(String prefix, String... names) {
	    assertContents(
		BoundNamesUtil.getServiceBoundNamesIterator(service, prefix),
		names);
	}
    }

    /**
     * Defines a class for checking the contents of iterators returned by an
     * iterable.
     */
    class CheckIterable extends CheckIterator {
	/** This implementation creates an Iterable. */
	void checkContents(String prefix, String... names) {
	    Iterable<String> ible =
		BoundNamesUtil.getServiceBoundNamesIterable(service, prefix);
	    assertContents(ible.iterator(), names);
	    assertContents(ible.iterator(), names);
	}
    }

    /**
     * A DataService that only really implements nextServiceBoundName and
     * removeServiceBinding, and that is a sorted set of names which represent
     * the service bindings.
     */
    private static class DummyDataService
	extends TreeSet<String>
	implements DataService
    {
	private static final long serialVersionUID = 1;
	/* -- Stubs for DataManager -- */
	public <T> T getBinding(String name, Class<T> type) { return null; }
	public void setBinding(String name, ManagedObject object) { }
	public void removeBinding(String name) { }
	public String nextBoundName(String name) { return null; }
	public void removeObject(ManagedObject object) { }
	public void markForUpdate(ManagedObject object) { }
	public ManagedReference createReference(ManagedObject object) {
	    return null;
	}
	/* -- Stubs for DataService -- */
	public <T> T getServiceBinding(String name, Class<T> type) {
	    return null;
	}
	public void setServiceBinding(String name, ManagedObject object) { }
	public ManagedReference createReferenceForId(BigInteger id) {
	    return null;
	}
	/* -- Stubs for Service -- */
	public String getName() { return null; }
	public void configure(
	    ComponentRegistry registry, TransactionProxy proxy)
	{ }
	public boolean shutdown() { return false; }
	/** Get the next name from the set. */
	public String nextServiceBoundName(String name) {
	    if (name == null) {
		try {
		    return first();
		} catch (NoSuchElementException e) {
		    return null;
		}
	    } else {
		Iterator<String> iter = tailSet(name).iterator();
		if (iter.hasNext()) {
		    String n = iter.next();
		    if (!n.equals(name)) {
			return n;
		    } else if (iter.hasNext()) {
			return iter.next();
		    }
		}
		return null;
	    }
	}
	/** Remove the name from the set. */
	public void removeServiceBinding(String name) {
	    remove(name);
	}
    }
}
