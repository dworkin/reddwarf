/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.data.store.cache.queue;

import com.sun.sgs.impl.service.data.store.cache.CacheConsistencyException;
import static com.sun.sgs.impl.sharedutil.Exceptions.throwUnchecked;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.util.Arrays;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;

/**
 * A {@code UpdateQueueServer} that delegates its operations to an underlying
 * server and logs all calls at {@link Level#FINEST FINEST}.
 */
public class LoggingUpdateQueueServer implements UpdateQueueServer {

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
    public LoggingUpdateQueueServer(
	UpdateQueueServer server, LoggerWrapper logger)
    {
	this.server = server;
	this.logger = logger;
    }

    /* -- Implement UpdateQueueServer -- */

    /** {@inheritDoc} */
    @Override
    public void commit(long nodeId,
		       long[] oids,
		       byte[][] oidValues,
		       int newOids,
		       String[] names,
		       long[] nameValues)
	throws CacheConsistencyException
    {
	String operation = logger.isLoggable(FINEST)
	    ? ("commit nodeId:" + nodeId +
	       ", oids:" + Arrays.toString(oids) +
	       ", oidValues:" + toStringAbbrev(oidValues) +
	       ", newOids:" + newOids +
	       ", names:" + Arrays.toString(names) +
	       ", nameValues:" + Arrays.toString(nameValues))
	    : null;
	logger.log(FINEST, operation);
	try {
	    server.commit(nodeId, oids, oidValues, newOids, names, nameValues);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else {
		throwUnchecked(e);
	    }
	}
    }

    /** {@inheritDoc} */
    @Override
    public void evictObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "evictObject nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    server.evictObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else {
		throwUnchecked(e);
	    }
	}
    }

    /** {@inheritDoc} */
    @Override
    public void downgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "downgradeObject nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    server.downgradeObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else {
		throwUnchecked(e);
	    }
	}
    }

    /** {@inheritDoc} */
    @Override
    public void evictBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "evictBinding nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    server.evictBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else {
		throwUnchecked(e);
	    }
	}
    }

    /** {@inheritDoc} */
    @Override
    public void downgradeBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "downgradeBinding nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    server.downgradeBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else {
		throwUnchecked(e);
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
