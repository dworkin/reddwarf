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

import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import static com.sun.sgs.impl.kernel.StandardProperties.APP_ROOT;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.decodeString;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeLong;
import static com.sun.sgs.impl.service.data.store.DataEncoding.encodeString;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import static com.sun.sgs.impl.service.data.store.
    DataStoreImpl.DEFAULT_ENVIRONMENT_CLASS;
import static com.sun.sgs.impl.service.data.store.
    DataStoreImpl.ENVIRONMENT_CLASS_PROPERTY;
import com.sun.sgs.impl.service.data.store.DbUtilities;
import com.sun.sgs.impl.service.data.store.DbUtilities.Databases;
import com.sun.sgs.impl.service.data.store.cache.BindingKey;
import com.sun.sgs.impl.service.data.store.cache.CacheConsistencyException;
import com.sun.sgs.impl.service.data.store.cache.CallbackServer;
import com.sun.sgs.impl.service.data.store.cache.FailureReporter;
import com.sun.sgs.impl.service.data.store.cache.queue.LoggingUpdateQueueServer;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueListener;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer;
import com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueueRequest;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.UpdateQueueRequestHandler;
import com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueueServer;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import static com.sun.sgs.impl.service.transaction.
    TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractBasicService;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.impl.util.NamedThreadFactory;
import com.sun.sgs.impl.util.lock.LockConflict;
import static com.sun.sgs.impl.util.lock.LockConflictType.DEADLOCK;
import static com.sun.sgs.impl.util.lock.LockConflictType.DENIED;
import static com.sun.sgs.impl.util.lock.LockConflictType.INTERRUPTED;
import static com.sun.sgs.impl.util.lock.LockConflictType.TIMEOUT;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.DataService;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/*
 * TBD: Add profiling.  -tjb@sun.com (11/17/2009)
 *
 * TBD: Maybe shutdown node immediately when cache consistency is found.
 * -tjb@sun.com (01/20/2010)
 *
 * TBD: Consider using a data structure that supports range-locking, to avoid
 * looping to lock the next binding key.  -tjb@sun.com (01/20/2010)
 */

/**
 * An implementation of {@code CachingDataStoreServer}. <p>
 *
 * The {@link #CachingDataStoreServerImpl constructor} supports the following
 * configuration properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #DIRECTORY_PROPERTY}</b></code> <br>
 *	<i>Default:</i> <code><i>${com.sun.sgs.app.root}</i>/dsdb</code>
 *
 * <dd style="padding-top: .5em">The directory in which to store database
 *	files. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #LOCK_TIMEOUT_PROPERTY}</b></code>
 *	<br>
 *	<i>Default:</i> <code>{@value #DEFAULT_LOCK_TIMEOUT_PROPORTION}</code>
 *	times the transaction timeout
 *
 * <dd style="padding-top: .5em">The maximum amount of time in milliseconds
 *	that an attempt to obtain a lock will be allowed to continue before
 *	being aborted.  The value must be greater than <code>0</code>, and
 *	should be less than the transaction timeout. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #MAX_RETRY_PROPERTY}</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_MAX_RETRY}</code>
 *
 * <dd style="padding-top: .5em">The number of milliseconds to continue
 *	retrying I/O operations before determining that the failure is
 *	permanent.  The value must be non-negative. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #NUM_CALLBACK_THREADS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_NUM_CALLBACK_THREADS}</code>
 *
 * <dd style="padding-top: .5em">The number of threads to use for performing
 *	callbacks.  The value must be greater than <code>0</code>. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #NUM_KEY_MAPS_PROPERTY}</b></code>
 *	<br>
 *	<i>Default:</i> <code>{@value #DEFAULT_NUM_KEY_MAPS}</code>
 *
 * <dd style="padding-top: .5em">The number of maps to use for associating keys
 *	and maps.  The value must be greater than <code>0</code>.  The number
 *	of maps controls the amount of concurrency. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #RETRY_WAIT_PROPERTY}</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_RETRY_WAIT}</code>
 *
 * <dd style="padding-top: .5em">The number of milliseconds to wait before
 *	retrying a failed I/O operation.  The value must be non-negative. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #SERVER_PORT_PROPERTY}</b></code><br>
 *	<i>Default:</i>	<code>{@value #DEFAULT_SERVER_PORT}</code>
 *
 * <dd style="padding-top: .5em">The network port used to accept server
 *	requests.  The value should be a non-negative number less than
 *	<code>65536</code>.  If the value specified is <code>0</code>, then an
 *	anonymous port will be chosen. <p>

 * <dt> <i>Property:</i> <code><b>{@value #TXN_TIMEOUT_PROPERTY}</b></code><br>
 *	<i>Default:</i> The value of the <code>{@value
 *	com.sun.sgs.impl.service.transaction.TransactionCoordinator#TXN_TIMEOUT_PROPERTY}
 *	</code> property, if specified, or else <code>{@value
 *	com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl#BOUNDED_TIMEOUT_DEFAULT}
 *	</code>
 *
 * <dd style="padding-top: .5em">The transaction timeout in milliseconds. <p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #UPDATE_QUEUE_PORT_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_UPDATE_QUEUE_PORT}</code>
 *
 * <dd style="padding-top: .5em">The network port used to accept requests to
 *	the server's update queue.  The value should be a non-negative number
 *	less than <code>65536</code>.  If the value specified is
 *	<code>0</code>, then an anonymous port will be chosen. <p>
 *
 * </dl> <p>
 *
 * To avoid deadlocks, the implementation needs to insure that operations that
 * obtain multiple locks from the lock manager grab the lowest key first.  In
 * particular, the methods that obtain multiple locks are {@link
 * #getBindingForUpdate} and {@link #getBindingForRemove}.
 */
