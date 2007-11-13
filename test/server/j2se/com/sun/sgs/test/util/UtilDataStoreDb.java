/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.test.util;

import com.sun.sgs.impl.service.data.store.db.DbEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbEnvironmentFactory;
import com.sun.sgs.test.util.UtilReflection;
import java.lang.reflect.Method;
import java.util.Properties;

/** Utilities for handling the data store database layer in tests. */
public final class UtilDataStoreDb {

    /**
     * The name of the class that implements the data store database
     * environment using Berkeley DB Standard Edition.
     */
    public static final String BDB_ENVIRONMENT_CLASS_NAME =
	"com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment";

    /**
     * The name of the class that implements the data store database
     * environment using Berkeley DB Java Edition.
     */
    public static final String JE_ENVIRONMENT_CLASS_NAME =
	"com.sun.sgs.impl.service.data.store.db.je.JeEnvironment";


    /** Types of data store database environment implementations. */
    public enum EnvironmentType {

	/** Berkeley DB Standard Edition */
	BDB,

	/** Berkeley DB Java Edition */
	JE
    };

    /**
     * Returns type of data store database environment implementation in use.
     *
     * @param properties the configuration properties
     * @return the database environment type
     */
    public static EnvironmentType getEnvironmentType(Properties properties) {
	String className = properties.getProperty(
	    DbEnvironmentFactory.ENVIRONMENT_CLASS_PROPERTY);
	if (className == null ||
	    className.equals(BDB_ENVIRONMENT_CLASS_NAME))
	{
	    return EnvironmentType.BDB;
	} else if (className.equals(JE_ENVIRONMENT_CLASS_NAME)) {
	    return EnvironmentType.JE;
	} else {
	    throw new RuntimeException(
		"Unknown environment class: " + className);
	}
    }

    /**
     * Returns the system property that specifies the lock timeout.
     *
     * @param properties the configuration properties
     * @return the system property for specifying the lock timeout
     */
    public static String getLockTimeoutPropertyName(
	Properties properties)
    {
	switch (getEnvironmentType(properties)) {
	case BDB:
	    try {
		return (String) UtilReflection.getField(
		    Class.forName(BDB_ENVIRONMENT_CLASS_NAME),
		    "LOCK_TIMEOUT_PROPERTY").get(null);
	    } catch (Exception e) {
		throw new RuntimeException(
		    "Unexpected exception: " + e, e);
	    }
	case JE:
	    try {
		return (String) UtilReflection.getField(
		    Class.forName(JE_ENVIRONMENT_CLASS_NAME),
		    "LOCK_TIMEOUT_PROPERTY").get(null);
	    } catch (Exception e) {
		throw new RuntimeException(
		    "Unexpected exception: " + e, e);
	    }
	default:
	    throw new RuntimeException("Unknown environment");
	}
    }

    /**
     * Returns the lock timeout in microseconds for the specified data store
     * database environment.
     *
     * @param env the database environment
     * @return the lock timeout in microseconds
     */
    public static long getLockTimeoutMicros(DbEnvironment env) {
	Method method = UtilReflection.getMethod(
	    env.getClass(), "getLockTimeoutMicros");
	try {
	    return (Long) method.invoke(env);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }
}
