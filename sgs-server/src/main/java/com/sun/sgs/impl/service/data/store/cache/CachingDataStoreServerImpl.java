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
import static com.sun.sgs.impl.kernel.StandardProperties.APP_ROOT;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeString;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeString;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import static com.sun.sgs.impl.service.data.store.DataStoreImpl.
    DEFAULT_ENVIRONMENT_CLASS;
import static com.sun.sgs.impl.service.data.store.DataStoreImpl.
    ENVIRONMENT_CLASS_PROPERTY;
import com.sun.sgs.impl.service.data.store.DbUtilities;
import com.sun.sgs.impl.service.data.store.DbUtilities.Databases;
import com.sun.sgs.impl.service.data.store.DelegatingScheduler;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.cache.UpdateQueueRequest.
    UpdateQueueRequestHandler;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import static com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl.
    BOUNDED_TIMEOUT_DEFAULT;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractComponent;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.lock.LockConflict;
import static com.sun.sgs.impl.util.lock.LockConflictType.DEADLOCK;
import static com.sun.sgs.impl.util.lock.LockConflictType.DENIED;
import static com.sun.sgs.impl.util.lock.LockConflictType.INTERRUPTED;
import static com.sun.sgs.impl.util.lock.LockConflictType.TIMEOUT;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.TransactionInterruptedException;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.service.store.db.DbCursor;
import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbEnvironment;
import com.sun.sgs.service.store.db.DbTransaction;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * An implementation of {@code CachingDataStoreServer}. <p>
 *
 * To avoid deadlocks, the implementation needs to insure that operations that
 * obtain multiple locks from the lock manager grab the lowest key first.  In
 * particular, the methods that obtain multiple locks are {@link
 * #getBindingForUpdate} and {@link #getBindingForRemove}.
 */
public class CachingDataStoreServerImpl extends AbstractComponent
    implements CachingDataStoreServer, NodeListener, UpdateQueueServer,
    FailureReporter
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

    /** The property for specifying the server port. */
    public static final String SERVER_PORT_PROPERTY = PKG + ".server.port";

    /** The default server port. */
    public static final int DEFAULT_SERVER_PORT = 44540;

    /**
     * The property for specifying the maximum number of milliseconds to wait
     * for obtaining a lock.
     */
    public static final String LOCK_TIMEOUT_PROPERTY = PKG + ".lock.timeout";

    /**
     * The proportion of the transaction timeout to use for the lock timeout if
     * no lock timeout is specified.
     */
    public static final double DEFAULT_LOCK_TIMEOUT_PROPORTION = 0.1;

    /**
     * The default value of the lock timeout property, if no transaction
     * timeout is specified.
     */
    public static final long DEFAULT_LOCK_TIMEOUT = 
	computeLockTimeout(BOUNDED_TIMEOUT_DEFAULT);

    /**
     * The property for specifying the number of maps to use for associating
     * keys and maps.  The number of maps controls the amount of concurrency.
     */
    public static final String NUM_KEY_MAPS_PROPERTY =
	PKG + ".num.key.maps";

    /** The default number of key maps. */
    public static final int NUM_KEY_MAPS_DEFAULT = 8;

    /** The property for specifying the transaction timeout in milliseconds. */
    public static final String TXN_TIMEOUT_PROPERTY = PKG + ".txn.timeout";

    /** The default transaction timeout in milliseconds. */
    public static final long DEFAULT_TXN_TIMEOUT = 100;

    /** The property for specifying the update queue port. */
    public static final String UPDATE_QUEUE_PORT_PROPERTY =
	PKG + ".update.queue.port";

    /** The default update queue port. */
    public static final int DEFAULT_UPDATE_QUEUE_PORT = 44542;

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG + ".server"));

    /** The number of node IDs to allocate at once. */
    private static final int NODE_ID_ALLOCATION_BLOCK_SIZE = 100;

    /** The number of milliseconds to wait when allocating node IDs. */
    private static final long NODE_ID_ALLOCATION_TIMEOUT = 1000;

    /** The transaction timeout. */
    private final long txnTimeout;

    /** The transaction proxy. */
    private final TransactionProxy txnProxy;

    /** The owner for tasks run by the server. */
    private final Identity taskOwner;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

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
     * The lock manager, for managing contended access to name bindings and
     * objects.
     */
    private final MultiLockManager<Object, NodeInfo> lockManager;

    /** The port for accepting update queue connections. */
    private final int updateQueuePort;

    /** Thread that handles update queue connections. */
    private final RequestQueueListener requestQueueListener;

    /** The server port for accepting connections. */
    private final int serverPort;

    /**
     * Maps node IDs to information about the node.  Synchronize on the map
     * when accessing it.
     */
    private final Map<Long, NodeInfo> nodeInfoMap =
	new HashMap<Long, NodeInfo>();

    /** The exporter for the server. */
    private final Exporter<CachingDataStoreServer> serverExporter =
	new Exporter<CachingDataStoreServer>(CachingDataStoreServer.class);

    /**
     * A queue of node requests that are blocked and need the associated item
     * to be called back.
     */
    final BlockingQueue<NodeRequest> callbackRequests =
	new LinkedBlockingQueue<NodeRequest>();

    /**
     * The next node ID.  Synchronize on {@link #nextNodeIdSync} when accessing
     * this field.
     */
    private long nextNodeId = 0;

    /**
     * The last free node ID.  Synchronize on {@link #nextNodeIdSync} when
     * accessing this field.
     */
    private long lastNodeId = -1;

    /**
     * The synchronizer for the {@link #nextNodeId} and {@link #lastNodeId}
     * fields.
     */
    private final Object nextNodeIdSync = new Object();

    /**
     * The watchdog service, or {@code null} if not initialized.  Synchronize
     * on {@link #watchdogServiceSync} when accessing.
     */
    private WatchdogService watchdogService;

    /**
     * An exception responsible for a failure before the watchdog service
     * became available, or {@code null} if there was no failure.  Synchronize
     * on {@link #watchdogServiceSync} when accessing.
     */
    private Throwable failureBeforeWatchdog;

    /**
     * Synchronizer for {@link #watchdogService} and {@link
     * #failureBeforeWatchdog}.
     */
    private final Object watchdogServiceSync = new Object();

    /** The update queue server, wrapped for logging. */
    private final UpdateQueueServer updateQueueServer =
	new LoggingUpdateQueueServer(this, logger);

    /* -- Constructor -- */

    /**
     * Creates an instance of this class.
     *
     * @param	properties the properties for configuring this instance
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	IOException if there is a problem exporting the server or
     *		update queue
     */
    public CachingDataStoreServerImpl(Properties properties,
				      ComponentRegistry systemRegistry,
				      TransactionProxy txnProxy)
	throws IOException
    {
	super(properties, systemRegistry, txnProxy, logger);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String dbEnvClass = wrappedProps.getProperty(
	    ENVIRONMENT_CLASS_PROPERTY, DEFAULT_ENVIRONMENT_CLASS);	    
	String directory = wrappedProps.getProperty(DIRECTORY_PROPERTY);
	if (directory == null) {
	    String rootDir = properties.getProperty(APP_ROOT);
	    if (rootDir == null) {
		throw new IllegalArgumentException(
		    "A value for the property " + APP_ROOT +
		    " must be specified");
	    }
	    directory = rootDir + File.separator + DEFAULT_DIRECTORY;
	}
	/*
	 * Use an absolute path to avoid problems on Windows.
	 * -tjb@sun.com (02/16/2007)
	 */
	directory = new File(directory).getAbsolutePath();
	txnTimeout = wrappedProps.getLongProperty(
	    TXN_TIMEOUT_PROPERTY,
	    wrappedProps.getLongProperty(
		TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
		BOUNDED_TIMEOUT_DEFAULT));
	long defaultLockTimeout = (txnTimeout < 1)
	    ? DEFAULT_LOCK_TIMEOUT : computeLockTimeout(txnTimeout);
	long lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, defaultLockTimeout, 1, Long.MAX_VALUE);
	int numKeyMaps = wrappedProps.getIntProperty(
	    NUM_KEY_MAPS_PROPERTY, NUM_KEY_MAPS_DEFAULT, 1, Integer.MAX_VALUE);
	int requestedServerPort = wrappedProps.getIntProperty(
	    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	int requestedUpdateQueuePort = wrappedProps.getIntProperty(
	    UPDATE_QUEUE_PORT_PROPERTY, DEFAULT_UPDATE_QUEUE_PORT, 0, 65535);
	if (logger.isLoggable(CONFIG)) {
	    logger.log(CONFIG,
		       "Creating CachingDataStoreServerImpl with properties:" +
		       "\n  db environment class: " + dbEnvClass +
		       "\n  directory: " + directory +
		       "\n  lock timeout: " + lockTimeout +
		       "\n  num key maps: " + numKeyMaps +
		       "\n  server port: " + requestedServerPort +
		       "\n  txn timeout: " + txnTimeout +
		       "\n  update queue port: " + requestedUpdateQueuePort);
	}
	this.txnProxy = txnProxy;
	taskOwner = txnProxy.getCurrentOwner();
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	try {
	    File directoryFile = new File(directory);
	    if (!directoryFile.exists()) {
		logger.log(INFO, "Creating database directory: " + directory);
		if (!directoryFile.mkdirs()) {
		    throw new DataStoreException(
			"Unable to create database directory: " + directory);
		}
	    }
	    env = wrappedProps.getClassInstanceProperty(
		ENVIRONMENT_CLASS_PROPERTY, DEFAULT_ENVIRONMENT_CLASS,
		DbEnvironment.class,
		new Class<?>[] {
		    String.class, Properties.class, Scheduler.class },
		directory, properties,
		new DelegatingScheduler(taskScheduler, taskOwner));
	    boolean txnDone = false;
	    DbTransaction txn = env.beginTransaction(Long.MAX_VALUE);
	    try {
		Databases dbs = DbUtilities.getDatabases(env, txn, logger);
		infoDb = dbs.info();
		classesDb = dbs.classes();
		oidsDb = dbs.oids();
		namesDb = dbs.names();
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    lockManager = new MultiLockManager<Object, NodeInfo>(
		lockTimeout, numKeyMaps);
	    ServerSocket serverSocket =
		new ServerSocket(requestedUpdateQueuePort);
	    updateQueuePort = serverSocket.getLocalPort();
	    requestQueueListener = new RequestQueueListener(
		serverSocket,
		new RequestQueueListener.ServerDispatcher() {
		    public RequestQueueServer<UpdateQueueRequest> getServer(
			long nodeId)
		    {
			return getNodeInfo(nodeId).updateQueueServer;
		    }
		},
		this, properties);
	    serverPort = serverExporter.export(
		new LoggingCachingDataStoreServer(this, logger),
		"CachingDataStoreServer", requestedServerPort);
	} catch (IOException e) {
	    if (logger.isLoggable(WARNING)) {
		logger.logThrow(WARNING, e, "Problem starting server");
	    }
	    throw e;
	}
    }

    /**
     * Like {@link DataStore#ready DataStore.ready}, this method should be
     * called when services have been created, in this case to allow the server
     * to access the watchdog service.
     *
     * @throws	Exception if an error occurs 
     */
    public void ready() throws Exception {
	synchronized (watchdogServiceSync) {
	    if (failureBeforeWatchdog != null) {
		if (failureBeforeWatchdog instanceof Error) {
		    throw (Error) failureBeforeWatchdog;
		} else {
		    throw (Exception) failureBeforeWatchdog;
		}
	    }
	    watchdogService = txnProxy.getService(WatchdogService.class);
	}
	watchdogService.addNodeListener(this);
    }

    /* -- Implement CachingDataStoreServer -- */

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public RegisterNodeResult registerNode(CallbackServer callbackServer) {
	callStarted();
	try {
	    checkNull("callbackServer", callbackServer);
	    long nodeId = getNextNodeId();
	    NodeInfo nodeInfo = new NodeInfo(
		lockManager, nodeId, callbackServer,
		new RequestQueueServer<UpdateQueueRequest>(
		    nodeId,
		    new UpdateQueueRequestHandler(updateQueueServer, nodeId),
		    new Properties()));
	    synchronized (nodeInfoMap) {
		assert !nodeInfoMap.containsKey(nodeInfo.nodeId);
		nodeInfoMap.put(nodeInfo.nodeId, nodeInfo);
	    }
	    return new RegisterNodeResult(nodeInfo.nodeId, updateQueuePort);
	} finally {
	    callFinished();
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public long newObjectIds(int numIds) {
	callStarted();
	try {
	    boolean txnDone = false;
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    try {
		long result = DbUtilities.getNextObjectId(infoDb, txn, numIds);
		txnDone = true;
		txn.commit();
		return result;
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	} finally {
	    callFinished();
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public GetObjectResults getObject(long nodeId, long oid) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    lock(nodeInfo, oid, false);
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    try {
		byte[] data = oidsDb.get(txn, encodeLong(oid), false);
		txnDone = true;
		txn.commit();
		return (data == null) ? null
		    : new GetObjectResults(data, getWaiting(oid, false));
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public GetObjectForUpdateResults getObjectForUpdate(
	long nodeId, long oid)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    lock(nodeInfo, oid, true);
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
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public boolean upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    boolean found = false;
	    for (LockRequest<Object, NodeInfo> owner :
		     lockManager.getOwners(oid))
	    {
		if (nodeInfo == owner.getLocker()) {
		    if (owner.getForWrite()) {
			/* Already locked for write -- no conflict */
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
	    return getWaiting(oid, false);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public NextObjectResults nextObjectId(long nodeId, long oid) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    if (oid < -1) {
		throw new IllegalArgumentException(
		    "Object ID must not be less than -1");
	    }
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    NextObjectResults result;
	    try {
		DbCursor cursor = oidsDb.openCursor(txn);
		try {
		    boolean found = (oid == -1) ? cursor.findFirst()
			: cursor.findNext(encodeLong(oid));
		    long nextOid = !found ? -1 : decodeLong(cursor.getKey());
		    if (oid != -1 && oid == nextOid) {
			found = cursor.findNext();
			nextOid = !found ? -1 : decodeLong(cursor.getKey());
		    }
		    result = !found ? null
			: new NextObjectResults(nextOid, cursor.getValue(),
						getWaiting(oid, false));
		} finally {
		    cursor.close();
		}
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    if (result != null) {
		lock(nodeInfo, result.oid, false);
	    }
	    return result;
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public GetBindingResults getBinding(long nodeId, String name) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    String nextName;
	    boolean found;
	    long oid;
	    try {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = cursor.findNext(encodeString(name));
		    nextName = hasNext ? decodeString(cursor.getKey()) : null;
		    found = hasNext && name.equals(nextName);
		    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		} 
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    BindingKey nextNameKey = BindingKey.getAllowLast(nextName);
	    lock(nodeInfo, nextNameKey, false);
	    return new GetBindingResults(
		found, found ? null : nextName, oid,
		getWaiting(nextNameKey, false));
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public GetBindingForUpdateResults getBindingForUpdate(
	long nodeId, String name)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    String nextName = null;
	    boolean found;
	    long oid;
	    try {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = cursor.findNext(encodeString(name));
		    nextName = hasNext ? decodeString(cursor.getKey()) : null;
		    found = hasNext && name.equals(nextName);
		    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		}
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    BindingKey nameKey = BindingKey.get(name);
	    BindingKey nextNameKey = BindingKey.getAllowLast(nextName);
	    lock(nodeInfo, nameKey, true);
	    if (!found) {
		lock(nodeInfo, nextNameKey, true);
	    }
	    return new GetBindingForUpdateResults(
		found, found ? null : nextName, oid,
		getWaiting(found ? nameKey : nextNameKey, true),
		getWaiting(found ? nameKey : nextNameKey, false));
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public GetBindingForRemoveResults getBindingForRemove(
	long nodeId, String name)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    String nextName;
	    long oid;
	    long nextOid;
	    try {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = cursor.findNext(encodeString(name));
		    nextName = hasNext ? decodeString(cursor.getKey()) : null;
		    if (hasNext && name.equals(nextName)) {
			oid = decodeLong(cursor.getValue());
			/* Move to name after matching name */
			hasNext = cursor.findNext();
			nextName =
			    hasNext ? decodeString(cursor.getKey()) : null;
		    } else {
			oid = -1;
		    }
		    nextOid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		}
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    BindingKey nameKey = BindingKey.get(name);
	    BindingKey nextNameKey = BindingKey.getAllowLast(nextName);
	    boolean found = (oid != -1);
	    if (found) {
		lock(nodeInfo, nameKey, true);
	    }
	    lock(nodeInfo, nextNameKey, oid != -1);
	    return new GetBindingForRemoveResults(
		found, nextName, oid,
		found && getWaiting(nameKey, true),
		found && getWaiting(nameKey, false),
		nextOid,
		getWaiting(nextNameKey, true),
		getWaiting(nextNameKey, false));
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public NextBoundNameResults nextBoundName(long nodeId, String name) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    long oid;
	    String nextName;
	    boolean txnDone = false;
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    try {
		DbCursor cursor = namesDb.openCursor(txn);
		try {
		    boolean hasNext = (name == null) ? cursor.findFirst()
			: cursor.findNext(encodeString(name));
		    nextName = hasNext ? decodeString(cursor.getKey()) : null;
		    if ((name != null) && name.equals(nextName)) {
			hasNext = cursor.findNext();
			nextName = (hasNext) ? decodeString(cursor.getKey())
			    : null;
		    }   
		    oid = hasNext ? decodeLong(cursor.getValue()) : -1;
		} finally {
		    cursor.close();
		}
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    BindingKey nextNameKey = BindingKey.getAllowLast(nextName);
	    lock(nodeInfo, nextNameKey, false);
	    return new NextBoundNameResults(
		nextName, oid, getWaiting(nextNameKey, false));
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc} 
     *
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public int getClassId(byte[] classInfo) {
	callStarted();
	try {
	    return DbUtilities.getClassId(
		env, classesDb, classInfo, txnTimeout);
	} finally {
	    callFinished();
	}
    }
	    
    /**
     * {@inheritDoc} 
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	IOException {@inheritDoc}
     */
    @Override
    public byte[] getClassInfo(int classId) {
	callStarted();
	try {
	    return DbUtilities.getClassInfo(
		env, classesDb, classId, txnTimeout);
	} finally {
	    callFinished();
	}
    }

    /* -- Implement UpdateQueueServer -- */

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     */
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
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    throw new CacheConsistencyException(e.getMessage(), e);
	}
	try {
	    checkNull("oids", oids);
	    checkNull("oidValues", oidValues);
	    if (oids.length != oidValues.length) {
		throw new IllegalArgumentException(
		    "The number of object IDs and OID values" +
		    " must be the same");
	    }
	    if (newOids < 0 || newOids > oids.length) {
		throw new IllegalArgumentException(
		    "Illegal newOids: " + newOids);
	    }
	    checkNull("names", names);
	    checkNull("nameValues", nameValues);
	    if (names.length != nameValues.length) {
		throw new IllegalArgumentException(
		    "The number of names and name values must be the same");
	    }
	    if (newNames < 0 || newNames > names.length) {
		throw new IllegalArgumentException(
		    "Illegal newNames: " + newNames);
	    }
	    boolean txnDone = false;
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    try {
		for (int i = 0; i < oids.length; i++) {
		    long oid = oids[i];
		    if (oid < 0) {
			throw new IllegalArgumentException(
			    "The object IDs must not be negative");
		    }
		    if (i < newOids) {
			lock(nodeInfo, oid, true);
		    } else {
			checkLocked(nodeInfo, oid, true);
		    }
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
		    BindingKey key = BindingKey.get(name);
		    if (i < newNames) {
			lock(nodeInfo, key, true);
		    } else {
			checkLocked(nodeInfo, key, true);
		    }
		    long value = nameValues[i];
		    if (value < -1) {
			throw new IllegalArgumentException(
			    "The name values must not be less than -1");
		    } else if (value == -1) {
			namesDb.delete(txn, encodeString(name));
		    } else {
			namesDb.put(
			    txn, encodeString(name), encodeLong(value));
		    }
		}
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    public void evictObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    throw new CacheConsistencyException(e.getMessage(), e);
	}
	try {
	    checkOid(oid);
	    checkLocked(nodeInfo, oid, false);
	    releaseLock(nodeInfo, oid);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    public void downgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    throw new CacheConsistencyException(e.getMessage(), e);
	}
	try {
	    checkOid(oid);
	    checkLocked(nodeInfo, oid, true);
	    lockManager.downgradeLock(nodeInfo, oid);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    public void evictBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    throw new CacheConsistencyException(e.getMessage(), e);
	}
	try {
	    BindingKey nameKey = BindingKey.getAllowLast(name);
	    checkLocked(nodeInfo, nameKey, false);
	    releaseLock(nodeInfo, nameKey);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     */
    @Override
    public void downgradeBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    throw new CacheConsistencyException(e.getMessage(), e);
	}
	try {
	    BindingKey nameKey = BindingKey.getAllowLast(name);
	    checkLocked(nodeInfo, nameKey, true);
	    lockManager.downgradeLock(nodeInfo, nameKey);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /* -- Implement NodeListener -- */

    /** {@inheritDoc} */
    @Override
    public void nodeFailed(Node node) {
	/*
	 * Note that we may want to insure that the data store is shutdown for
	 * a particular node before marking the node as not alive.  That would
	 * insure that operations for a failed node were not still underway
	 * even though the watchdog considers it to have failed.
	 * -tjb@sun.com (07/27/2009)
	 */
	shutdownNode(node.getId());
    }

    /** {@inheritDoc} */
    @Override
    public void nodeStarted(Node node) { }

    /* -- Implement FailureReporter -- */

    /** {@inheritDoc} */
    @Override
    public void reportFailure(Throwable exception) {
	logger.logThrow(
	    WARNING, exception, "CachingDataStoreServerImpl failed");
	synchronized (watchdogServiceSync) {
	    if (watchdogService == null) {
		if (failureBeforeWatchdog != null) {
		    failureBeforeWatchdog = exception;
		}
	    } else {
		Thread thread =
		    new Thread("CachingDataStoreServerImpl reportFailure") {
			public void run() {
			    watchdogService.reportFailure(
				watchdogService.getLocalNodeId(),
				CachingDataStoreServerImpl.class.getName());
			}
		    };
		thread.start();
	    }
	}
    }

    /* -- Other public methods -- */

    /**
     * Mark the node with the specified ID as shutdown and schedule a task to
     * release all of its locks.  Does nothing if the node is not found.
     *
     * @param	nodeId the ID of the failed node
     */
    public void shutdownNode(long nodeId) {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = getNodeInfo(nodeId);
	} catch (IllegalArgumentException e) {
	    return;
	}
	if (nodeInfo.shutdown()) {
	    synchronized (nodeInfoMap) {
		nodeInfoMap.remove(nodeId);
	    }
	    scheduleTask(new ReleaseNodeLocksTask(nodeInfo));
	}
    }

    /** A {@link KernelRunnable} that releases the locks owned by a node. */
    private static class ReleaseNodeLocksTask extends AbstractKernelRunnable {
	private final NodeInfo nodeInfo;
	ReleaseNodeLocksTask(NodeInfo nodeInfo) {
	    super(null);
	    this.nodeInfo = nodeInfo;
	}
	@Override
	public void run() {
	    nodeInfo.releaseAllLocks();
	}
    }

    /* -- AbstractComponent methods -- */

    /** {@inheritDoc} */
    @Override
    protected void doShutdown() {
	infoDb.close();
	classesDb.close();
	oidsDb.close();
	namesDb.close();
	env.close();
    }

    /* -- Other methods -- */

    /**
     * Returns the port the server is using to accept connections.
     *
     * @return	the server port
     */
    public int getServerPort() {
	return serverPort;
    }

    /**
     * Obtains a lock on behalf of the node with the specified ID.  Use this
     * method rather than calling the lock manager to make sure that callbacks
     * and lock tracking are performed correctly.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item to be locked
     * @param	forWrite whether the item should be locked for write
     */
    private void lock(NodeInfo nodeInfo, Object key, boolean forWrite) {
	assert key instanceof Long || key instanceof BindingKey;
	synchronized (nodeInfo) {
	    LockConflict<Object, NodeInfo> conflict =
		lockManager.lockNoWait(nodeInfo, key, forWrite);
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
	    nodeInfo.noteLocked(key);
	}
    }

    /**
     * Releases a lock owned by a node.  Use this method rather than calling
     * the lock manager directly to make sure that locking tracking is
     * performed correctly.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item whose lock should be released
     */
    private void releaseLock(NodeInfo nodeInfo, Object key) {
	assert key instanceof Long || key instanceof BindingKey;
	lockManager.releaseLock(nodeInfo, key);
	nodeInfo.noteUnlocked(key);
    }

    /**
     * Throws CacheConsistencyException if the node does not hold the lock for
     * the specified key, or if forWrite is true and the lock is not held for
     * write.
     */
    private void checkLocked(NodeInfo nodeInfo, Object key, boolean forWrite)
	throws CacheConsistencyException
    {
	assert key instanceof Long || key instanceof BindingKey;
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
     * Returns information about the node with the specified ID.  Throws
     * IllegalArgumentException if the node is not found.  Throws
     * IllegalStateException if the server is shutdown or if the node is marked
     * as failed.  Otherwise, increments the call count and the count of calls
     * active for the node.
     */
    private NodeInfo nodeCallStarted(long nodeId) {
	callStarted();
	boolean done = false;
	try {
	    NodeInfo nodeInfo = getNodeInfo(nodeId);
	    nodeInfo.nodeCallStarted();
	    return nodeInfo;
	} finally {
	    if (!done) {
		callFinished();
	    }
	}
    }

    private void nodeCallFinished(NodeInfo nodeInfo) {
	nodeInfo.nodeCallFinished();
	callFinished();
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

    /**
     * Returns an identifier for a new node.
     *
     * @return	the new node ID
     */
    private long getNextNodeId() {
	synchronized (nextNodeIdSync) {
	    if (nextNodeId > lastNodeId) {
		boolean txnDone = false;
		DbTransaction txn =
		    env.beginTransaction(NODE_ID_ALLOCATION_TIMEOUT);
		try {
		    nextNodeId = DbUtilities.getNextNodeId(
			infoDb, txn, NODE_ID_ALLOCATION_BLOCK_SIZE);
		    txnDone = true;
		    txn.commit();
		} finally {
		    if (!txnDone) {
			txn.abort();
		    }
		}
		lastNodeId = nextNodeId + NODE_ID_ALLOCATION_BLOCK_SIZE - 1;
	    }
	    return nextNodeId++;
	}
    }

    /**
     * Computes the lock timeout based on the specified transaction timeout and
     * {@link #DEFAULT_LOCK_TIMEOUT_PROPORTION}.
     */
    private static long computeLockTimeout(long txnTimeout) {
	long result = (long) (txnTimeout * DEFAULT_LOCK_TIMEOUT_PROPORTION);
	/* Lock timeout should be at least 1 */
	if (result < 1) {
	    result = 1;
	}
	return result;
    }

    /**
     * Schedules a task to run immediately.
     *
     * @param	task the task
     */
    private void scheduleTask(KernelRunnable task) {
	taskScheduler.scheduleTask(task, taskOwner);
    }

    /* -- Nested classes -- */

    /**
     * A {@code Runnable} that carries out callbacks queued to the {@link
     * #callbackRequests} field.
     */
    private class CallbackRequester implements Runnable {

	/** Creates an instance of this class. */
	CallbackRequester() { }

	@Override
	public void run() {
	    while (!shuttingDown()) {
		try {
		    callbackOwners(callbackRequests.take());
		} catch (InterruptedException e) {
		    continue;
		}
	    }
	}

	/**
	 * Sends callback requests to all of the owners of the requested lock,
	 * unless the server is shutting down.  Abandons further callbacks if
	 * the requesting node is marked as failed.
	 *
	 * @param	request the blocked request whose owners should receive
	 *		callback requests
	 */
	private void callbackOwners(NodeRequest request) {
	    try {
		callStarted();
	    } catch (IllegalStateException e) {
		return;
	    }
	    try {
		NodeInfo nodeInfo = request.getLocker();
		nodeInfo.nodeCallStarted();
		try {
		    for (LockRequest<Object, NodeInfo> owner
			     : lockManager.getOwners(request.getKey()))
		    {
			if (nodeInfo != owner.getLocker()) {
			    callback((NodeRequest) owner, request);
			}
		    }
		} finally {
		    nodeInfo.nodeCallFinished();
		}
	    } finally {
		callFinished();
	    }
	}

	/**
	 * Sends a callback request to the owner on behalf of the requester.
	 */
	private void callback(NodeRequest owner, NodeRequest request) {
	    if (owner.noteCallback()) {
		boolean downgrade =
		    owner.getForWrite() && !request.getForWrite();
		NodeInfo ownerNodeInfo = owner.getLocker();
		Object key = request.getKey();
		CallbackTask task = new CallbackTask(
		    ownerNodeInfo.callbackServer, key, downgrade,
		    request.getLocker().nodeId);
		runIoTask(task, ownerNodeInfo.nodeId);
		if (task.released) {
		    if (downgrade) {
			lockManager.downgradeLock(ownerNodeInfo, key);
		    } else {
			releaseLock(ownerNodeInfo, key);
		    }
		}
	    }
	}
    }

    /** Performs a callback. */
    private static class CallbackTask implements IoRunnable {

	/** The callback server. */
	private final CallbackServer callbackServer;

	/** The key of the item being called back. */
	private final Object key;

	/** Whether to perform a downgrade. */
	private final boolean downgrade;

	/** The node ID of the node requesting the callback. */
	private final long requesterNodeId;

	/**
	 * Whether the node released the item immediately in response to the
	 * callback request.
	 */
	boolean released;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	callbackServer the callback server
	 * @param	key the key of the item being called back
	 * @param	downgrade whether to perform a downgrade
	 * @param	node ID the node ID of the node requesting the callback
	 */
	CallbackTask(CallbackServer callbackServer,
		     Object key,
		     boolean downgrade,
		     long requesterNodeId)
	{
	    this.callbackServer = callbackServer;
	    this.key = key;
	    this.downgrade = downgrade;
	    this.requesterNodeId = requesterNodeId;
	}

	@Override
	public void run() throws IOException {
	    if (key instanceof BindingKey) {
		String name = ((BindingKey) key).getNameAllowLast();
		if (downgrade) {
		    released = callbackServer.requestDowngradeBinding(
			name, requesterNodeId);
		} else {
		    released = callbackServer.requestEvictBinding(
			name, requesterNodeId);
		}
	    } else {
		if (downgrade) {
		    released = callbackServer.requestDowngradeObject(
			(Long) key, requesterNodeId);
		} else {
		    released = callbackServer.requestEvictObject(
			(Long) key, requesterNodeId);
		}
	    }
	}
    }
}
