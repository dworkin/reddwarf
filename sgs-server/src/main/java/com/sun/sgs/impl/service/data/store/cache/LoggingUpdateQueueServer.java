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

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.util.Arrays;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;

/**
 * A {@code UpdateQueueServer} that delegates its operations to an underlying
 * server and logs all calls at {@link Level#FINEST FINEST}.  This class is
 * part of the implementation of {@link CachingDataStore}.
 */
class LoggingUpdateQueueServer implements UpdateQueueServer {

    /** The underlying server. */
    private final UpdateQueueServer server;

    /** The logger. */
    private final LoggerWrapper logger;

    /**
     * Creates an instance of this class.
     *
     * @param	server the underlying server
     * @param	logger the logger
     */
    LoggingUpdateQueueServer(UpdateQueueServer server, LoggerWrapper logger) {
	this.server = server;
	this.logger = logger;
    }

    /* -- Implement UpdateQueueServer -- */

    @Override
    public void commit(long nodeId,
		       long[] oids,
		       byte[][] oidValues,
		       int newOids,
		       String[] names,
		       long[] nameValues,
		       int newNames)
	throws CacheConsistencyException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "commit nodeId:" + nodeId +
		       ", oids:" + Arrays.toString(oids) +
		       ", oidValues:" + toStringAbbrev(oidValues) +
		       ", newOids:" + newOids +
		       ", names:" + Arrays.toString(names) +
		       ", nameValues:" + Arrays.toString(nameValues) +
		       ", newNames:" + newNames);
	}
	try {
	    server.commit(
		nodeId, oids, oidValues, newOids, names, nameValues, newNames);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "commit nodeId:" + nodeId +
			   ", oids:" + Arrays.toString(oids) +
			   ", oidValues:" + toStringAbbrev(oidValues) +
			   ", newOids:" + newOids +
			   ", names:" + Arrays.toString(names) +
			   ", nameValues:" + Arrays.toString(nameValues) +
			   ", newNames:" + newNames +
			   " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"commit nodeId:" + nodeId +
				", oids:" + Arrays.toString(oids) +
				", oidValues:" + toStringAbbrev(oidValues) +
				", newOids:" + newOids +
				", names:" + Arrays.toString(names) +
				", nameValues:" + Arrays.toString(nameValues) +
				", newNames:" + newNames +
				" throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    @Override
    public void evictObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "evictObject nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    server.evictObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "evictObject nodeId:" + nodeId + ", oid:" + oid +
			   " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"evictObject nodeId:" + nodeId +
				", oid:" + oid + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    @Override
    public void downgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "downgradeObject nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    server.downgradeObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "downgradeObject nodeId:" + nodeId +
			   ", oid:" + oid + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"downgradeObject nodeId:" + nodeId +
				", oid:" + oid + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    @Override
    public void evictBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "evictBinding nodeId:" + nodeId + ", name:" + name);
	}
	try {
	    server.evictBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "evictBinding nodeId:" + nodeId +
			   ", name:" + name + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"evictBinding nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    @Override
    public void downgradeBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "downgradeBinding nodeId:" + nodeId + ", name:" + name);
	}
	try {
	    server.downgradeBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "downgradeBinding nodeId:" + nodeId +
			   ", name:" + name + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"downgradeBinding nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }

    /* -- Other methods -- */

    /**
     * Print the contents of an array of arrays of bytes, but just printing
     * each element's size or that it is null.
     */
    private static String toStringAbbrev(byte[][] array) {
	if (array == null) {
	    return "null";
	}
	StringBuilder sb = new StringBuilder("[");
	for (int i = 0; i < array.length; i++) {
	    if (i != 0) {
		sb.append(", ");
	    }
	    if (array[i] == null) {
		sb.append("null");
	    } else {
		sb.append("byte[").append(array[i].length).append("]");
	    }
	}
	sb.append("]");
	return sb.toString();
    }
}
