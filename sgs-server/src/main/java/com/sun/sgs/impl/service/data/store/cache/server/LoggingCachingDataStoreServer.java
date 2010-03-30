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

package com.sun.sgs.impl.service.data.store.cache.server;

import com.sun.sgs.impl.service.data.store.cache.CacheConsistencyException;
import com.sun.sgs.impl.service.data.store.cache.CallbackServer;
import static com.sun.sgs.impl.sharedutil.Exceptions.throwUnchecked;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;

/**
 * A {@code CachingDataStoreServer} that delegates its operations to an
 * underlying server and logs all calls at level {@link Level#FINEST FINEST}.
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
	String operation = logger.isLoggable(FINEST)
	    ? "registerNode callbackServer:" + callbackServer : null;
	logger.log(FINEST, operation);
	try {
	    RegisterNodeResult result = server.registerNode(callbackServer);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public long newObjectIds(int numIds) throws IOException {
	String operation = logger.isLoggable(FINEST)
	    ? "newObjectIds numIds:" + numIds : null;
	logger.log(FINEST, operation);
	try {
	    long result = server.newObjectIds(numIds);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public GetObjectResults getObject(long nodeId, long oid)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "getObject nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    GetObjectResults result = server.getObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public GetObjectForUpdateResults getObjectForUpdate(long nodeId, long oid)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "getObjectForUpdate nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    GetObjectForUpdateResults result =
		server.getObjectForUpdate(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public UpgradeObjectResults upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException, IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "upgradeObject nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    UpgradeObjectResults result = server.upgradeObject(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof CacheConsistencyException) {
		throw (CacheConsistencyException) e;
	    } else if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public NextObjectResults nextObjectId(long nodeId, long oid)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "nextObjectId nodeId:" + nodeId + ", oid:" + oid : null;
	logger.log(FINEST, operation);
	try {
	    NextObjectResults result = server.nextObjectId(nodeId, oid);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public GetBindingResults getBinding(long nodeId, String name)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "getBinding nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    GetBindingResults result = server.getBinding(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public GetBindingForUpdateResults getBindingForUpdate(
	long nodeId, String name)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "getBindingForUpdate nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    GetBindingForUpdateResults result =
		server.getBindingForUpdate(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public GetBindingForRemoveResults getBindingForRemove(
	long nodeId, String name)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "getBindingForRemove nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    GetBindingForRemoveResults result =
		server.getBindingForRemove(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public NextBoundNameResults nextBoundName(long nodeId, String name)
	throws IOException
    {
	String operation = logger.isLoggable(FINEST)
	    ? "nextBoundName nodeId:" + nodeId + ", name:" + name : null;
	logger.log(FINEST, operation);
	try {
	    NextBoundNameResults result = server.nextBoundName(nodeId, name);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public int getClassId(byte[] classInfo) throws IOException {
	String operation = logger.isLoggable(FINEST)
	    ? ("getClassId classInfo:" +
	       (classInfo == null ? "null" : "byte[" + classInfo.length + "]"))
	    : null;
	logger.log(FINEST, operation);
	try {
	    int result = server.getClassId(classInfo);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, operation + " returns " + result);
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }

    @Override
    public byte[] getClassInfo(int classId) throws IOException {
	String operation = logger.isLoggable(FINEST)
	    ? "getClassInfo classId:" + classId : null;
	logger.log(FINEST, operation);
	try {
	    byte[] result = server.getClassInfo(classId);
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   operation + " returns " +
			   (result == null
			    ? "null" : "byte[" + result.length + "]"));
	    }
	    return result;
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    if (e instanceof IOException) {
		throw (IOException) e;
	    } else {
		throwUnchecked(e);
		throw new AssertionError(); /* not reached */
	    }
	}
    }
}
