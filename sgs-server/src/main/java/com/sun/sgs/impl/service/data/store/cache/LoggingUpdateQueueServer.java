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
import static java.util.logging.Level.FINEST;

/**
 * A {@code UpdateQueueServer} that delegates its operations to an underlying
 * server and logs all calls.
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
		       ", oids:" +
		       (oids == null ? "null" :
			oids.length == 0 ? "[]" :
			"[" + oids[0] + "...]") +
		       ", newOids:" + newOids +
		       ", names:" +
		       (names == null ? "null" :
			names.length == 0 ? "[]" :
			"[" + names[0] + "...]") +
		       ", newNames:" + newNames);
	}
	try {
	    server.commit(
		nodeId, oids, oidValues, newOids, names, nameValues, newNames);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "commit nodeId:" + nodeId +
			   ", oids:" +
			   (oids == null ? "null" :
			    oids.length == 0 ? "[]" :
			    "[" + oids[0] + "...]") +
			   ", newOids:" + newOids +
			   ", names:" +
			   (names == null ? "null" :
			    names.length == 0 ? "[]" :
			    "[" + names[0] + "...]") +
			   ", newNames:" + newNames +
			   " returns");
	    }
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"commit nodeId:" + nodeId +
				", oids:" +
				(oids == null ? "null" :
				 oids.length == 0 ? "[]" :
				 "[" + oids[0] + "...]") +
				", newOids:" + newOids +
				", names:" + 
				(names == null ? "null" :
				 names.length == 0 ? "[]" :
				 "[" + names[0] + "...]") +
				", newNames:" + newNames +
				" throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
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
	    } else {
		throw (Error) e;
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
	    } else {
		throw (Error) e;
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
	    } else {
		throw (Error) e;
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
	    server.evictBinding(nodeId, name);
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
	    } else {
		throw (Error) e;
	    }
	}
    }
}
