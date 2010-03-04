/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.kernel.NullAccessCoordinator;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.DummyProfileCollectorHandle;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.Assert;

/**
 * A basic environment for running a {@code DataStore} test.  This class
 * emulates the behavior of the {@code Kernel} class in setting up system
 * components.
 */
public class BasicDataStoreTestEnv extends Assert {

    /** The property for overriding the default access coordinator. */
    public final static String ACCESS_COORDINATOR_PROPERTY =
	"test.access.coordinator";

    /** The transaction proxy. */
    public final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** The profile collector handle. */
    public final DummyProfileCollectorHandle profileCollectorHandle =
	new DummyProfileCollectorHandle();

    /** The access coordinator. */
    public final AccessCoordinatorHandle accessCoordinator;

    /** The transaction scheduler. */
    public final TransactionScheduler txnScheduler;

    /** The task scheduler. */
    public final TaskScheduler taskScheduler;

    /**
     * The system registry, which contains the access coordinator, transaction
     * scheduler, and task scheduler.
     */
    public final ComponentRegistry systemRegistry;
    
    /**
     * Creates a basic environment for running a {@code DataStore} test, using
     * a {@link NullAccessCoordinator} by default.
     *
     * @param	properties the configuration properties
     */
    public BasicDataStoreTestEnv(Properties properties) {
	this(properties, NullAccessCoordinator.class.getName());
    }
    
    /**
     * Creates a basic environment for running a {@code DataStore} test, using
     * an access coordinator of the specified class by default.
     *
     * @param	properties the configuration properties
     * @param	accessCoordinatorClassName the class name of the access
     *		coordinator to use by default
     */
    public BasicDataStoreTestEnv(Properties properties,
				 String accessCoordinatorClassName)
    {
	try {
	    /* Access coordinator */
	    accessCoordinatorClassName =
		properties.getProperty(ACCESS_COORDINATOR_PROPERTY,
				       accessCoordinatorClassName);
	    Constructor<? extends AccessCoordinatorHandle> accessCoordCons =
		Class.forName(accessCoordinatorClassName)
		.asSubclass(AccessCoordinatorHandle.class)
		.getDeclaredConstructor(Properties.class,
					TransactionProxy.class,
					ProfileCollectorHandle.class);
	    accessCoordCons.setAccessible(true);
	    accessCoordinator = accessCoordCons.newInstance(
		properties, txnProxy, profileCollectorHandle);
	    /* Transaction scheduler */
	    Constructor<? extends TransactionScheduler> txnSchedCons =
		Class.forName(
		    "com.sun.sgs.impl.kernel.TransactionSchedulerImpl")
		.asSubclass(TransactionScheduler.class)
		.getDeclaredConstructor(
		    Properties.class, TransactionCoordinator.class,
		    ProfileCollectorHandle.class,
		    AccessCoordinatorHandle.class);
	    txnSchedCons.setAccessible(true);
	    txnScheduler = txnSchedCons.newInstance(
		properties, txnProxy, profileCollectorHandle,
		accessCoordinator);
	    /* Task scheduler */
	    Constructor<? extends TaskScheduler> taskSchedCons =
		Class.forName(
		    "com.sun.sgs.impl.kernel.TaskSchedulerImpl")
		.asSubclass(TaskScheduler.class)
		.getDeclaredConstructor(
		    Properties.class, ProfileCollectorHandle.class);
	    taskSchedCons.setAccessible(true);
	    taskScheduler = taskSchedCons.newInstance(
		properties, profileCollectorHandle);
	    /* System registry */
	    Class<? extends ComponentRegistry> sysRegClass =
		Class.forName("com.sun.sgs.impl.kernel.ComponentRegistryImpl")
		.asSubclass(ComponentRegistry.class);
	    Constructor<? extends ComponentRegistry> sysRegCons =
		sysRegClass.getDeclaredConstructor();
	    sysRegCons.setAccessible(true);
	    systemRegistry = sysRegCons.newInstance();
	    Method addComponentMethod = sysRegClass.getDeclaredMethod(
		"addComponent", Object.class);
	    addComponentMethod.setAccessible(true);
	    addComponentMethod.invoke(systemRegistry, accessCoordinator);
	    addComponentMethod.invoke(systemRegistry, txnScheduler);
	    addComponentMethod.invoke(systemRegistry, taskScheduler);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }
}
