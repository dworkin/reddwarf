/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.db.bdbdb.BdbDbEnvironment;
import com.sun.sgs.impl.service.data.store.db.bdbje.BdbJeEnvironment;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.util.Properties;

/**
 * Provides a factory for obtaining an environment for interacting with a
 * database implementation.
 */
public final class DbEnvironmentFactory {

    /** The property that specifies the environment class. */
    private static final String ENVIRONMENT_CLASS_PROPERTY =
	"com.sun.sgs.impl.service.data.store.db.environment.class";

    /**
     * Returns the environment for interacting with the database implementation
     * specified by the properties.  The value of the {@code
     * com.sun.sgs.impl.service.data.store.db.environment.class} property
     * should be the fully qualified name of a non-abstract class that
     * implements {@link DbEnvironment}, and that has a public constructor with
     * two parameters: a {@link Properties}, which specifies configuration
     * options, and a {@link Scheduler}, which the implementation can use to
     * run asynchronous periodic tasks.  If the property is present, the
     * results of calling the constructor with the arguments passed to this
     * method will be returned.  Otherwise, an instance of {@link
     * BdbDbEnvironment} constructed with the arguments will be returned.
     *
     * @param	properties the properties
     * @param	scheduler the periodic task scheduler
     * @return	the database environment
     * @throws	IllegalArgumentException if {@code properties} contains a value
     *		for the {@code
     *		com.sun.sgs.impl.service.data.store.db.environment.class}
     *		property that is not a valid {@code DbEnvironment} class
     */
    public static DbEnvironment getEnvironment(
	Properties properties, Scheduler scheduler)
    {
	PropertiesWrapper wrapper = new PropertiesWrapper(properties);
	DbEnvironment result = wrapper.getClassInstanceProperty(
	    ENVIRONMENT_CLASS_PROPERTY, DbEnvironment.class,
	    new Class<?>[] { Properties.class, Scheduler.class },
	    properties);
	return (result != null)
	    ? result : //new BdbDbEnvironment(properties, scheduler)
	    new BdbJeEnvironment(properties, scheduler);
    };
}
