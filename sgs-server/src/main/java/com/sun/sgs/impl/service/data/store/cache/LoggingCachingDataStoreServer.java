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
import java.io.IOException;

/**
 * A {@code CachingDataStoreServer} that delegates its operations to an
 * underlying server and logs all calls.  This class is part of the
 * implementation of {@link CachingDataStore}.
 */
class LoggingCachingDataStoreServer implements CachingDataStoreServer {

    /** The underlying server. */
    private final CachingDataStoreServer server;

    /** The logger. */
    private final LoggerWrapper logger;

    /**
     * Creates an instance of this class.
     *
     * @param	server the underlying server
     * @param	logger the logger
     */
    LoggingCachingDataStoreServer(CachingDataStoreServer server,
				  LoggerWrapper logger)
    {
	this.server = server;
	this.logger = logger;
    }

    @Override
    public RegisterNodeResult registerNode(CallbackServer callbackServer)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "registerNode callbackServer:" + callbackServer);
	}
	try {
	    RegisterNodeResult result = server.registerNode(callbackServer);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "registerNode callbackServer:" + callbackServer +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(
		    FINEST, e,
		    "registerNode callbackServer:" + callbackServer +
		    " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public long newObjectIds(int numIds) throws IOException {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "newObjectIds numIds:" + numIds);
	}
	try {
	    long result = server.newObjectIds(numIds);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "newObjectIds numIds:" + numIds +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"newObjectIds numIds:" + numIds + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public GetObjectResults getObject(long nodeId, long oid)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "getObject nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    GetObjectResults result = server.getObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getObject nodeId:" + nodeId + ", oid:" + oid +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getObject nodeId:" + nodeId + ", oid:" + oid +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public GetObjectForUpdateResults getObjectForUpdate(long nodeId, long oid)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getObjectForUpdate nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    GetObjectForUpdateResults result =
		server.getObjectForUpdate(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getObjectForUpdate nodeId:" + nodeId +
			   ", oid:" + oid + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getObjectForUpdate nodeId:" + nodeId +
				", oid:" + oid + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public boolean upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException, IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "upgradeObject nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    boolean result = server.upgradeObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "upgradeObject nodeId:" + nodeId +
			   ", oid:" + oid + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"upgradeObject nodeId:" + nodeId +
				", oid:" + oid + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public NextObjectResults nextObjectId(long nodeId, long oid)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "nextObjectId nodeId:" + nodeId + ", oid:" + oid);
	}
	try {
	    NextObjectResults result = server.nextObjectId(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "nextObjectId nodeId:" + nodeId +
			   ", oid:" + oid + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"nextObjectId nodeId:" + nodeId +
				", oid:" + oid + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public GetBindingResults getBinding(long nodeId, String name)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getBinding nodeId:" + nodeId + ", name:" + name);
	}
	try {
	    GetBindingResults result = server.getBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getBinding nodeId:" + nodeId +
			   ", name:" + name + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getBinding nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public GetBindingForUpdateResults getBindingForUpdate(
	long nodeId, String name)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getBindingForUpdate nodeId:" + nodeId +
		       ", name:" + name);
	}
	try {
	    GetBindingForUpdateResults result =
		server.getBindingForUpdate(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getBindingForUpdate nodeId:" + nodeId +
			   ", name:" + name + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getBindingForUpdate nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public GetBindingForRemoveResults getBindingForRemove(
	long nodeId, String name)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getBindingForRemove nodeId:" + nodeId +
		       ", name:" + name);
	}
	try {
	    GetBindingForRemoveResults result =
		server.getBindingForRemove(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getBindingForRemove nodeId:" + nodeId +
			   ", name:" + name + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getBindingForRemove nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public NextBoundNameResults nextBoundName(long nodeId, String name)
	throws IOException
    {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "nextBoundName nodeId:" + nodeId + ", name:" + name);
	}
	try {
	    NextBoundNameResults result = server.nextBoundName(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "nextBoundName nodeId:" + nodeId +
			   ", name:" + name + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"nextBoundName nodeId:" + nodeId +
				", name:" + name + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public int getClassId(byte[] classInfo) throws IOException {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST,
		       "getClassId classInfo:" +
		       (classInfo == null
			? "null" : "byte[" + classInfo.length + "]"));
	}
	try {
	    int result = server.getClassId(classInfo);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getClassId classInfo:" +
			   (classInfo == null
			    ? "null" : "byte[" + classInfo.length + "]") +
			   " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getClassId classInfo:" +
				(classInfo == null
				 ? "null" : "byte[" + classInfo.length + "]") +
				" throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }

    @Override
    public byte[] getClassInfo(int classId) throws IOException {
	if (logger.isLoggable(FINEST)) {
	    logger.log(FINEST, "getClassInfo classId:" + classId);
	}
	try {
	    byte[] result = server.getClassInfo(classId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "getClassInfo classId:" + classId + " returns " +
			   (result == null
			    ? "null" : "byte[" + result.length + "]"));
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e,
				"getClassInfo classId:" + classId + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else if (e instanceof RuntimeException) {
		throw (RuntimeException) e;
	    } else {
		throw (Error) e;
	    }
	}
    }
}
