/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.AbstractAccessCoordinator;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import com.sun.sgs.impl.kernel.NullAccessCoordinator;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests configuration the kernel to specify an access coordinator. */
@RunWith(FilteredNameRunner.class)
public class TestKernelSetAccessCoordinator extends Assert {

    /** The configuration property for specifying the access coordinator. */
    private static final String ACCESS_COORDINATOR_PROPERTY =
	"com.sun.sgs.impl.kernel.access.coordinator";

    /** The configuration properties. */
    private Properties properties;

    @Before
    public void before() throws Exception {
	properties = SgsTestNode.getDefaultProperties(
	    "TestKernelSetAccessCoordinator", null, null);
        properties.setProperty("com.sun.sgs.finalService", "DataService");
    }

    /* -- Tests -- */

    @Test
    public void testClassNameInvalid() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY, "34 skidoo");
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassNotFound() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       "ThisClassIsNotFound");
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassNotPublic() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       NonPublicCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassAbstract() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       AbstractCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassNotImplementing() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       NotImplementingCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassMissingConstructor() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       MissingConstructorCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassNonPublicConstructor() throws Exception {
	properties.setProperty(
	    ACCESS_COORDINATOR_PROPERTY,
	    NonPublicConstructorCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testClassNotHandle() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       NotHandleCoordinator.class.getName());
	try {
	    new SgsTestNode(
		"TestKernelSetAccessCoordinator", null, properties);
	    fail("Expected InvocationTargetException");
	} catch (InvocationTargetException e) {
	    Throwable cause = e.getCause();
	    assertTrue("Expected IllegalArgumentException: " + cause,
		       cause instanceof IllegalArgumentException);
	}
    }

    @Test
    public void testNullCoordinator() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       NullAccessCoordinator.class.getName());
	SgsTestNode node = new SgsTestNode(
	    "TestKernelSetAccessCoordinator", null, properties);
	node.shutdown(false);
    }

    @Test
    public void testLockingAccessCoordinator() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       LockingAccessCoordinator.class.getName());
	SgsTestNode node = new SgsTestNode(
	    "TestKernelSetAccessCoordinator", null, properties);
	node.shutdown(false);
    }

    @Test
    public void testMyAccessCoordinator() throws Exception {
	properties.setProperty(ACCESS_COORDINATOR_PROPERTY,
			       MyAccessCoordinator.class.getName());
	SgsTestNode node = new SgsTestNode(
	    "TestKernelSetAccessCoordinator", null, properties);
        Identity taskOwner = node.getProxy().getCurrentOwner();
        TransactionScheduler txnScheduler =
	    node.getSystemRegistry().getComponent(TransactionScheduler.class);
	final DataService dataService = node.getDataService();
	txnScheduler.runTask(
	    new TestAbstractKernelRunnable() {
		public void run() throws Exception {
		    dataService.setBinding("a", new DummyManagedObject());
		}
	    }, taskOwner);
	assertTrue(MyAccessCoordinator.getUsed());
	node.shutdown(false);
    }

    /* -- Other classes and methods -- */

    public abstract static class AbstractCoordinator
	extends NullAccessCoordinator
    {
	public AbstractCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	    super(properties, txnProxy, profileCollectorHandle);
	}
    }

    static class NonPublicCoordinator extends NullAccessCoordinator {
	public NonPublicCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	    super(properties, txnProxy, profileCollectorHandle);
	}
    }

    public static class NotImplementingCoordinator {
	public NotImplementingCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	}
    }

    public static class MissingConstructorCoordinator
	extends NullAccessCoordinator
    {
	public MissingConstructorCoordinator() {
	    super(null, null, null);
	}
    }

    public static class NonPublicConstructorCoordinator
	extends NullAccessCoordinator
    {
	NonPublicConstructorCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	    super(properties, txnProxy, profileCollectorHandle);
	}
    }

    public static class NotHandleCoordinator
	implements AccessCoordinator
    {
	NotHandleCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	}
	public <T> AccessReporter<T> registerAccessSource(
	    String sourceName, Class<T> objectIdType)
	{
	    return null;
	}
	public Transaction getConflictingTransaction(Transaction txn) {
	    return null;
	}
    }

    /** Records whether its methods were called. */
    public static class MyAccessCoordinator extends AbstractAccessCoordinator {
	private static boolean used;

	public MyAccessCoordinator(
	    Properties properties,
	    TransactionProxy txnProxy,
	    ProfileCollectorHandle profileCollectorHandle)
	{
	    super(txnProxy, profileCollectorHandle);
	}

	static boolean getUsed() {
	    boolean result = used;
	    used = false;
	    return result;
	}

	public <T> AccessReporter<T> registerAccessSource(
	    String sourceName, Class<T> objectIdType)
	{
	    return new AccessReporterImpl<T>(sourceName);
	}

	public Transaction getConflictingTransaction(Transaction txn) {
	    return null;
	}

	public void notifyNewTransaction(
	    Transaction txn, long requestedStartTime, int tryCount)
	{
	}

	private class AccessReporterImpl<T> extends AbstractAccessReporter<T> {
	    AccessReporterImpl(String source) {
		super(source);
	    }

	    public void reportObjectAccess(
		Transaction txn, T objectId, AccessType type,
		Object description)
	    {
		used = true;
	    }

	    public void setObjectDescription(
		Transaction txn, T objectId, Object description)
	    {
		used = true;
	    }
	}
    }
}
