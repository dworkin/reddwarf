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

import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeString;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeString;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DbUtilities;
import com.sun.sgs.impl.service.data.store.DbUtilities.Databases;
import com.sun.sgs.impl.service.data.store.DelegatingScheduler;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractComponent;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.lock.LockConflict;
import static com.sun.sgs.impl.util.lock.LockConflictType.DEADLOCK;
import static com.sun.sgs.impl.util.lock.LockConflictType.DENIED;
import static com.sun.sgs.impl.util.lock.LockConflictType.INTERRUPTED;
import static com.sun.sgs.impl.util.lock.LockConflictType.TIMEOUT;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.impl.util.lock.MultiLocker;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TransactionInterruptedException;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.db.DbCursor;
import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbEnvironment;
import com.sun.sgs.service.store.db.DbTransaction;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.INFO;
import java.util.logging.Logger;

/*
 * - Permit waiting for locks in multiple threads for the same locker?  The
 *   current scheme only permits a single waiter, and uses that information to
 *   find deadlocks.  It also stores a value in a single conflict field.  Hmmm.
 */

/*
 * Note that, to avoid deadlocks, operations that grab multiple locks should
 * grab them in key order.  This only applies to getBindingForUpdate and
 * getBindingForRemove.
 */

public class CachingDataStoreServerImpl extends AbstractComponent
    implements CachingDataStoreServer
{
    /** The package for this class. */
    private static final String PKG =
	"com.sun.sgs.impl.service.data.store.cache";

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    public static final String DIRECTORY_PROPERTY = PKG + ".directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG));

    /** The directory in which to store database files. */
    private final String directory;

    /** The database environment. */
    private final DbEnvironment env;

    /**
     * The database that holds version and next object ID information.  This
     * information is stored in a separate database to avoid concurrency
     * conflicts between the object ID and other data.
     */
    private final DbDatabase infoDb;

    /** The database that stores class information. */
    private final DbDatabase classesDb;

    /** The database that maps object IDs to object bytes. */
    final DbDatabase oidsDb;

    /** The database that maps name bindings to object IDs. */
    private final DbDatabase namesDb;

    /**
     * Maps node IDs to information about the node.  Synchronize on the map
     * when accessing it.
     */
    private final Map<Long, NodeInfo> nodeInfoMap =
	new HashMap<Long, NodeInfo>();

    private final MultiLockManager<Object, NodeInfo> lockManager =
	new MultiLockManager<Object, NodeInfo>(100, 8);

    final BlockingQueue<NodeRequest> callbackRequests =
	new LinkedBlockingQueue<NodeRequest>();

    private final long txnTimeout = -1;  //FIXME: from config

    private final int updateQueuePort = 0; //FIXME: from config

    private boolean shuttingDown = false;

    /* -- Nested classes -- */

    static class NodeInfo extends MultiLocker<Object, NodeInfo> {
	final long nodeId;
	final CallbackServer callbackServer;
	final Map<Object, NodeRequest> locks =
	    new HashMap<Object, NodeRequest>();
	final RequestQueueServer<UpdateQueueRequest> updateQueue;

	NodeInfo(CachingDataStoreServerImpl server,
		 MultiLockManager<Object, NodeInfo> lockManager,
		 long nodeId,
		 CallbackServer callbackServer)
	{
	    super(lockManager);
	    this.nodeId = nodeId;
	    this.callbackServer = callbackServer;
	    updateQueue = new RequestQueueServer<UpdateQueueRequest>(
		nodeId,
		new UpdateQueueRequest.UpdateQueueRequestHandler(
		    server, nodeId),
		new Properties());
	}
	@Override
	protected LockRequest<Object, NodeInfo> newLockRequest(
	    Object key, boolean forWrite, boolean upgrade,
	    long requestedStartTime)
	{
	    return new NodeRequest(
		this, key, forWrite, upgrade, requestedStartTime);
	}
    }

    static class NodeRequest extends LockRequest<Object, NodeInfo> {
	final long requestedStartTime;
	private boolean calledBack;
	NodeRequest(NodeInfo nodeInfo,
		    Object key,
		    boolean forWrite,
		    boolean upgrade,
		    long requestedStartTime)
	{
	    super(nodeInfo, key, forWrite, upgrade);
	    this.requestedStartTime = requestedStartTime;
	    
	}
	@Override
	public long getRequestedStartTime() {
	    return requestedStartTime;
	}
	synchronized boolean noteCallback() {
	    if (!calledBack) {
		calledBack = true;
		return true;
	    } else {
		return false;
	    }
	}
    }

    class CallbackRequester implements Runnable {
	CallbackRequester() { }
	public void run() {
	    while (true) {
		try {
		    callbackOwners(callbackRequests.take());
		} catch (InterruptedException e) {
		    break;
		}
	    }
	}
	private void callbackOwners(NodeRequest request) {
	    NodeInfo nodeInfo = request.getLocker();
	    for (LockRequest<Object, NodeInfo> owner
		     : lockManager.getOwners(request.getKey()))
	    {
		if (nodeInfo != owner.getLocker()) {
		    callback((NodeRequest) owner, request);
		}
	    }
	}
	void callback(NodeRequest request, NodeRequest forRequest) {
	    if (!request.noteCallback()) {
		boolean downgrade = request.getForWrite() &&
		    !forRequest.getForWrite();
		NodeInfo nodeInfo = request.getLocker();
		Object key = request.getKey();
		CallbackTask task = new CallbackTask(
		    nodeInfo.callbackServer, key, downgrade);
		runIoTask(task, nodeInfo.nodeId);
		if (task.released) {
		    if (downgrade) {
			lockManager.downgradeLock(nodeInfo, key);
		    } else {
			lockManager.releaseLock(nodeInfo, key);
		    }
		}
	    }
	}
    }

    private static class CallbackTask implements IoRunnable {
	private final CallbackServer callbackServer;
	private final Object key;
	private final boolean downgrade;
	boolean released;
	CallbackTask(CallbackServer callbackServer,
		     Object key,
		     boolean downgrade)
	{
	    this.callbackServer = callbackServer;
	    this.key = key;
	    this.downgrade = downgrade;
	}
	public void run() throws IOException {
	    if (key instanceof String) {
		if (downgrade) {
		    released = callbackServer.requestDowngradeBinding(
			(String) key, /* FIXME: nodeId */ 0);
		} else {
		    released = callbackServer.requestEvictBinding(
			(String) key, /* FIXME: nodeId */ 0);
		}
	    } else {
		if (downgrade) {
		    released = callbackServer.requestDowngradeObject(
			(Long) key, /* FIXME: nodeId */ 0);
		} else {
		    released = callbackServer.requestEvictObject(
			(Long) key, /* FIXME: nodeId */ 0);
		}
	    }
	}
    }

    /* -- Constructor -- */

    public CachingDataStoreServerImpl(Properties properties,
				      ComponentRegistry systemRegistry,
				      TransactionProxy txnProxy)
    {
	super(properties, systemRegistry, txnProxy, logger);
	logger.log(CONFIG,
		   "Creating CachingDataStoreServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String specifiedDirectory =
	    wrappedProps.getProperty(DIRECTORY_PROPERTY);
	if (specifiedDirectory == null) {
	    String rootDir =
		properties.getProperty(StandardProperties.APP_ROOT);
	    if (rootDir == null) {
		throw new IllegalArgumentException(
		    "A value for the property " + StandardProperties.APP_ROOT +
		    " must be specified");
	    }
	    specifiedDirectory = rootDir + File.separator + DEFAULT_DIRECTORY;
	}
	/* FIXME: Start update queue listener */
	/*
	 * Use an absolute path to avoid problems on Windows.
	 * -tjb@sun.com (02/16/2007)
	 */
	directory = new File(specifiedDirectory).getAbsolutePath();
	Identity taskOwner = txnProxy.getCurrentOwner();
	TaskScheduler taskScheduler =
	    systemRegistry.getComponent(TaskScheduler.class);
	File directoryFile = new File(specifiedDirectory).getAbsoluteFile();
	if (!directoryFile.exists()) {
	    logger.log(INFO, "Creating database directory : " +
		       directoryFile.getAbsolutePath());
	    if (!directoryFile.mkdirs()) {
		throw new DataStoreException(
		    "Unable to create database directory: " +
		    directoryFile.getName());
	    }
	}
	env = wrappedProps.getClassInstanceProperty(
	    DataStoreImpl.ENVIRONMENT_CLASS_PROPERTY,
	    DataStoreImpl.DEFAULT_ENVIRONMENT_CLASS,
	    DbEnvironment.class,
	    new Class<?>[] {
		String.class, Properties.class, Scheduler.class },
	    directory, properties,
	    new DelegatingScheduler(taskScheduler, taskOwner));
	boolean done = false;
	DbTransaction txn = env.beginTransaction(Long.MAX_VALUE);
	try {
	    Databases dbs = DbUtilities.getDatabases(env, txn, logger);
	    infoDb = dbs.info();
	    classesDb = dbs.classes();
	    oidsDb = dbs.oids();
	    namesDb = dbs.names();
	    done = true;
	    txn.commit();
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
    }

    /* -- Implement CachingDataStoreServer -- */

    public RegisterNodeResult registerNode(CallbackServer callbackServer) {
	checkNull("callbackServer", callbackServer);
// FIXME
// 	synchronized (nodeInfoMap) {
// 	    if (nodeInfoMap.containsKey(nodeId)) {
// 		throw new IllegalArgumentException(
// 		    "Node " + nodeId + " has already been registered");
// 	    } else {
// 		nodeInfoMap.put(
// 		    nodeId,
// 		    new NodeInfo(this, lockManager, nodeId, callbackServer));
// 	    }
// 	}
	return new RegisterNodeResult(
	    /* FIXME: Get node ID */ 0,
	    updateQueuePort);
    }

    public long newObjectIds(int numIds) {
	boolean done = false;
	DbTransaction txn = env.beginTransaction(txnTimeout);
	try {
	    long result = DbUtilities.getNextObjectId(infoDb, txn, numIds);
	    done = true;
	    txn.commit();
	    return result;
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
    }

    public GetObjectResults getObject(long nodeId, long oid) {
	checkOid(oid);
	lock(nodeId, oid, false);
	DbTransaction txn = env.beginTransaction(txnTimeout);
	boolean txnDone = false;
	try {
	    byte[] result = oidsDb.get(txn, encodeLong(oid), false);
	    txnDone = true;
	    txn.commit();
	    return (result == null) ? null
		: new GetObjectResults(
		    result, getWaiting(oid, false));
	} finally {
	    if (!txnDone) {
		txn.abort();
	    }
	}
    }

    public GetObjectForUpdateResults getObjectForUpdate(
	long nodeId, long oid)
    {
	checkOid(oid);
	lock(nodeId, oid, true);
	DbTransaction txn = env.beginTransaction(txnTimeout);
	boolean txnDone = false;
	try {
	    byte[] result = oidsDb.get(txn, encodeLong(oid), true);
	    txnDone = true;
	    txn.commit();
	    return (result == null) ? null
		: new GetObjectForUpdateResults(
		    result, getWaiting(oid, false), getWaiting(oid, true));
	} finally {
	    if (!txnDone) {
		txn.abort();
	    }
	}
    }

    public boolean upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	checkOid(oid);
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	boolean found = false;
	for (LockRequest<Object, NodeInfo> owner :
		 lockManager.getOwners(oid))
	{
	    if (nodeInfo == owner.getLocker()) {
		if (owner.getForWrite()) {
		    return false;
		} else {
		    found = true;
		    break;
		}
	    }
	}
	if (!found) {
	    throw new CacheConsistencyException(
		"Node " + nodeId + " attempted to upgrade object " + oid +
		", but does not own that object for read");
	}
	lock(nodeInfo, oid, true);
	return getWaiting(oid, true);
    }

    public NextObjectResults nextObjectId(long nodeId, long oid) {
	checkOid(oid);
	DbTransaction txn = env.beginTransaction(txnTimeout);
	DbCursor cursor = null;
	boolean done = false;
	NextObjectResults result;
	try {
	    cursor = oidsDb.openCursor(txn);
	    boolean found = (oid == -1) ? cursor.findFirst()
		: cursor.findNext(encodeLong(oid));
	    if (!found) {
		result = null;
	    } else {
		result = new NextObjectResults(
		    decodeLong(cursor.getKey()),
		    cursor.getValue(),
		    getWaiting(oid, false));
	    }
	    done = true;
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	    if (done) {
		txn.commit();
	    } else {
		txn.abort();
	    }
	}
	lock(nodeId, result.oid, false);
	return result;
    }

    public GetBindingResults getBinding(long nodeId, String name) {
	checkNull("name", name);
	DbTransaction txn = env.beginTransaction(txnTimeout);
	boolean done = false;
	boolean found;
	long oid;
	try {
	    byte[] encodedName = encodeString(name);
	    byte[] value = namesDb.get(txn, encodedName, false);
	    found = (value != null);
	    if (found) {
		oid = decodeLong(value);
	    } else {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = cursor.findNext(encodedName);
		    name = hasNext ? decodeString(cursor.getKey()) : null;
		    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		}
	    }
	    done = true;
	    txn.commit();
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
	lock(nodeId, name, false);
	return new GetBindingResults(
	    found, found ? null : name, oid,
	    (oid == -1) ? false : getWaiting(name, false));
    }

    public GetBindingForUpdateResults getBindingForUpdate(
	long nodeId, String name)
    {
	checkNull("name", name);
	boolean done = false;
	DbTransaction txn = env.beginTransaction(txnTimeout);
	String nextName = null;
	boolean found;
	long oid;
	try {
	    byte[] encodedName = encodeString(name);
	    byte[] value = namesDb.get(txn, encodedName, true);
	    found = (value != null);
	    if (found) {
		oid = decodeLong(value);
	    } else {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = cursor.findNext(encodedName);
		    nextName = hasNext ? decodeString(cursor.getKey()) : null;
		    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		}
	    }
	    done = true;
	    txn.commit();
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
	lock(nodeId, name, true);
	if (!found) {
	    lock(nodeId, nextName, true);
	}
	return new GetBindingForUpdateResults(
	    found, found ? null : nextName, oid,
	    (oid == -1) ? false : getWaiting(name, true),
	    (oid == -1) ? false : getWaiting(name, false));
    }

    public GetBindingForRemoveResults getBindingForRemove(
	long nodeId, String name)
    {
	checkNull("name", name);
	DbTransaction txn = env.beginTransaction(txnTimeout);
	boolean done = false;
	String nextName;
	long oid;
	long nextOid;
	try {
	    byte[] encodedName = encodeString(name);
	    byte[] value = namesDb.get(txn, encodedName, true);
	    oid = (value == null) ? -1 : decodeLong(value);
	    DbCursor cursor = namesDb.openCursor(txn);
	    try {
		boolean hasNext = cursor.findNext(encodedName);
		nextName = hasNext ? decodeString(cursor.getKey()) : null;
		nextOid = hasNext ? decodeLong(cursor.getValue()) : -1;
	    } finally {
		cursor.close();
	    }
	    done = true;
	    txn.commit();
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
	if (oid != -1) {
	    lock(nodeId, name, true);
	}
	lock(nodeId, nextName, oid != -1);
	return new GetBindingForRemoveResults(
	    oid != -1,
	    nextName,
	    oid,
	    oid == -1 ? false : getWaiting(name, true),
	    oid == -1 ? false : getWaiting(name, false),
	    nextOid,
	    getWaiting(nextName, true),
	    oid == -1 ? false : getWaiting(nextName, false));
    }

    public NextBoundNameResults nextBoundName(long nodeId, String name) {
	checkNull("name", name);
	long oid;
	boolean done = false;
	DbTransaction txn = env.beginTransaction(txnTimeout);
	DbCursor cursor = null;
	try {
	    cursor = namesDb.openCursor(txn);
	    boolean hasNext = cursor.findNext(encodeString(name));
	    name = hasNext ? decodeString(cursor.getKey()) : null;
	    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
	    done = true;
	    txn.commit();
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	    if (!done) {
		txn.abort();
	    }
	}
	lock(nodeId, name, false);
	return new NextBoundNameResults(
	    name, oid, getWaiting(name, false));
    }

    public int getClassId(byte[] classInfo) {
	return DbUtilities.getClassId(env, classesDb, classInfo, txnTimeout);
    }
	    
    public byte[] getClassInfo(int classId) {
	return DbUtilities.getClassInfo(env, classesDb, classId, txnTimeout);
    }

    /* -- UpdateQueue methods -- */

    public void commit(long nodeId,
		       long[] oids,
		       byte[][] oidValues,
		       String[] names,
		       long[] nameValues)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	checkNull("oids", oids);
	checkNull("oidValues", oidValues);
	if (oids.length != oidValues.length) {
	    throw new IllegalArgumentException(
		"The number of object IDs and OID values must be the same");
	}
	checkNull("names", names);
	checkNull("nameValues", nameValues);
	if (names.length != nameValues.length) {
	    throw new IllegalArgumentException(
		"The number of names and name values must be the same");
	}
	boolean done = false;
	DbTransaction txn = env.beginTransaction(txnTimeout);
	try {
	    for (int i = 0; i < oids.length; i++) {
		long oid = oids[i];
		if (oid < 0) {
		    throw new IllegalArgumentException(
			"The object IDs must not be negative");
		}
		checkLocked(nodeInfo, oid, true);
		byte[] value = oidValues[i];
		if (value == null) {
		    oidsDb.delete(txn, encodeLong(oid));
		} else {
		    oidsDb.put(txn, encodeLong(oid), oidValues[i]);
		}
	    }
	    for (int i = 0; i < names.length; i++) {
		String name = names[i];
		if (name == null) {
		    throw new IllegalArgumentException(
			"The names must not be null");
		}
		checkLocked(nodeInfo, name, true);
		long value = nameValues[i];
		if (value < -1) {
		    throw new IllegalArgumentException(
			"The name values must not be less than -1");
		} else if (value == -1) {
		    namesDb.delete(txn, encodeString(name));
		} else {
		    namesDb.put(txn, encodeString(name), encodeLong(value));
		}
	    }
	    done = true;
	    txn.commit();
	} finally {
	    if (!done) {
		txn.abort();
	    }
	}
    }

    public void evictObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	checkOid(oid);
	checkLocked(nodeInfo, oid, false);
	lockManager.releaseLock(nodeInfo, oid);
    }

    public void downgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	checkOid(oid);
	checkLocked(nodeInfo, oid, true);
	lockManager.downgradeLock(nodeInfo, oid);
    }

    public void evictBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	checkNull("name", name);
	checkLocked(nodeInfo, name, false);
	lockManager.releaseLock(nodeInfo, name);
    }

    public void downgradeBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = getNodeInfo(nodeId);
	checkNull("name", name);
	checkLocked(nodeInfo, name, true);
	lockManager.downgradeLock(nodeInfo, name);
    }

    /* -- Other public methods -- */

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "CachingDataStoreServerImpl[directory=\"" + directory + "\"]";
    }

    /* -- AbstractComponent methods -- */

    @Override
    protected void doShutdown() {
	infoDb.close();
	classesDb.close();
	namesDb.close();
	env.close();
    }

    /* -- Other methods -- */

    private void lock(long nodeId, Object key, boolean forWrite) {
	lock(getNodeInfo(nodeId), key, forWrite);
    }

    private void lock(NodeInfo nodeInfo, Object key, boolean forWrite) {
	synchronized (nodeInfo) {
	    LockConflict<Object, NodeInfo> conflict =
		lockManager.lockNoWait(nodeInfo, key, forWrite, -1);
	    if (conflict != null) {
		NodeRequest request = (NodeRequest) conflict.getLockRequest();
		callbackRequests.add(request);
		conflict = lockManager.waitForLock(nodeInfo);
		if (conflict != null) {
		    String accessMsg = "Access nodeId:" + nodeInfo.nodeId +
		    ", key:" + key +
		    ", forWrite:" + forWrite +
		    " failed: ";
		String conflictMsg = ", with conflicting node ID " +
		    conflict.getConflictingLocker().nodeId;
		switch (conflict.getType()) {
		    case TIMEOUT:
			throw new TransactionTimeoutException(
			    accessMsg + "Transaction timed out" + conflictMsg);
		    case DENIED:
			throw new TransactionConflictException(
			    accessMsg + "Access denied" + conflictMsg);
		    case INTERRUPTED:
			throw new TransactionInterruptedException(
			    accessMsg + "Transaction interrupted" +
			    conflictMsg);
		    case DEADLOCK:
			throw new TransactionConflictException(
			    accessMsg + "Transaction deadlock" + conflictMsg);
		    default:
			throw new AssertionError(
			    "Should not be " + conflict.getType());
		    }
		}
	    }
	}
    }

    /**
     * Throws CacheConsistencyException if the node does not hold the lock for
     * the specified key, or if forWrite is true and the lock is not held for
     * write.
     */
    private void checkLocked(NodeInfo nodeInfo, Object key, boolean forWrite)
	throws CacheConsistencyException
    {
	for (LockRequest<Object, NodeInfo> lockRequest :
		 lockManager.getOwners(key))
	{
	    if (lockRequest.getLocker() == nodeInfo) {
		if (!forWrite || lockRequest.getForWrite()) {
		    return;
		}
		break;
	    }
	}
	throw new CacheConsistencyException(
	    "Node " + nodeInfo  + " does not own lock key:" + key +
	    ", forWrite:" + forWrite);
    }

    /**
     * Returns whether there is a request waiting to access to specified key
     * for read or write.
     */
    private boolean getWaiting(Object key, boolean forWrite) {
	return !lockManager.getWaiters(key).isEmpty();
    }

    /**
     * Returns information about the node with the specified ID, throwing
     * IllegalArgumentException if it is not found.
     */
    private NodeInfo getNodeInfo(long nodeId) {
	synchronized (nodeInfoMap) {
	    NodeInfo nodeInfo = nodeInfoMap.get(nodeId);
	    if (nodeInfo != null) {
		return nodeInfo;
	    }
	}
	throw new IllegalArgumentException(
	    "Node ID " + nodeId + " is not known");
    }

    /** Checks that the object ID is not negative. */
    private static void checkOid(long oid) {
	if (oid < 0) {
	    throw new IllegalArgumentException(
		"The object ID must not be negative");
	}
    }
}
