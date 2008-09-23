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

package com.sun.sgs.impl.service.data.store.db;

import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.util.Properties;

/**
 * Provides a factory for obtaining an environment for interacting with a
 * database implementation.  This class should not be instantiated.
 */
public final class DbEnvironmentFactory {

    /** The property that specifies the environment class: {@value
     * #ENVIRONMENT_CLASS_PROPERTY}. */
    public static final String ENVIRONMENT_CLASS_PROPERTY =
	"com.sun.sgs.impl.service.data.store.db.environment.class";

    /** This class should not be instantiated. */
    private DbEnvironmentFactory() { }

    /**
     * Returns the environment for interacting with the database implementation
     * for files stored in the specified directory, which must exist, and
     * configured with the specified properties and scheduler.  The value of
     * the {@value #ENVIRONMENT_CLASS_PROPERTY} property, if present, should be
     * the fully qualified name of a non-abstract class that implements {@link
     * DbEnvironment}, and that has a public constructor with three parameters:
     * a {@link String}, which specifies the directory containing database
     * files, {@link Properties}, which specifies configuration options, and a
     * {@link Scheduler}, which the implementation can use to run asynchronous,
     * periodic tasks.  If the property is present, the results of calling the
     * constructor with the arguments passed to this method will be returned.
     * Otherwise, an instance of {@link BdbEnvironment}, constructed with the
     * arguments, will be returned.
     *
     * @param	directory the directory containing database files
     * @param	properties the properties to configure the implementation
     * @param	scheduler the periodic task scheduler
     * @return	the database environment
     * @throws	IllegalArgumentException if {@code properties} contains an
     *		invalid value for the {@value #ENVIRONMENT_CLASS_PROPERTY}
     *		property
     */
    public static DbEnvironment getEnvironment(
	String directory, Properties properties, Scheduler scheduler)
    {
	PropertiesWrapper wrapper = new PropertiesWrapper(properties);
	DbEnvironment result = wrapper.getClassInstanceProperty(
	    ENVIRONMENT_CLASS_PROPERTY, DbEnvironment.class,
	    new Class<?>[] { String.class, Properties.class, Scheduler.class },
	    directory, properties, scheduler);
	return (result != null)
	    ? result : new BdbEnvironment(directory, properties, scheduler);
    };
}
