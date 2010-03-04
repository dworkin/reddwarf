/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.service.data.store.db.je.JeEnvironment;
import com.sun.sgs.service.store.db.DbEnvironment;
import java.lang.reflect.Method;
import java.util.Properties;

/** Utilities for handling the data store database layer in tests. */
public final class UtilDataStoreDb {

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
	    DataStoreImpl.ENVIRONMENT_CLASS_PROPERTY);
	if (className == null ||
	    className.equals(
		"com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment"))
	{
	    return EnvironmentType.BDB;
	} else if (className.equals(
		       "com.sun.sgs.impl.service.data.store.db.je." +
		       "JeEnvironment"))
	{
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
	    return BdbEnvironment.LOCK_TIMEOUT_PROPERTY;
	case JE:
	    return JeEnvironment.LOCK_TIMEOUT_PROPERTY;
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
