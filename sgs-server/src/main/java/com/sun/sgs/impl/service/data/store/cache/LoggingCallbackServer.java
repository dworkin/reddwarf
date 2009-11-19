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

import static com.sun.sgs.impl.sharedutil.Exceptions.initCause;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;

/**
 * A {@code CallbackServer} that delegates its operations to an underlying
 * server and logs all calls at level {@link Level#FINEST FINEST}.  This class
 * is part of the implementation of {@link CachingDataStore}.
 */
class LoggingCallbackServer implements CallbackServer {

    /** The underlying server. */
    private final CallbackServer server;

    /** The logger. */
    private final LoggerWrapper logger;

    /** The local node ID, or -1 if not set. */
    private volatile long nodeId = -1;

    /**
     * Creates an instance of this class.
     *
     * @param	server the underlying server
     * @param	logger the logger
     */
    LoggingCallbackServer(CallbackServer server, LoggerWrapper logger) {
	this.server = server;
	this.logger = logger;
    }

    /**
     * Sets the local node ID.
     *
     * @param	nodeId the local node ID
     */
    void setLocalNodeId(long nodeId) {
	this.nodeId = nodeId;
    }

    /* -- Implement CallbackServer -- */

    @Override
    public boolean requestDowngradeObject(long oid, long conflictNodeId)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "requestDowngradeObject nodeId:" + nodeId +
		       ", oid:" + oid + ", conflictNodeId:" + conflictNodeId);
	}
	try {
	    boolean result =
		server.requestDowngradeObject(oid, conflictNodeId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "requestDowngradeObject nodeId:" + nodeId +
			   ", oid:" + oid +
			   ", conflictNodeId:" + conflictNodeId +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"requestDowngradeObject nodeId:" + nodeId +
				", oid:" + oid +
				", conflictNodeId:" + conflictNodeId +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw initCause(
		    new AssertionError("Unexpected exception: " + e), e);
	    }
	}
    }

    @Override
    public boolean requestEvictObject(long oid, long conflictNodeId)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "requestEvictObject nodeId:" + nodeId + ", oid:" + oid +
		       ", conflictNodeId:" + conflictNodeId);
	}
	try {
	    boolean result = server.requestEvictObject(oid, conflictNodeId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "requestEvictObject nodeId:" + nodeId +
			   ", oid:" + oid +
			   ", conflictNodeId:" + conflictNodeId +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"requestEvictObject nodeId:" + nodeId +
				", oid:" + oid +
				", conflictNodeId:" + conflictNodeId +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw initCause(
		    new AssertionError("Unexpected exception: " + e), e);
	    }
	}
    }

    @Override
    public boolean requestDowngradeBinding(String name, long conflictNodeId)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "requestDowngradeBinding nodeId:" + nodeId +
		       ", name:" + name +
		       ", conflictNodeId:" + conflictNodeId);
	}
	try {
	    boolean result =
		server.requestDowngradeBinding(name, conflictNodeId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "requestDowngradeBinding nodeId:" + nodeId +
			   ", name:" + name +
			   ", conflictNodeId:" + conflictNodeId +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"requestDowngradeBinding nodeId:" + nodeId +
				", name:" + name +
				", conflictNodeId:" + conflictNodeId +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw initCause(
		    new AssertionError("Unexpected exception: " + e), e);
	    }
	}
    }

    @Override
    public boolean requestEvictBinding(String name, long conflictNodeId)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "requestEvictBinding nodeId:" + nodeId +
		       ", name:" + name +
		       ", conflictNodeId:" + conflictNodeId);
	}
	try {
	    boolean result = server.requestEvictBinding(name, conflictNodeId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "requestEvictBinding nodeId:" + nodeId +
			   ", name:" + name +
			   ", conflictNodeId:" + conflictNodeId +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"requestEvictBinding nodeId:" + nodeId +
				", name:" + name +
				", conflictNodeId:" + conflictNodeId +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else if (e instanceof Error) {
		throw (Error) e;
	    } else {
		throw initCause(
		    new AssertionError("Unexpected exception: " + e), e);
	    }
	}
    }
}