public class CachingDataStoreServerImpl extends AbstractBasicService
    implements CachingDataStoreServer, NodeListener, UpdateQueueServer,
    FailureReporter
{
    /** The package for this class. */
    private static final String PKG =
	"com.sun.sgs.impl.service.data.store.cache.server";

    /** The name of this class. */
    private static final String CLASSNAME =
	CachingDataStoreServerImpl.class.getName();

    /** The property for specifying debugging output. */
    public static final String DEBUG_OUTPUT_PROPERTY = PKG + ".debug";

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    public static final String DIRECTORY_PROPERTY = PKG + ".directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /** The property for specifying the server port. */
    public static final String SERVER_PORT_PROPERTY = PKG + ".port";

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
    public static final double DEFAULT_LOCK_TIMEOUT_PROPORTION = 0.2;

    /**
     * The default value of the lock timeout property, if no transaction
     * timeout is specified.
     */
    public static final long DEFAULT_LOCK_TIMEOUT =
	computeLockTimeout(BOUNDED_TIMEOUT_DEFAULT);

    /**
     * The property for specifying the number of milliseconds to continue
     * retrying I/O operations before determining that the failure is
     * permanent.
     */
    public static final String MAX_RETRY_PROPERTY = PKG + ".max.retry";

    /** The default maximum retry, in milliseconds. */
    public static final long DEFAULT_MAX_RETRY = 1000;

    /**
     * The property for specifying the number of threads to use for performing
     * callbacks.
     */
    public static final String NUM_CALLBACK_THREADS_PROPERTY =
	PKG + ".num.callback.threads";

    /** The default number of callback threads. */
    public static final int DEFAULT_NUM_CALLBACK_THREADS = 4;

    /**
     * The property for specifying the number of maps to use for associating
     * keys and maps.  The number of maps controls the amount of concurrency.
     */
    public static final String NUM_KEY_MAPS_PROPERTY =
	PKG + ".num.key.maps";

    /** The default number of key maps. */
    public static final int DEFAULT_NUM_KEY_MAPS = 8;

    /**
     * The property for specifying the number of milliseconds to wait before
     * retrying a failed I/O operation.
     */
    public static final String RETRY_WAIT_PROPERTY = PKG + ".retry.wait";

    /** The default retry wait, in milliseconds. */
    public static final long DEFAULT_RETRY_WAIT = 10;

    /** The property for specifying the transaction timeout in milliseconds. */
    public static final String TXN_TIMEOUT_PROPERTY = PKG + ".txn.timeout";

    /** The property for specifying the update queue port. */
    public static final String UPDATE_QUEUE_PORT_PROPERTY =
	PKG + ".update.queue.port";

    /** The default update queue port. */
    public static final int DEFAULT_UPDATE_QUEUE_PORT = 0;

    /** The number of node IDs to allocate at once. */
    private static final int NODE_ID_ALLOCATION_BLOCK_SIZE = 100;

    /**
     * The number of times to retry locking the next binding keys in the face
     * earlier keys being added concurrently before throwing a resource
     * exception.
     */
    private static final int MAX_RANGE_LOCK_RETRIES = 100;

    /** The transaction timeout. */
    private final long txnTimeout;

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

    /** Whether to print debugging output. */
    private final boolean debug;

    /**
     * The lock manager, for managing contended access to name bindings and
     * objects.
     */
    private final MultiLockManager<Object> lockManager;

    /** The port for accepting update queue connections. */
    private final int updateQueuePort;

    /** The update queue server, wrapped for logging. */
    private final UpdateQueueServer updateQueueServer;

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
     * A queue of requests that are blocked and need the associated item to be
     * called back.
     */
    final BlockingQueue<CallbackRequest> callbackRequests =
	new LinkedBlockingQueue<CallbackRequest>();

    /** A thread pool for performing callback requests. */
    private final ExecutorService callbackExecutor;

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
     * The data service, or {@code null} if not initialized.  Synchronize on
     * {@link #readySync} when accessing.
     */
    private DataService dataService = null;

    /**
     * The watchdog service, or {@code null} if not initialized.  Synchronize
     * on {@link #readySync} when accessing.
     */
    private WatchdogService watchdogService = null;

    /**
     * An exception responsible for a failure before the watchdog service
     * became available, or {@code null} if there was no failure.  Synchronize
     * on {@link #readySync} when accessing.
     */
    private Throwable failureBeforeWatchdog = null;

    /**
     * Synchronizer for {@link #dataService}, {@link #watchdogService}, and
     * {@link #failureBeforeWatchdog}.
     */
    private final Object readySync = new Object();

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
	super(properties, systemRegistry, txnProxy,
	      new LoggerWrapper(Logger.getLogger(CLASSNAME)));
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String dbEnvClass = wrappedProps.getProperty(
	    ENVIRONMENT_CLASS_PROPERTY, DEFAULT_ENVIRONMENT_CLASS);
	debug = wrappedProps.getBooleanProperty(DEBUG_OUTPUT_PROPERTY, false);
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
	long maxRetry = wrappedProps.getLongProperty(
	    MAX_RETRY_PROPERTY, DEFAULT_MAX_RETRY, 0, Long.MAX_VALUE);
	int numCallbackThreads = wrappedProps.getIntProperty(
	    NUM_CALLBACK_THREADS_PROPERTY, DEFAULT_NUM_CALLBACK_THREADS, 1,
	    Integer.MAX_VALUE);
	int numKeyMaps = wrappedProps.getIntProperty(
	    NUM_KEY_MAPS_PROPERTY, DEFAULT_NUM_KEY_MAPS, 1, Integer.MAX_VALUE);
	int requestedServerPort = wrappedProps.getIntProperty(
	    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 0, 65535);
	int requestedUpdateQueuePort = wrappedProps.getIntProperty(
	    UPDATE_QUEUE_PORT_PROPERTY, DEFAULT_UPDATE_QUEUE_PORT, 0, 65535);
	long retryWait = wrappedProps.getLongProperty(
	    RETRY_WAIT_PROPERTY, DEFAULT_RETRY_WAIT, 0, Long.MAX_VALUE);
	if (logger.isLoggable(CONFIG)) {
	    logger.log(
		CONFIG,
		"Creating CachingDataStoreServerImpl with properties:" +
		"\n  " + ENVIRONMENT_CLASS_PROPERTY + "=" + dbEnvClass +
		"\n  " + DEBUG_OUTPUT_PROPERTY + "=" + debug +
		"\n  " + DIRECTORY_PROPERTY + "=" + directory +
		"\n  " + LOCK_TIMEOUT_PROPERTY + "=" + lockTimeout +
		"\n  " + MAX_RETRY_PROPERTY + "=" + maxRetry +
		"\n  " + NUM_CALLBACK_THREADS_PROPERTY + "=" +
		numCallbackThreads +
		"\n  " + NUM_KEY_MAPS_PROPERTY + "=" + numKeyMaps +
		"\n  " + RETRY_WAIT_PROPERTY + "=" + retryWait +
		"\n  " + SERVER_PORT_PROPERTY + "=" + requestedServerPort +
		"\n  " + TXN_TIMEOUT_PROPERTY + "=" + txnTimeout +
		"\n  " + UPDATE_QUEUE_PORT_PROPERTY + "=" +
		requestedUpdateQueuePort);
	}
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
		    String.class, Properties.class,
		    ComponentRegistry.class, TransactionProxy.class },
		directory, properties, systemRegistry, txnProxy);
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
	    lockManager =
		new MultiLockManager<Object>(lockTimeout, numKeyMaps);
	    ServerSocket serverSocket =
		new ServerSocket(requestedUpdateQueuePort);
	    updateQueuePort = serverSocket.getLocalPort();
	    updateQueueServer = new LoggingUpdateQueueServer(this, logger);
	    requestQueueListener = new RequestQueueListener(
		serverSocket,
		new RequestQueueListener.ServerDispatcher() {
		    public RequestQueueServer<UpdateQueueRequest> getServer(
			long nodeId)
		    {
			return getNodeInfo(nodeId).updateQueueServer;
		    }
		},
		this, maxRetry, retryWait);
	    serverPort = serverExporter.export(
		new LoggingCachingDataStoreServer(this, logger),
		"CachingDataStoreServer", requestedServerPort);
	    callbackExecutor = Executors.newCachedThreadPool(
		new NamedThreadFactory(CLASSNAME + ".callback-"));
	    for (int i = 0; i < numCallbackThreads; i++) {
		callbackExecutor.execute(new CallbackRequester());
	    }
	} catch (IOException e) {
	    if (logger.isLoggable(WARNING)) {
		logger.logThrow(WARNING, e, "Problem starting server");
	    }
	    doShutdown();
	    throw e;
	} catch (RuntimeException e) {
	    if (logger.isLoggable(WARNING)) {
		logger.logThrow(WARNING, e, "Problem starting server");
	    }
	    doShutdown();
	    throw e;
	} catch (Error e) {
	    if (logger.isLoggable(WARNING)) {
		logger.logThrow(WARNING, e, "Problem starting server");
	    }
	    doShutdown();
	    throw e;
	}
    }

    /* -- Implement CachingDataStoreServer -- */

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}
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
		    new UpdateQueueRequestHandler(updateQueueServer, nodeId)));
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
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public long newObjectIds(int numIds) {
	callStarted();
	try {
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
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
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public GetObjectResults getObject(long nodeId, long oid) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    lock(nodeInfo, oid, false, "getObject");
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    try {
		byte[] data = oidsDb.get(txn, encodeLong(oid), false);
		txnDone = true;
		txn.commit();
		return (data == null) ? null
		    : new GetObjectResults(
			data, getWaiting(oid) == GetWaitingResult.WRITERS);
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
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public GetObjectForUpdateResults getObjectForUpdate(
	long nodeId, long oid)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    lock(nodeInfo, oid, true, "getObjectForUpdate");
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    try {
		byte[] result = oidsDb.get(txn, encodeLong(oid), true);
		txnDone = true;
		txn.commit();
		GetWaitingResult waiters = getWaiting(oid);
		return (result == null) ? null
		    : new GetObjectForUpdateResults(
			result, waiters == GetWaitingResult.WRITERS,
			waiters == GetWaitingResult.READERS);
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
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public UpgradeObjectResults upgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkOid(oid);
	    boolean found = false;
	    for (LockRequest<Object> owner : lockManager.getOwners(oid)) {
		if (nodeInfo == owner.getLocker()) {
		    found = true;
		    if (!owner.getForWrite()) {
			lock(nodeInfo, oid, true, "upgradeObject");
		    }
		    break;
		}
	    }
	    if (!found) {
		CacheConsistencyException exception =
		    new CacheConsistencyException(
			"Node " + nodeId + " attempted to upgrade object " +
			oid + ", but does not own that object for read");
		logger.logThrow(
		    WARNING, exception, "Cache consistency failure");
		throw exception;
	    }
	    DbTransaction txn = env.beginTransaction(txnTimeout);
	    boolean txnDone = false;
	    try {
		oidsDb.markForUpdate(txn, encodeLong(oid));
		txnDone = true;
		txn.commit();
	    } finally {
		if (!txnDone) {
		    txn.abort();
		}
	    }
	    GetWaitingResult waiting = getWaiting(oid);
	    return new UpgradeObjectResults(
		waiting == GetWaitingResult.WRITERS,
		waiting == GetWaitingResult.READERS);
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
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
			: new NextObjectResults(
			    nextOid, cursor.getValue(),
			    getWaiting(oid) == GetWaitingResult.WRITERS);
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
		lock(nodeInfo, result.oid, false, "nextObjectId");
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
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public GetBindingResults getBinding(long nodeId, String name) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    GetBindingResults results = null;
	    BindingKey nextNameKey = null;
	    /*
	     * Since the implementation doesn't have range locking, find the
	     * next key, lock it, and confirm that it is indeed the next key
	     * after locking it, repeating as necessary.
	     */
	    for (int i = 0; true; i++) {
		if (i > MAX_RANGE_LOCK_RETRIES) {
		    throw new ResourceUnavailableException("Too many retries");
		}
		DbTransaction txn = env.beginTransaction(txnTimeout);
		boolean txnDone = false;
		String nextName;
		boolean found;
		long oid;
		try {
		    DbCursor cursor = namesDb.openCursor(txn);
		    try {
			boolean hasNext = cursor.findNext(encodeString(name));
			nextName =
			    hasNext ? decodeString(cursor.getKey()) : null;
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
		if (results != null &&
		    found == results.found &&
		    safeEquals(found ? null : nextName, results.nextName))
		{
		    return results;
		}
		/*
		 * TBD: Maybe include all locks obtained in the results?  Right
		 * now, the node doesn't hear about any additional locks that
		 * are grabbed while trying to find the true next key.
		 * -tjb@sun.com (01/20/2010)
		 */
		nextNameKey = BindingKey.getAllowLast(nextName);
		lock(nodeInfo, nextNameKey, false, "getBinding",
		     found ? null : name);
		results = new GetBindingResults(
		    found, found ? null : nextName, oid,
		    getWaiting(nextNameKey) == GetWaitingResult.WRITERS);
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public GetBindingForUpdateResults getBindingForUpdate(
	long nodeId, String name)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    GetBindingForUpdateResults results = null;
	    BindingKey key = null;
	    for (int i = 0; true; i++) {
		if (i > MAX_RANGE_LOCK_RETRIES) {
		    throw new ResourceUnavailableException("Too many retries");
		}
		DbTransaction txn = env.beginTransaction(txnTimeout);
		boolean txnDone = false;
		String nextName;
		boolean found;
		long oid;
		try {
		    DbCursor cursor = namesDb.openCursor(txn);
		    try {
			boolean hasNext = cursor.findNext(encodeString(name));
			nextName =
			    hasNext ? decodeString(cursor.getKey()) : null;
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
		if (results != null &&
		    found == results.found &&
		    safeEquals(found ? null : nextName, results.nextName))
		{
		    return results;
		}
		key = found ?
		    BindingKey.get(name) : BindingKey.getAllowLast(nextName);
		lock(nodeInfo, key, true, "getBindingUpdate",
		     found ? null : name);
		GetWaitingResult waiting = getWaiting(key);
		results = new GetBindingForUpdateResults(
		    found, found ? null : nextName, oid,
		    waiting == GetWaitingResult.WRITERS,
		    waiting == GetWaitingResult.READERS);
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public GetBindingForRemoveResults getBindingForRemove(
	long nodeId, String name)
    {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    checkNull("name", name);
	    GetBindingForRemoveResults results = null;
	    BindingKey nameKey = BindingKey.get(name);
	    BindingKey nextNameKey = null;
	    for (int i = 0; true; i++) {
		if (i > MAX_RANGE_LOCK_RETRIES) {
		    throw new ResourceUnavailableException("Too many retries");
		}
		DbTransaction txn = env.beginTransaction(txnTimeout);
		boolean txnDone = false;
		String nextName;
		long oid;
		long nextOid;
		try {
		    DbCursor cursor = namesDb.openCursor(txn);
		    try {
			boolean hasNext = cursor.findNext(encodeString(name));
			nextName =
			    hasNext ? decodeString(cursor.getKey()) : null;
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
		boolean found = (oid != -1);
		if (results != null &&
		    found == results.found &&
		    safeEquals(nextName, results.nextName))
		{
		    return results;
		}
		nextNameKey = BindingKey.getAllowLast(nextName);
		if (found) {
		    lock(nodeInfo, nameKey, true, "getBindingRemove");
		}
		lock(nodeInfo, nextNameKey, found, "getBindingRemove", name);
		GetWaitingResult waitingName = getWaiting(nameKey);
		GetWaitingResult waitingNextName = getWaiting(nextNameKey);
		results = new GetBindingForRemoveResults(
		    found, nextName, oid,
		    found && waitingName == GetWaitingResult.WRITERS,
		    found && waitingName == GetWaitingResult.READERS,
		    nextOid,
		    waitingNextName == GetWaitingResult.WRITERS,
		    waitingNextName == GetWaitingResult.READERS);
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public NextBoundNameResults nextBoundName(long nodeId, String name) {
	NodeInfo nodeInfo = nodeCallStarted(nodeId);
	try {
	    NextBoundNameResults results = null;
	    BindingKey nextNameKey = null;
	    for (int i = 0; true; i++) {
		if (i > MAX_RANGE_LOCK_RETRIES) {
		    throw new ResourceUnavailableException("Too many retries");
		}
		DbTransaction txn = env.beginTransaction(txnTimeout);
		boolean txnDone = false;
		long oid;
		String nextName;
		try {
		    DbCursor cursor = namesDb.openCursor(txn);
		    try {
			boolean hasNext = (name == null) ? cursor.findFirst()
			    : cursor.findNext(encodeString(name));
			nextName =
			    hasNext ? decodeString(cursor.getKey()) : null;
			if ((name != null) && name.equals(nextName)) {
			    hasNext = cursor.findNext();
			    nextName = (hasNext)
				? decodeString(cursor.getKey()) : null;
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
		if (results != null &&
		    safeEquals(nextName, results.nextName))
		{
		    return results;
		}
		nextNameKey = BindingKey.getAllowLast(nextName);
		lock(nodeInfo, nextNameKey, false, "nextBoundName", name);
		results = new NextBoundNameResults(
		    nextName, oid,
		    getWaiting(nextNameKey) == GetWaitingResult.WRITERS);
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	TransactionAbortedException {@inheritDoc}
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
     * @throws	TransactionAbortedException {@inheritDoc}
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
		       long[] nameValues)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    CacheConsistencyException exception =
		new CacheConsistencyException(e.getMessage(), e);
	    logger.logThrow(WARNING, exception, "Cache consistency failure");
	    throw exception;
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
	    for (int i = 0; true; i++) {
		assert i < 1000 : "Too many retries";
		try {
		    commitInternal(
			nodeInfo, oids, oidValues, newOids, names, nameValues);
		    break;
		} catch (RuntimeException e) {
		    if (!isRetryableException(e)) {
			throw e;
		    }
		}
	    }
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /** Makes a single attempt to perform the commit. */
    private void commitInternal(NodeInfo nodeInfo,
				long[] oids,
				byte[][] oidValues,
				int newOids,
				String[] names,
				long[] nameValues)
	throws CacheConsistencyException
    {
	DbTransaction txn = env.beginTransaction(txnTimeout);
	boolean txnDone = false;
	try {
	    for (int i = 0; i < oids.length; i++) {
		long oid = oids[i];
		if (oid < 0) {
		    throw new IllegalArgumentException(
			"The object IDs must not be negative");
		}
		if (i < newOids) {
		    lock(nodeInfo, oid, true, "commit");
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
	    DbCursor cursor = namesDb.openCursor(txn);
	    try {
		for (int i = 0; i < names.length; i++) {
		    String name = names[i];
		    if (name == null) {
			throw new IllegalArgumentException(
			    "The names must not be null");
		    }
		    long value = nameValues[i];
		    if (value < -1) {
			throw new IllegalArgumentException(
			    "The name values must not be less than -1");
		    }
		    String nextName = cursor.findNext(encodeString(name))
			? decodeString(cursor.getKey()) : null;
		    BindingKey nextKey = BindingKey.getAllowLast(nextName);
		    if (value != -1) {
			/* Set -- next name must be write locked */
			checkLocked(nodeInfo, nextKey, true);
			if (!name.equals(nextName)) {
			    lock(nodeInfo, BindingKey.get(name), true,
				 "commit");
			}
			namesDb.put(
			    txn, encodeString(name), encodeLong(value));
		    } else if (!name.equals(nextName)) {
			/* Already removed -- next name must be read locked */
			checkLocked(nodeInfo, nextKey, false);
		    } else {
			/* Remove -- name and next name must be write locked */
			checkLocked(nodeInfo, nextKey, true);
			checkLocked(nodeInfo,
				    BindingKey.getAllowLast(
					cursor.findNext() ?
					decodeString(cursor.getKey()) : null),
				    true);
			namesDb.delete(txn, encodeString(name));
			releaseLock(nodeInfo, nextKey, "commit");
		    }
		}
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
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public void evictObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    CacheConsistencyException exception =
		new CacheConsistencyException(e.getMessage(), e);
	    logger.logThrow(WARNING, exception, "Cache consistency failure");
	    throw exception;
	}
	try {
	    checkOid(oid);
	    checkLocked(nodeInfo, oid, false);
	    releaseLock(nodeInfo, oid, "evict");
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public void downgradeObject(long nodeId, long oid)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    CacheConsistencyException exception =
		new CacheConsistencyException(e.getMessage(), e);
	    logger.logThrow(WARNING, exception, "Cache consistency failure");
	    throw exception;
	}
	try {
	    checkOid(oid);
	    checkLocked(nodeInfo, oid, true);
	    downgradeLock(nodeInfo, oid, "downgrade");
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public void evictBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    CacheConsistencyException exception =
		new CacheConsistencyException(e.getMessage(), e);
	    logger.logThrow(WARNING, exception, "Cache consistency failure");
	    throw exception;
	}
	try {
	    BindingKey nameKey = BindingKey.getAllowLast(name);
	    checkLocked(nodeInfo, nameKey, false);
	    releaseLock(nodeInfo, nameKey, "evict");
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /**
     * {@inheritDoc}
     *
     * @throws	CacheConsistencyException {@inheritDoc}
     * @throws	IllegalArgumentException {@inheritDoc}
     * @throws	TransactionAbortedException {@inheritDoc}
     */
    @Override
    public void downgradeBinding(long nodeId, String name)
	throws CacheConsistencyException
    {
	NodeInfo nodeInfo;
	try {
	    nodeInfo = nodeCallStarted(nodeId);
	} catch (IllegalStateException e) {
	    CacheConsistencyException exception =
		new CacheConsistencyException(e.getMessage(), e);
	    logger.logThrow(WARNING, exception, "Cache consistency failure");
	    throw exception;
	}
	try {
	    BindingKey nameKey = BindingKey.getAllowLast(name);
	    checkLocked(nodeInfo, nameKey, true);
	    downgradeLock(nodeInfo, nameKey, "downgrade");
	} finally {
	    nodeCallFinished(nodeInfo);
	}
    }

    /* -- Implement NodeListener -- */

    /** {@inheritDoc} */
    @Override
    public void nodeHealthUpdate(Node node) {
	/*
	 * TBD: Note that we may want to insure that the data store is
	 * shutdown for a particular node before marking the node as not alive.
	 * That would insure that operations for a failed node were not still
	 * underway even though the watchdog considers it to have failed.
	 * -tjb@sun.com (07/27/2009)
	 */
	if (!node.isAlive()) {
	    shutdownNode(node.getId());
	}
    }

    /* -- Implement FailureReporter -- */

    /** {@inheritDoc} */
    @Override
    public void reportFailure(Throwable exception) {
	logger.logThrow(
	    WARNING, exception, "CachingDataStoreServerImpl failed");
	synchronized (readySync) {
	    if (watchdogService == null) {
		if (failureBeforeWatchdog != null) {
		    failureBeforeWatchdog = exception;
		}
	    } else {
		Thread thread =
		    new Thread(CLASSNAME + ".reportFailure") {
			public void run() {
			    watchdogService.reportFailure(
				dataService.getLocalNodeId(),
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
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, "Releasing all locks for " + nodeInfo);
	    }
	    scheduleTask(new ReleaseNodeLocksTask(nodeInfo, debug));
	}
    }

    /** A {@link KernelRunnable} that releases the locks owned by a node. */
    private static class ReleaseNodeLocksTask extends AbstractKernelRunnable {
	private final NodeInfo nodeInfo;
	private final boolean debug;
	ReleaseNodeLocksTask(NodeInfo nodeInfo, boolean debug) {
	    super(null);
	    this.nodeInfo = nodeInfo;
	    this.debug = debug;
	}
	@Override
	public void run() {
	    nodeInfo.releaseAllLocks(debug);
	}
    }

    /* -- AbstractBasicService methods -- */

    /** {@inheritDoc} */
    protected void doReady() throws Exception {
	synchronized (readySync) {
	    if (failureBeforeWatchdog != null) {
		if (failureBeforeWatchdog instanceof Error) {
		    throw (Error) failureBeforeWatchdog;
		} else {
		    throw (Exception) failureBeforeWatchdog;
		}
	    }
	    dataService = txnProxy.getService(DataService.class);
	    watchdogService = txnProxy.getService(WatchdogService.class);
	}
	watchdogService.addNodeListener(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void doShutdown() {
	serverExporter.unexport();
	if (callbackExecutor != null) {
	    callbackExecutor.shutdownNow();
	}
	if (requestQueueListener != null) {
	    requestQueueListener.shutdown();
	}
	for (NodeInfo nodeInfo : nodeInfoMap.values()) {
	    nodeInfo.updateQueueServer.disconnect();
	}
	if (infoDb != null) {
	    infoDb.close();
	}
	if (classesDb != null) {
	    classesDb.close();
	}
	if (oidsDb != null) {
	    oidsDb.close();
	}
	if (namesDb != null) {
	    namesDb.close();
	}
	if (env != null) {
	    env.close();
	}
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
     * and lock tracking are performed correctly, and debugging output is
     * printed.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item to be locked
     * @param	forWrite whether the item should be locked for write
     * @param	operation a string representation of the operation that
     *		requested the lock, for debug output
     */
    private void lock(
	NodeInfo nodeInfo, Object key, boolean forWrite, String operation)
    {
	lock(nodeInfo, key, forWrite, operation, null);
    }

    /**
     * Obtains a lock on behalf of the node with the specified ID, providing
     * additional argument to identify the operation.  Use this method rather
     * than calling the lock manager to make sure that callbacks and lock
     * tracking are performed correctly, and debugging output is printed.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item to be locked
     * @param	forWrite whether the item should be locked for write
     * @param	operation a string representation of the operation that
     *		requested the lock, for debug output
     * @param	operationArg an expression representing the argument provided
     *		for the operation, or {@code null} if there is no argument.
     */
    private void lock(NodeInfo nodeInfo,
		      Object key,
		      boolean forWrite,
		      String operation,
		      Object operationArg)
    {
	assert key instanceof Long || key instanceof BindingKey;
	synchronized (nodeInfo) {
	    LockConflict<Object> conflict =
		lockManager.lockNoWait(nodeInfo, key, forWrite);
	    if (conflict == null) {
		if (debug) {
		    debugOutput(nodeInfo, forWrite ? "w " : "r ", key,
				operation, operationArg);
		}
	    } else {
		if (debug) {
		    debugOutput(nodeInfo, forWrite ? "wB" : "rB", key,
				operation, operationArg);
		}
		callbackRequests.add(
		    new CallbackRequest(nodeInfo, key, forWrite));
		conflict = lockManager.waitForLock(nodeInfo);
		if (conflict == null) {
		    if (debug) {
			debugOutput(nodeInfo, forWrite ? "wY" : "rY", key,
				    operation, operationArg);
		    }
		} else {
		    if (debug) {
			debugOutput(
			    nodeInfo, forWrite ? "wN" : "rN", key,
			    operation,
			    (operationArg == null
			     ? conflict : operationArg + " " + conflict));
		    }
		    String accessMsg = "Access nodeId:" + nodeInfo.nodeId +
		    ", key:" + key +
		    ", forWrite:" + forWrite +
		    " failed: ";
		    String conflictMsg = ", with conflicting node ID " +
			((NodeInfo) conflict.getConflictingLocker()).nodeId;
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
     * performed correctly, and debugging output is printed.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item whose lock should be released
     * @param	operation a string representation of the operation that
     *		requested the lock, for debug output
     */
    private void releaseLock(NodeInfo nodeInfo, Object key, String operation) {
	assert key instanceof Long || key instanceof BindingKey;
	if (debug) {
	    debugOutput(nodeInfo, "e ", key, operation, null);
	}
	lockManager.releaseLock(nodeInfo, key);
	nodeInfo.noteUnlocked(key);
    }

    /**
     * Downgrades a lock owned by a node.  Use this method rather than calling
     * the lock manager directly to make sure that debugging output is printed.
     *
     * @param	nodeInfo the information for the node
     * @param	key the key of the item whose lock should be downgraded
     * @param	operation a string representation of the operation that
     *		requested the lock, for debug output
     */
    private void downgradeLock(
	NodeInfo nodeInfo, Object key, String operation)
    {
	assert key instanceof Long || key instanceof BindingKey;
	if (debug) {
	    debugOutput(nodeInfo, "d ", key, operation, null);
	}
	lockManager.downgradeLock(nodeInfo, key);
    }

    /**
     * Prints debugging output for a locking operation.
     *
     * @param	nodeInfo the node info for the node performing the operation
     * @param	lockOp the locking operation
     * @param	key the name of the binding or the object ID
     * @param	operation a string representation of the operation
     * @param	operationArg an expression representing the argument provided
     *		for the operation, or {@code null} if there is no argument.
     */
    static void debugOutput(NodeInfo nodeInfo,
			    String lockOp,
			    Object key,
			    String operation,
			    Object operationArg)
    {
	StringBuilder sb = new StringBuilder();
	sb.append("nid:").append(nodeInfo.nodeId);
	sb.append(' ').append(lockOp);
	if (key instanceof BindingKey) {
	    sb.append(" name:");
	    if (key == BindingKey.LAST) {
		sb.append("LAST");
	    } else {
		sb.append('"').append(((BindingKey) key).getName());
		sb.append('"');
	    }
	} else {
	    sb.append(" oid:").append(key);
	}
	sb.append(' ').append(operation);
	if (operationArg != null) {
	    sb.append(' ').append(operationArg);
	}
	System.err.println(sb);	
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
	for (LockRequest<Object> lockRequest : lockManager.getOwners(key)) {
	    if (lockRequest.getLocker() == nodeInfo) {
		if (!forWrite || lockRequest.getForWrite()) {
		    return;
		}
		break;
	    }
	}
	CacheConsistencyException exception =
	    new CacheConsistencyException(
		"Node " + nodeInfo  + " does not own lock key:" + key +
		", forWrite:" + forWrite);
	logger.logThrow(WARNING, exception, "Cache consistency failure");
	throw exception;
    }

    /**
     * Checks whether there are requests waiting to access the specified key
     * for read or write.  Returns NO_WAITERS if there are no waiters, READERS
     * if there are only readers, and WRITERS if there are any writers.
     */
    private GetWaitingResult getWaiting(Object key) {
	List<LockRequest<Object>> waiters = lockManager.getWaiters(key);
	if (waiters.isEmpty()) {
	    return GetWaitingResult.NO_WAITERS;
	}
	for (LockRequest<Object> waiter : waiters) {
	    if (waiter.getForWrite()) {
		return GetWaitingResult.WRITERS;
	    }
	}
	return GetWaitingResult.READERS;
    }

    /** The return type of getWaiting. */
    private enum GetWaitingResult {

	/** No waiters. */
	NO_WAITERS,

	/** The waiters are all waiting for read. */
	READERS,

	/** There are waiters waiting for write. */
	WRITERS;
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
	    done = true;
	    return nodeInfo;
	} finally {
	    if (!done) {
		callFinished();
	    }
	}
    }

    /**
     * Decrements the call count and the count of calls active for the node.
     */
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
		DbTransaction txn = env.beginTransaction(txnTimeout);
		boolean txnDone = false;
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

    /** Checks two possibly null arguments for equality. */
    private static boolean safeEquals(Object x, Object y) {
	return x == y || (x != null && x.equals(y));
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
		    callbackRequests.take().callbackOwners();
		} catch (InterruptedException e) {
		    continue;
		}
	    }
	}
    }

    /**
     * Records information about a lock request that was blocked and may
     * require callbacks.
     */
    private class CallbackRequest {

	/** Information about the node making the request. */
	final NodeInfo nodeInfo;

	/** The key of the requested item. */
	final Object key;

	/** Whether the item was requested for write. */
	final boolean forWrite;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	nodeInfo information about the node making the request
	 *		request
	 * @param	key the key of the requested item
	 * @param	forWrite whether the item was requested for write
	 */
	CallbackRequest(NodeInfo nodeInfo, Object key, boolean forWrite) {
	    this.nodeInfo = nodeInfo;
	    this.key = key;
	    this.forWrite = forWrite;
	}

	/**
	 * Sends callback requests to all of the owners of the requested lock,
	 * unless the server is shutting down.  Abandons further callbacks if
	 * the requesting node is marked as failed.
	 */
	void callbackOwners() {
	    try {
		callStarted();
	    } catch (IllegalStateException e) {
		return;
	    }
	    try {
		nodeInfo.nodeCallStarted();
		try {
		    for (LockRequest<Object> owner
			     : lockManager.getOwners(key))
		    {
			if (nodeInfo != owner.getLocker()) {
			    callback((NodeRequest) owner);
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
	private void callback(NodeRequest owner) {
	    if (owner.noteCallback()) {
		boolean downgrade = owner.getForWrite() && !forWrite;
		NodeInfo ownerNodeInfo = owner.getNodeInfo();
		CallbackTask task = new CallbackTask(
		    ownerNodeInfo.callbackServer, key, downgrade,
		    nodeInfo.nodeId);
		runIoTask(task, ownerNodeInfo.nodeId);
		if (task.released) {
		    if (downgrade) {
			downgradeLock(ownerNodeInfo, key, "callback");
		    } else {
			releaseLock(ownerNodeInfo, key, "callback");
		    }
		} else if (debug) {
		    debugOutput(ownerNodeInfo, downgrade ? "dB" : "eB",
				key, "callback", null);
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
