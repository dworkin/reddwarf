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

import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.AbstractDataStore;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.cache.BasicCacheEntry.
    AwaitWritableResult;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    GetBindingForRemoveResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    GetBindingForUpdateResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    GetBindingResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    GetObjectForUpdateResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    GetObjectResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    NextBoundNameResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    NextObjectResults;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStoreServer.
    RegisterNodeResult;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import static com.sun.sgs.kernel.AccessReporter.AccessType.READ;
import static com.sun.sgs.kernel.AccessReporter.AccessType.WRITE;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/*
 * How will I make sure that the cache availability semaphore stays in sync
 * with the cache contents?  Forgetting to acquire or release would be bad!
 *
 * Make sure to set tick on entries when they are encached, not used, to insure
 * they don't get evicted immediately.
 */
/**
 * Provides an implementation of {@link DataStore} that caches data on the
 * local node and communicates with a {@link CachingDataStoreServer}. <p>
 *
 * The {@link #CachingDataStore constructor} supports the following
 * configuration properties: <p>
 */
public class CachingDataStore extends AbstractDataStore
    implements CallbackServer
{
    /** The property for specifying the server host. */
    public static final String SERVER_HOST_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.server.host";

    /** The property for specifying the server port. */
    public static final String SERVER_PORT_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.server.port";

    /** The default server port. */
    public static final int DEFAULT_SERVER_PORT = 44540;

    /** The property for specifying the callback port. */
    public static final String CALLBACK_PORT_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.callback.port";

    /** The default callback port. */
    public static final int DEFAULT_CALLBACK_PORT = 44541;
    
    /** The property for specifying the cache size. */
    public static final String CACHE_SIZE_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.size";

    /** The default cache size. */
    public static final int DEFAULT_CACHE_SIZE = 5000;

    /** The minimum cache size. */
    public static final int MIN_CACHE_SIZE = 1000;

    /** The property for specifying the number of cache locks. */
    public static final String NUM_LOCKS_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.num.locks";

    /** The default number of cache locks. */
    public static final int DEFAULT_NUM_LOCKS = 20;

    /** The property for specifying the eviction batch size. */
    public static final String EVICTION_BATCH_SIZE_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.eviction.batch.size";

    /** The default cache size. */
    public static final int DEFAULT_EVICTION_BATCH_SIZE = 100;

    /** The property for specifying the eviction reserve size. */
    public static final String EVICTION_RESERVE_SIZE_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.eviction.reserve.size";

    /** The default cache size. */
    public static final int DEFAULT_EVICTION_RESERVE_SIZE = 50;

    /** The property for specifying the update queue size. */
    public static final String UPDATE_QUEUE_SIZE_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.update.queue.size";

    /** The default update queue size. */
    public static final int DEFAULT_UPDATE_QUEUE_SIZE = 100;

    /** The property for specifying the new object ID allocation batch size. */
    public static final String OBJECT_ID_BATCH_SIZE_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.object.id.batch.size";

    /** The default new object ID allocation batch size. */
    public static final int DEFAULT_OBJECT_ID_BATCH_SIZE = 1000;

    /**
     * The property for specifying the number of milliseconds to continue
     * retrying I/O operations before determining that the failure is
     * permanent.
     */
    public static final String MAX_RETRY_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.max.retry";

    /** The default maximum retry, in milliseconds. */
    /* FIXME: Same as watchdog renew interval? */
    public static final long DEFAULT_MAX_RETRY = 1000;

    /**
     * The property for specifying the number of milliseconds to wait when
     * attempting to obtain a lock.
     */
    /* FIXME: Same as bdb and locking access coordinator? */
    public static final String LOCK_TIMEOUT_PROPERTY =
	"com.sun.sgs.impl.service.data.store.cache.lock.timeout";
    
    /** The default lock timeout, in milliseconds. */
    public static final long DEFAULT_LOCK_TIMEOUT = 10;

    /** The name of this class. */
    private static final String CLASSNAME =
	"com.sun.sgs.impl.service.data.store.cache.CachingDataStore";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The logger for transaction abort exceptions. */
    static final LoggerWrapper abortLogger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME + ".abort"));

    /** The remote data store server. */
    final CachingDataStoreServer server;

    /** The transaction proxy. */
    final TransactionProxy txnProxy;

    /** The local data store server, if started, else {@code null}. */
    private final CachingDataStoreServerImpl localServer;

    /** The exporter for the callback server. */
    private final Exporter<CallbackServer> callbackExporter =
	new Exporter<CallbackServer>(CallbackServer.class);

    /** The proxy for the callback server, to send to the data store server. */
    private final CallbackServer callbackProxy;

    /** The node ID for the local node. */
    private final long nodeId;

    /** The maximum retry for I/O operations. */
    private final long maxRetry;

    /** The lock timeout. */
    private final long lockTimeout;

    /** The transaction scheduler. */
    private final TransactionScheduler txnScheduler;

    /** The transaction context map for this class. */
    private final TxnContextMap contextMap;

    /** The cache of binding and object entries. */
    private final Cache cache;

    /** The cache of object IDs available for new objects. */
    private final NewObjectIdCache newObjectIdCache;

    /** The number of evictions that have been scheduled but not completed. */
    private final AtomicInteger pendingEvictions = new AtomicInteger();

    /**
     * The number of cache entries to hold in reserve for use while finding
     * entries to evict.
     */
    private final int evictionReserveSize;

    /**
     * The number of cache entries to consider when looking for a least
     * recently used entry to evict.
     */
    private final int evictionBatchSize;

    /**
     * The thread that evicts least recently used entries from the cache as
     * needed.
     */
    private final Thread evictionThread = new EvictionThread();

    /** Manages sending updates to the server. */
    final UpdateQueue updateQueue;

    /** The task owner. */
    final Identity taskOwner;

    /** The watchdog service. */
    private final WatchdogService watchdogService;

    /** A thread pool for fetching data from the server. */
    private ExecutorService fetchExecutor =
	Executors.newCachedThreadPool(
	    /* Use named daemon threads */
	    new ThreadFactory() {
		private final AtomicInteger nextId = new AtomicInteger(1);
		public Thread newThread(Runnable r) {
		    Thread t = new Thread(r);
		    t.setName(
			"CachingDataStore fetch-" + nextId.getAndIncrement());
		    t.setDaemon(true);
		    return t;
		}
	    });

    /** The possible shutdown states. */
    enum ShutdownState {

	/** Shutdown has not been requested. */
	NOT_REQUESTED,

        /** Shutdown has been requested. */
	REQUESTED,

	/** All active transactions have been completed. */
	TXNS_COMPLETED,

	/** Shutdown has been completed. */
	COMPLETED;
    }

    /**
     * The shutdown state.  Synchronize on {@code shutdownSync} when accessing
     * this field.
     */
    private ShutdownState shutdownState = ShutdownState.NOT_REQUESTED;

    /** Synchronizer for {@code shutdownState}. */
    private final Object shutdownSync = new Object();

    /**
     * Creates an instance of this class.
     */
    public CachingDataStore(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy)
	throws Exception
    {
	this(properties, systemRegistry, txnProxy, null);
    }

    /**
     * Creates an instance of this class.
     */
    public CachingDataStore(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy,
			    Scheduler schedulerIgnored)
	throws Exception
    {
	super(/* FIXME: systemRegistry */ null, logger, abortLogger);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	boolean startServer = wrappedProps.getEnumProperty(
	    StandardProperties.NODE_TYPE, NodeType.class, NodeType.singleNode)
	    != NodeType.appNode;
	String serverHost = wrappedProps.getProperty(
	    SERVER_HOST_PROPERTY,
	    wrappedProps.getProperty(StandardProperties.SERVER_HOST));
	if (serverHost == null && !startServer) {
	    throw new IllegalArgumentException(
		"A server host must be specified");
	}
	int serverPort = wrappedProps.getIntProperty(
	    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, startServer ? 0 : 1,
	    65535);
	int callbackPort = wrappedProps.getIntProperty(
	    CALLBACK_PORT_PROPERTY, DEFAULT_CALLBACK_PORT, 0, 65535);
	int cacheSize = wrappedProps.getIntProperty(
	    CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE, MIN_CACHE_SIZE,
	    Integer.MAX_VALUE);
	int numLocks = wrappedProps.getIntProperty(
	    NUM_LOCKS_PROPERTY, DEFAULT_NUM_LOCKS, 1, Integer.MAX_VALUE);
	evictionBatchSize = wrappedProps.getIntProperty(
	    EVICTION_BATCH_SIZE_PROPERTY, DEFAULT_EVICTION_BATCH_SIZE,
	    1, cacheSize);
	evictionReserveSize = wrappedProps.getIntProperty(
	    EVICTION_RESERVE_SIZE_PROPERTY, DEFAULT_EVICTION_RESERVE_SIZE,
	    0, cacheSize);
	int updateQueueSize = wrappedProps.getIntProperty(
	    UPDATE_QUEUE_SIZE_PROPERTY, DEFAULT_UPDATE_QUEUE_SIZE,
	    1, Integer.MAX_VALUE);
	int objectIdBatchSize = wrappedProps.getIntProperty(
	    OBJECT_ID_BATCH_SIZE_PROPERTY, DEFAULT_OBJECT_ID_BATCH_SIZE,
	    1, Integer.MAX_VALUE);
	maxRetry = wrappedProps.getLongProperty(
	    MAX_RETRY_PROPERTY, DEFAULT_MAX_RETRY, 0, Long.MAX_VALUE);
	lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, DEFAULT_LOCK_TIMEOUT, 0, Long.MAX_VALUE);
	if (logger.isLoggable(CONFIG)) {
	    logger.log(CONFIG,
		       "Creating CachingDataStore with properties:" +
		       "\n  startServer: " + startServer +
		       "\n  serverHost: " + serverHost +
		       "\n  serverPort: " + serverPort +
		       "\n  callbackPort: " + callbackPort +
		       "\n  cacheSize: " + cacheSize +
		       "\n  evictionBatchSize: " + evictionBatchSize +
		       "\n  evictionReserveSize: " + evictionReserveSize +
		       "\n  updateQueueSize: " + updateQueueSize +
		       "\n  objectIdBatchSize: " + objectIdBatchSize +
		       "\n  maxRetry: " + maxRetry +
		       "\n  lockTimeout: " + lockTimeout);
	}
	try {
	    if (serverHost == null && startServer) {
		serverHost = InetAddress.getLocalHost().getHostName();
	    }
	    this.txnProxy = txnProxy;
	    taskOwner = txnProxy.getCurrentOwner();
	    watchdogService =
		systemRegistry.getComponent(WatchdogService.class);
	    if (startServer) {
		try {
		    localServer = new CachingDataStoreServerImpl(
			properties, systemRegistry, txnProxy);
		    serverPort = /* FIXME: localServer.getPort() */ 0;
		    logger.log(INFO, "Started server: {0}", localServer);
// FIXME: Server needs to export and throw IOException
// 		} catch (IOException t) {
// 		    logger.logThrow(SEVERE, t, "Problem starting server");
// 		    throw t;
		} catch (RuntimeException t) {
		    logger.logThrow(SEVERE, t, "Problem starting server");
		    throw t;
		}
	    } else {
		localServer = null;
	    }
	    server = new GetServerCallable(serverHost, serverPort).call();
	    RegisterNodeResult registerNodeResult =
		new RegisterNodeCallable().call();
	    nodeId = registerNodeResult.nodeId;
	    updateQueue = new UpdateQueue(
		this, serverHost, registerNodeResult.socketPort,
		updateQueueSize);
	    txnScheduler =
		systemRegistry.getComponent(TransactionScheduler.class);
	    cache = new Cache(this, cacheSize, numLocks, evictionThread);
	    newObjectIdCache = new NewObjectIdCache(this, objectIdBatchSize);
	    contextMap = new TxnContextMap(this);
	    evictionThread.start();
	    callbackExporter.export(this, callbackPort);
	    callbackProxy = callbackExporter.getProxy();
	} catch (Exception e) {
	    shutdownInternal();
	    throw e;
	}
    }

    /* -- Implement AbstractDataStore's DataStore methods -- */

    /* DataStore.createObject */

    /** {@inheritDoc} */
    protected long createObjectInternal(Transaction txn) {
	TxnContext context = contextMap.join(txn);
	long oid = newObjectIdCache.getNewObjectId();
	synchronized (cache.getObjectLock(oid)) {
	    context.noteNewObject(oid);
	}
	return oid;
    }

    /* DataStore.markForUpdate */

    /** {@inheritDoc} */
    protected void markForUpdateInternal(Transaction txn, long oid) {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	Object lock = cache.getObjectLock(oid);
	synchronized (lock) {
	    while (true) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		assert entry != null :
		    "markForUpdate called for object not in cache";
		switch (entry.awaitWritable(lock, stop)) {
		case DECACHED:
		    continue;
		case READABLE:
		    /* Upgrade */
		    entry.setFetchingUpgrade();
		    scheduleFetch(new UpgradeObjectRunnable(context, oid));
		    AwaitWritableResult result =
			entry.awaitWritable(lock, stop);
		    assert result == AwaitWritableResult.WRITABLE;
		    break;
		case WRITABLE:
		    /* Already cached for write */
		    break;
		}
		context.noteModifiedObject(entry);
		return;
	    }
	}
    }

    /** Upgrade an existing object. */
    private class UpgradeObjectRunnable extends RetryIoRunnable<Boolean> {
	private final TxnContext context;
	private final long oid;
	UpgradeObjectRunnable(TxnContext context, long oid) {
	    super(CachingDataStore.this);
	    this.context = context;
	    this.oid = oid;
	}
	Boolean callOnce() throws IOException {
	    try {
		return server.upgradeObject(nodeId, oid);
	    } catch (CacheConsistencyException e) {
		reportFailure();
		return false;
	    }
	}
	void callWithResult(Boolean callbackEvict) {
	    Object lock = cache.getObjectLock(oid);
	    synchronized (lock) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		context.noteAccess(entry);
		entry.setUpgraded(lock);
	    }
	    if (callbackEvict) {
		scheduleTask(new DowngradeObjectTask(oid));
	    }
	}
    }

    /* DataStore.getObject */

    /** {@inheritDoc} */
    protected byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate)
    {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	Object lock = cache.getObjectLock(oid);
	byte[] value;
	synchronized (lock) {
	    while (true) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		if (entry == null) {
		    entry = context.noteFetchingObject(oid, forUpdate);
		    scheduleFetch(
			forUpdate ? new GetObjectRunnable(context, oid)
			: new GetObjectForUpdateRunnable(context, oid));
		}
		if (!forUpdate) {
		    if (!entry.awaitReadable(lock, stop)) {
			continue;
		    }
		} else {
		    switch (entry.awaitWritable(lock, stop)) {
		    case DECACHED:
			continue;
		    case READABLE:
			/* Upgrade */
			entry.setFetchingUpgrade();
			scheduleFetch(
			    new UpgradeObjectRunnable(context, oid));
			AwaitWritableResult result =
			    entry.awaitWritable(lock, stop);
			assert result == AwaitWritableResult.WRITABLE;
			break;
		    case WRITABLE:
			/* Already cached for write */
			break;
		    }
		}
		context.noteModifiedObject(entry);
		value = entry.getValue();
		break;
	    }
	}
	if (value == null) {
	    throw new ObjectNotFoundException("Object not found: " + oid);
	}
	return value;
    }

    /** Gets an object for read. */
    private class GetObjectRunnable extends RetryIoRunnable<GetObjectResults> {
	private final TxnContext context;
	private final long oid;
	GetObjectRunnable(TxnContext context, long oid) {
	    super(CachingDataStore.this);
	    this.context = context;
	    this.oid = oid;
	}
	GetObjectResults callOnce() throws IOException {
	    return server.getObject(nodeId, oid);
	}
	void callWithResult(GetObjectResults results) {
	    synchronized (cache.getObjectLock(oid)) {
		context.noteCachedObject(
		    cache.getObjectEntry(oid), results.data, false);
	    }
	    if (results.callbackEvict) {
		scheduleTask(new EvictObjectTask(oid));
	    }
	}
    }

    /** Gets an object for write. */
    private class GetObjectForUpdateRunnable
	extends RetryIoRunnable< GetObjectForUpdateResults>
    {
	private final TxnContext context;
	private final long oid;
	GetObjectForUpdateRunnable(TxnContext context, long oid) {
	    super(CachingDataStore.this);
	    this.context = context;
	    this.oid = oid;
	}
	GetObjectForUpdateResults callOnce() throws IOException {
	    return server.getObjectForUpdate(nodeId, oid);
	}
	void callWithResult(GetObjectForUpdateResults results) {
	    synchronized (cache.getObjectLock(oid)) {
		context.noteCachedObject(
		    cache.getObjectEntry(oid), results.data, true);
	    }
	    if (results.callbackEvict) {
		scheduleTask(new EvictObjectTask(oid));
	    }
	    if (results.callbackDowngrade) {
		scheduleTask(new DowngradeObjectTask(oid));
	    }
	}
    }

    /* DataStore.setObject */

    /** {@inheritDoc} */
    protected void setObjectInternal(Transaction txn, long oid, byte[] data) {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	Object lock = cache.getObjectLock(oid);
	synchronized (lock) {
	    while (true) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		if (entry == null) {
		    /* Fetch for write */
		    entry = context.noteFetchingObject(oid, true);
		    scheduleFetch(
			new GetObjectForUpdateRunnable(context, oid));
		}
		switch (entry.awaitWritable(lock, stop)) {
		case DECACHED:
		    /* Not in cache -- try again */
		    continue;
		case READABLE:
		    /* Upgrade */
		    entry.setFetchingUpgrade();
		    scheduleFetch(new UpgradeObjectRunnable(context, oid));
		    AwaitWritableResult result =
			entry.awaitWritable(lock, stop);
		    assert result == AwaitWritableResult.WRITABLE;
		    break;
		case WRITABLE:
		    /* Already cached for write */
		    break;
		}
		context.noteModifiedObject(entry, data);
		break;
	    }
	}
    }

    /* DataStore.setObjects */

    /** {@inheritDoc} */
    protected void setObjectsInternal(
	Transaction txn, long[] oids, byte[][] dataArray)
    {
	for (int i = 0; i < oids.length; i++) {
	    setObjectInternal(txn, oids[i], dataArray[i]);
	}
    }

    /* DataStore.removeObject */

    /** {@inheritDoc} */
    protected void removeObjectInternal(Transaction txn, long oid) {
	setObjectInternal(txn, oid, null);
    }

    /* DataStore.getBinding */

    /** {@inheritDoc} */
    protected BindingValue getBindingInternal(Transaction txn, String name) {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	BindingKey nameKey = BindingKey.get(name);
	while (true) {
	    /* Find cache entry for name or next higher name */
	    BindingCacheEntry entry = cache.getCeilingBindingEntry(nameKey);
	    Object lock = cache.getBindingLock(
		(entry != null) ? entry.key : BindingKey.LAST);
	    synchronized (lock) {
		if (entry == null) {
		    /* No next entry -- create it */
		    entry = context.noteLastBinding();
		} else if (!entry.awaitReadable(lock, stop)) {
		    /* The entry is not in the cache -- try again */
		    continue;
		} else if (nameKey.equals(entry.key)) {
		    /* Name is bound */
		    context.noteAccess(entry);
		    return new BindingValue(entry.getValue(), null);
		} else if (entry.getKnownUnbound(nameKey)) {
		    /* Name is unbound */
		    context.noteAccess(entry);
		    return new BindingValue(-1, entry.key.getName());
		} else if (!assureNextEntry(entry, nameKey, lock, stop)) {
		    /* Entry is no longer for next name -- try again */
		    continue;
		}
		/* Get information from server about this name */
		scheduleFetch(
		    new GetBindingRunnable(context, nameKey, entry.key));
		entry.awaitNotPendingPrevious(lock, stop);
		if (entry.getReadable() && entry.getKnownUnbound(nameKey)) {
		    /* Name is not bound */
		    context.noteAccess(entry);
		    return new BindingValue(-1, entry.key.getName());
		} else {
		    /*
		     * Either a new entry was created for the name or else the
		     * real next entry was earlier than the one previously in
		     * the cache -- try again
		     */
		    continue;
		}
	    }
	}
    }

    /**
     * Make sure that the entry is not being used to check an earlier binding
     * and that it is indeed the next entry in the cache after the specified
     * key.
     *
     * @param	entry the entry
     * @param	previousKey the key for which {@code entry} should be the next
     *		entry in the cache
     * @param	lock the object to use when waiting for notifications
     * @param	stop the time in milliseconds when waiting should fail
     * @return	{@code true} if {@code entry} is the next entry present in the
     *		cache after {@code previousKey}, else {@code false}
     * @throws	TransactionTimeoutException if the operation does not succeed
     *		before the specified stop time
     */
    private boolean assureNextEntry(BindingCacheEntry entry,
				    BindingKey previousKey,
				    Object lock,
				    long stop)
    {
	assert Thread.holdsLock(lock);
	entry.awaitNotPendingPrevious(lock, stop);
	BindingCacheEntry checkEntry =
	    cache.getCeilingBindingEntry(previousKey);
	if (checkEntry != entry) {
	    /*
	     * Another entry was inserted in the time between when we got this
	     * entry and when we locked it -- try again
	     */
	    return false;
	} else if (!checkEntry.getReadable()) {
	    /* Entry got decached -- try again */
	    return false;
	} else {
	    return true;
	}
    }

    /** Gets a binding for read. */
    private class GetBindingRunnable
	extends ReserveCacheRetryIoRunnable<GetBindingResults>
    {
	private final TxnContext context;
	private final BindingKey nameKey;
	private final BindingKey nextNameKey;
	GetBindingRunnable(
	    TxnContext context, BindingKey nameKey, BindingKey nextNameKey)
	{
	    this.context = context;
	    this.nameKey = nameKey;
	    this.nextNameKey = nextNameKey;
	}
	GetBindingResults callOnce() throws IOException {
	    return server.getBinding(nodeId, nameKey.getName());
	}
	void callWithResult(GetBindingResults results) {
	    if (results.found) {
		/* Add new entry for name */
		Object lock = cache.getBindingLock(nameKey);
		synchronized (lock) {
		    context.noteCachedReservedBinding(
			nameKey, results.oid, false);
		    usedCacheEntry();
		}
	    }
	    BindingKey realNextNameKey = BindingKey.get(results.nextName);
	    if (!nextNameKey.equals(realNextNameKey)) {
		Object lock = cache.getBindingLock(realNextNameKey);
		synchronized (lock) {
		    /* Add new entry for real next name */
		    BindingCacheEntry entry =
			context.noteCachedReservedBinding(
			    realNextNameKey, results.oid, false);
		    entry.updatePreviousKey(nameKey, !results.found);
		    usedCacheEntry();
		}
	    }
	    Object lock = cache.getBindingLock(nextNameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nextNameKey);
		if (nextNameKey.equals(realNextNameKey)) {
		    /* Update existing next entry */
		    boolean updated =
			entry.updatePreviousKey(nameKey, !results.found);
		    assert updated;
		} else {
		    cache.removeBindingEntry(nextNameKey);
		}
		entry.setNotPendingPrevious(lock);
	    }
	    if (results.callbackEvict) {
		scheduleTask(
		    new EvictBindingTask(
			results.found ? nameKey.getName() : results.nextName));

	    }
	}
    }

    /**
     * A {@code RetryIoRunnable} that preallocates space in the cache and
     * releases any space that it does not use.
     */
    private abstract class ReserveCacheRetryIoRunnable<V>
	extends RetryIoRunnable<V>
    {
	/** The number of preallocated entries that have not been used. */
	private int unusedCacheEntries;

	/** Creates an instance with one preallocated entry. */
	ReserveCacheRetryIoRunnable() {
	    this(1);
	}

	/**
	 * Creates an instance with the specified number of preallocated
	 * entries.
	 *
	 * @param	numCacheEntries the number of entries to preallocate in
	 *		the cache
	 * @throws	IllegalArgumentException if {@code numCacheEntries} is
	 *		less than {@code 1}
	 */
	ReserveCacheRetryIoRunnable(int numCacheEntries) {
	    super(CachingDataStore.this);
	    if (numCacheEntries < 1) {
		throw new IllegalArgumentException(
		    "The numCacheEntries must not be less than 1");
	    }
	    cache.reserve(numCacheEntries);
	    unusedCacheEntries = numCacheEntries;
	}

	/**
	 * Notes that a preallocated cache entry has been used.
	 *
	 * @throws	IllegalStateException if there are no unused
	 *		preallocated entries
	 */
	void usedCacheEntry() {
	    if (unusedCacheEntries <= 0) {
		throw new IllegalStateException("No more unused entries");
	    }
	    unusedCacheEntries--;
	}

	/**
	 * Calls the superclass's {@code run} method, but arranging to release
	 * any unused cache entries when returning.
	 */
	public void run() {
	    try {
		super.run();
	    } finally {
		if (unusedCacheEntries != 0) {
		    cache.release(unusedCacheEntries);
		}
	    }
	}
    }

    /* DataStore.setBinding */

    /** {@inheritDoc} */
    protected BindingValue setBindingInternal(
	Transaction txn, String name, long oid)
    {
	TxnContext context = contextMap.join(txn);
	BindingKey nameKey = BindingKey.get(name);
	while (true) {
	    /* Find cache entry for name or next higher name */
	    BindingCacheEntry entry = cache.getCeilingBindingEntry(nameKey);
	    Object lock = cache.getBindingLock(
		(entry != null) ? entry.key : BindingKey.LAST);
	    synchronized (lock) {
		if (entry == null) {
		    /* No next entry -- create it */
		    entry = context.noteLastBinding();
		    setBindingInternalUnknown(context, lock, entry, nameKey);
		    continue;
		} else if (nameKey.equals(entry.key)) {
		    /* Found entry for name */
		    boolean result =
			setBindingInternalFound(context, lock, entry);
		    if (!result) {
			/* Entry is not in cache -- try again */
			continue;
		    } else {
			/* Name was already bound */
			context.noteModifiedBinding(entry, oid);
			return new BindingValue(1, null);
		    }
		} else if (entry.getKnownUnbound(nameKey)) {
		    /* Found next entry and know name is unbound */
		    boolean nextWritable = setBindingInternalUnbound(
			context, lock, entry, nameKey);
		    if (nextWritable) {
			/*
			 * Requested name is still unbound, next name was
			 * writable, and have marked the next entry pending --
			 * fall through to create name entry
			 */
		    } else {
			/* Try again */
			continue;
		    }
		} else {
		    /* Need to get information about name and try again */
		    setBindingInternalUnknown(context, lock, entry, nameKey);
		    continue;
		}
	    }
	    /* Create a new entry for the requested name */
	    synchronized (cache.getBindingLock(nameKey)) {
		context.noteCachedBinding(nameKey, oid, true);
	    }
	    /* Mark the next entry as not pending */
	    synchronized (lock) {
		cache.getBindingEntry(entry.key).setNotPendingPrevious(lock);
	    }
	    /* Name was unbound */
	    return new BindingValue(-1, entry.key.getName());
	}
    }

    /**
     * Implement {@code setBinding} for when an entry for the binding was found
     * in the cache.
     *
     * @param	context the transaction info
     * @param	lock the lock for the name entry
     * @param	entry the name entry
     * @return	{@code true} if the entry was found, {@code false} if it was
     *		no longer in the cache
     */
    private boolean setBindingInternalFound(
	TxnContext context, Object lock, BindingCacheEntry entry)
    {
	long stop = context.getStopTime();
	switch (entry.awaitWritable(lock, stop)) {
	case DECACHED:
	    /* Entry not in cache -- try again */
	    return false;
	case READABLE:
	    /*
	     * We've obtained a write lock from the access coordinator, so
	     * there can't be any evictions initiated.  For that reason, there
	     * is no need to retry once we confirm that the entry is cached.
	     */
	    boolean writable = false;
	    if (entry.getPendingPrevious()) {
		/* Wait for previous name operation to end, then retry */
		entry.awaitNotPendingPrevious(lock, stop);
		if (entry.getWritable()) {
		    /* Previous name operation did the upgrade */
		    writable = true;
		}
	    }
	    if (!writable) {
		/* Upgrade */
		entry.setFetchingUpgrade();
		scheduleFetch(
		    new GetBindingForUpdateUpgradeRunnable(
			context, entry.key));
		entry.awaitNotUpgrading(lock, stop);
	    }
	    break;
	case WRITABLE:
	    /* Already writable */
	    break;
	}
	return true;
    }

    /**
     * A {@code Runnable} that calls {@code getBindingForUpgrade} on the server
     * to upgrade the read-only cache entry already present for the requested
     * name.
     */
    private class GetBindingForUpdateUpgradeRunnable
	extends RetryIoRunnable<GetBindingForUpdateResults>
    {
	private final TxnContext context;
	private final BindingKey nameKey;
	GetBindingForUpdateUpgradeRunnable(TxnContext context,
					   BindingKey nameKey)
	{
	    super(CachingDataStore.this);
	    this.context = context;
	    this.nameKey = nameKey;
	}
	GetBindingForUpdateResults callOnce() throws IOException {
	    return server.getBindingForUpdate(nodeId, nameKey.getName());
	}
	void callWithResult(GetBindingForUpdateResults results) {
	    assert results.found;
	    Object lock = cache.getBindingLock(nameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nameKey);
		context.noteAccess(entry);
		entry.setUpgraded(lock);
	    }
	    if (results.callbackEvict) {
		scheduleTask(new EvictBindingTask(nameKey.getName()));

	    }
	    if (results.callbackDowngrade) {
		scheduleTask(new DowngradeBindingTask(nameKey.getName()));
	    }
	}
    }

    /**
     * Implement {@code setBinding} for when an entry for next binding was
     * found and has cached that the requested name is unbound.
     *
     * @param	context the transaction info
     * @param	lock the lock for the next entry
     * @param	entry the next entry
     * @param	nameKey the key for the requested name
     * @return	{@code true} if the next entry still caches that the requested
     *		name is unbound, or {@code false} if the information is no
     *		longer cached
     */
    private boolean setBindingInternalUnbound(TxnContext context,
					      Object lock,
					      BindingCacheEntry entry,
					      BindingKey nameKey)
    {
	long stop = context.getStopTime();	
	AwaitWritableResult result = entry.awaitWritable(lock, stop);
	if (result == AwaitWritableResult.DECACHED) {
	    /* Entry not in cache -- try again */
	    return false;
	} else if (!entry.getKnownUnbound(nameKey)) {
	    /* Entry was altered while waiting -- try again */
	    return false;
	} else if (result == AwaitWritableResult.WRITABLE) {
	    /* Entry is writable and name is unbound -- mark pending */
	    entry.setPendingPrevious();
	    return true;
	} else /* result == AwaitWritableResult.READABLE */ {
	    if (entry.getPendingPrevious()) {
		/* Wait for previous name operation to end, then retry */
		entry.awaitNotPendingPrevious(lock, stop);
		return false;
	    }
	    /* Upgrade */
	    entry.setFetchingUpgrade();
	    entry.setPendingPrevious();
	    scheduleFetch(
		new GetBindingForUpdateUpgradeNextRunnable(
		    context, nameKey.getName(), entry.key));
	    entry.awaitNotPendingPrevious(lock, stop);
	    if (entry.getKnownUnbound(nameKey) && entry.getWritable()) {
		/* Entry is writable and name is unbound -- mark pending */
		entry.setPendingPrevious();
		return true;
	    } else {
		/* Entry was altered while waiting -- try again */
		return false;
	    }
	}
    }

    /**
     * A {@code Runnable} that calls {@code getBindingForUpdate} on the server
     * to upgrade the read-only cache entry already present for the next name
     * after the requested name, and that caches that the requested name is
     * unbound.
     */
    private class GetBindingForUpdateUpgradeNextRunnable
	extends RetryIoRunnable<GetBindingForUpdateResults>
    {
	private final TxnContext context;
	private final String name;
	private final BindingKey nextNameKey;
	GetBindingForUpdateUpgradeNextRunnable(
	    TxnContext context, String name, BindingKey nextNameKey)
	{
	    super(CachingDataStore.this);
	    this.context = context;
	    this.name = name;
	    this.nextNameKey = nextNameKey;
	}
	GetBindingForUpdateResults callOnce() throws IOException {
	    return server.getBindingForUpdate(nodeId, name);
	}
	void callWithResult(GetBindingForUpdateResults results) {
	    assert !results.found;
	    Object lock = cache.getBindingLock(nextNameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nextNameKey);
		assert nextNameKey.equals(BindingKey.get(results.nextName));
		entry.setUpgraded(lock);
		entry.setNotPendingPrevious(lock);
	    }
	    if (results.callbackEvict) {
		scheduleTask(new EvictBindingTask(nextNameKey.getName()));
	    }
	    if (results.callbackDowngrade) {
		scheduleTask(new DowngradeBindingTask(nextNameKey.getName()));
	    }
	}
    }

    /**
     * Implement {@code setBinding} for when no cache entries were found for
     * the requested name.
     *
     * @param	context the transaction info
     * @param	lock the lock for the next entry
     * @param	entry the next entry found in the cache
     * @param	nameKey the key for the requested name
     */
    private void setBindingInternalUnknown(TxnContext context,
					   Object lock,
					   BindingCacheEntry entry,
					   BindingKey nameKey)
    {
	long stop = context.getStopTime();	
	if (assureNextEntry(entry, nameKey, lock, stop)) {
	    /* This is the next entry -- get information about the name */
	    entry.setPendingPrevious();
	    scheduleFetch(
		new GetBindingForUpdateRunnable(context, nameKey, entry.key));
	    entry.awaitNotPendingPrevious(lock, stop);
	}
    }

    /**
     * A {@code Runnable} that calls {@code getBindingForUpdate} on the server
     * to get information about a requested name for which there were no
     * entries cached.
     */
    private class GetBindingForUpdateRunnable
	extends ReserveCacheRetryIoRunnable<GetBindingForUpdateResults>
    {
	private final TxnContext context;
	private final BindingKey nameKey;
	private final BindingKey nextNameKey;
	GetBindingForUpdateRunnable(
	    TxnContext context, BindingKey nameKey, BindingKey nextNameKey)
	{
	    this.context = context;
	    this.nameKey = nameKey;
	    this.nextNameKey = nextNameKey;
	}
	GetBindingForUpdateResults callOnce() throws IOException {
	    return server.getBindingForUpdate(nodeId, nameKey.getName());
	}
	void callWithResult(GetBindingForUpdateResults results) {
	    if (results.found) {
		/* Add new entry for name */
		Object lock = cache.getBindingLock(nameKey);
		synchronized (lock) {
		    context.noteCachedReservedBinding(
			nameKey, results.oid, true);
		    usedCacheEntry();
		}
	    }
	    BindingKey realNextNameKey = BindingKey.get(results.nextName);
	    if (!nextNameKey.equals(realNextNameKey)) {
		Object lock = cache.getBindingLock(realNextNameKey);
		synchronized (lock) {
		    /* Add new entry for real next name */
		    BindingCacheEntry entry =
			context.noteCachedReservedBinding(
			    nameKey, results.oid, true);
		    entry.updatePreviousKey(nameKey, !results.found);
		    usedCacheEntry();
		}
	    }
	    Object lock = cache.getBindingLock(nextNameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nextNameKey);
		if (nextNameKey.equals(realNextNameKey)) {
		    /* Update existing next entry */
		    boolean updated = entry.updatePreviousKey(
			nameKey, !results.found);
		    assert updated;
		} else {
		    cache.removeBindingEntry(nextNameKey);
		}
		entry.setNotPendingPrevious(lock);
	    }
	    String evictName =
		results.found ? nameKey.getName() : results.nextName;
	    if (results.callbackEvict) {
		scheduleTask(new EvictBindingTask(evictName));
	    }
	    if (results.callbackDowngrade) {
		scheduleTask(new DowngradeBindingTask(evictName));
	    }
	}
    }

    /* DataStore.removeBinding */

    /** {@inheritDoc} */
    protected BindingValue removeBindingInternal(
	Transaction txn, String name)
    {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	BindingKey nameKey = BindingKey.get(name);
	while (true) {
	    /* Find cache entry for name or next higher name */
	    BindingCacheEntry entry = cache.getCeilingBindingEntry(nameKey);
	    Object lock = cache.getBindingLock(
		entry != null ? entry.key : BindingKey.LAST);
	    long nameValue;
	    boolean nameWritable;
	    synchronized (lock) {
		if (entry == null) {
		    /* No next entry -- create it */
		    entry = context.noteLastBinding();
		    removeBindingInternalUnknown(
			context, lock, entry, nameKey);
		    continue;
		} else if (nameKey.equals(entry.key)) {
		    /* Found entry for name */
		    if (!removeBindingInternalFound(entry, lock, stop)) {
			/* Not in cache -- try again */
			continue;
		    }
		    /* Found name entry -- fall through for next name */
		    nameValue = entry.getValue();
		    nameWritable = entry.getWritable();
		    context.noteModifiedBinding(entry, -1);
		} else if (entry.getKnownUnbound(nameKey)) {
		    if (entry.awaitReadable(lock, stop)) {
			/*
			 * Found the next entry, and it has cached that the
			 * requested name is already unbound
			 */
			context.noteAccess(entry);
			return new BindingValue(-1, entry.key.getName());
		    } else {
			/* Entry is not in the cache -- try again */
			continue;
		    }
		} else {
		    /* Get information about name and try again */
		    removeBindingInternalUnknown(
			context, lock, entry, nameKey);
		    continue;
		}
	    }
	    /* Check next name */
	    BindingValue result = removeBindingInternalCheckNext(
		context, nameKey, nameValue, nameWritable);
	    if (result != null) {
		return result;
	    } else {
		continue;
	    }
	}
    }

    /**
     * Implement {@code removeBinding} for when an entry for the binding was
     * found in the cache.  If the entry is cached for read, it will be set to
     * upgrading.
     *
     * @param	entry the entry for the requested name
     * @param	lock the associated lock
     * @param	stop the time in milliseconds when waiting should fail
     * @return	{@code true} if the entry was found encached and not pending
     *		for a operation on the previous name
     */
    private boolean removeBindingInternalFound(
	BindingCacheEntry entry, Object lock, long stop)
    {
	assert Thread.holdsLock(lock);
	switch (entry.awaitWritable(lock, stop)) {
	case DECACHED:
	    /* Not in cache -- try again */
	    return false;
	case READABLE:
	    if (entry.getPendingPrevious()) {
		/* Wait to complete fetching previous name, then try again */
		entry.awaitNotPendingPrevious(lock, stop);
		return false;
	    }
	    /* Upgrade */
	    entry.setFetchingUpgrade();
	    return true;
	case WRITABLE:
	    /* Already writable */
	    return true;
	default:
	    throw new AssertionError();
	}
    }

    /**
     * Implement {@code removeBinding} for when no cache entries were found for
     * the requested name.
     *     
     * @param	context the transaction info
     * @param	lock the lock for the next entry
     * @param	entry the next entry found in the cache
     * @param	nameKey the key for the requested name
     */
    private void removeBindingInternalUnknown(TxnContext context,
					      Object lock,
					      BindingCacheEntry nextEntry,
					      BindingKey nameKey)
    {
	long stop = context.getStopTime();
	if (assureNextEntry(nextEntry, nameKey, lock, stop)) {
	    /* This is the next entry -- get information about the name */
	    nextEntry.setPendingPrevious();
	    scheduleFetch(
		new GetBindingForRemoveRunnable(
		    context, nameKey, nextEntry.key, true, false));
	    nextEntry.awaitNotPendingPrevious(lock, stop);
	}
    }

    /**
     * A {@link Runnable} that calls {@code getBindingForRemove} on the server
     * to get information about the request name for which there were no
     * entries cached.
     */
    private class GetBindingForRemoveRunnable
	extends ReserveCacheRetryIoRunnable<GetBindingForRemoveResults>
    {
	private final TxnContext context;
	private final BindingKey nameKey;
	private final BindingKey nextNameKey;
	private final boolean createName;
	private final boolean upgradeName;
	GetBindingForRemoveRunnable(TxnContext context,
				    BindingKey nameKey,
				    BindingKey nextNameKey,
				    boolean createName,
				    boolean upgradeName)
	{
	    super(createName ? 2 : 1);
	    this.context = context;
	    this.nameKey = nameKey;
	    this.nextNameKey = nextNameKey;
	    this.createName = createName;
	    this.upgradeName = upgradeName;
	}
	GetBindingForRemoveResults callOnce() throws IOException {
	    return server.getBindingForRemove(nodeId, nameKey.getName());
	}
	void callWithResult(GetBindingForRemoveResults results) {
	    if (createName) {
		synchronized (cache.getBindingLock(nameKey)) {
		    context.noteCachedReservedBinding(
			nameKey, results.oid, true);
		    usedCacheEntry();
		}
	    } else if (upgradeName) {
		assert results.found;
		Object lock = cache.getBindingLock(nameKey);
		synchronized (lock) {
		    BindingCacheEntry entry = cache.getBindingEntry(nameKey);
		    context.noteAccess(entry);
		    entry.setUpgraded(lock);
		}
	    }
	    if (results.found) {
		if (results.callbackEvict) {
		    scheduleTask(new EvictBindingTask(nameKey.getName()));
		}
		if (results.callbackDowngrade) {
		    scheduleTask(new DowngradeBindingTask(nameKey.getName()));
		}
	    }
	    Object lock = cache.getBindingLock(nextNameKey);
	    synchronized (lock) {
		BindingCacheEntry nextEntry =
		    cache.getBindingEntry(nextNameKey);
		BindingKey actualNextNameKey =
		    BindingKey.get(results.nextName);
		if (nextNameKey.equals(actualNextNameKey)) {
		    /* Already had the next name */
		    nextEntry.updatePreviousKey(nameKey, !results.found);
		    context.noteAccess(nextEntry);
		} else {
		    /* XXX: Cache previous key info? */
		    context.noteCachedReservedBinding(
			actualNextNameKey, results.nextOid, results.found);
		    usedCacheEntry();
		}		    
		nextEntry.setNotPendingPrevious(lock);
	    }
	    if (results.nextCallbackEvict) {
		scheduleTask(new EvictBindingTask(results.nextName));
	    }
	    if (results.nextCallbackDowngrade) {
		scheduleTask(new DowngradeBindingTask(results.nextName));
	    }
	}
    }

    /**
     * Implement {@code removeBinding} for when a cache entry was found for the
     * requested name and the next step is to find the entry for the next name.
     *
     * @param	context the transaction info
     * @param	nameKey the requested name
     * @param	nameValue the value of the requested name, which may be {@code
     *		-1} to indicate that the name was unbound
     * @param	nameWritable whether the name entry was writable
     * @return	information about the binding, if the entries for the name and
     *		the next name were found and were cached properly, else {@code
     *		null}
     */
    private BindingValue removeBindingInternalCheckNext(TxnContext context,
							BindingKey nameKey,
							long nameValue,
							boolean nameWritable)
    {
	long stop = context.getStopTime();
	BindingCacheEntry entry = cache.getHigherBindingEntry(nameKey);
	Object lock = cache.getBindingLock(entry.key);
	synchronized (lock) {
	    if (nameWritable &&
		entry.getIsNextEntry(nameKey) &&
		(entry.getWritable() ||
		 (entry.getReadable() && nameValue == -1)))
	    {
		/*
		 * The name entry was writable, we found the next entry, and
		 * the next entry was either writable, or it was readable and
		 * the name was unbound.  We're done!
		 */
		return new BindingValue(nameValue, entry.key.getName());
	    } else if (!assureNextEntry(entry, nameKey, lock, stop)) {
		/* Don't have the next entry -- try again */
		return null;
	    }
	    if (!nameWritable) {
		entry.setFetchingUpgrade();
	    }
	    entry.setPendingPrevious();
	    scheduleFetch(
		new GetBindingForRemoveRunnable(
		    context, nameKey, entry.key, false, !nameWritable));
	    entry.awaitNotPendingPrevious(lock, stop);
	    /* Fetched more information -- try again */
	    return null;
	}
    }

    /* DataStore.nextBoundName */

    /** {@inheritDoc} */
    protected String nextBoundNameInternal(Transaction txn, String name) {
	TxnContext context = contextMap.join(txn);
	long stop = context.getStopTime();
	BindingKey nameKey = BindingKey.get(name);
	while (true) {
	    /* Find next entry */
	    BindingCacheEntry entry = cache.getCeilingBindingEntry(nameKey);
	    Object lock = cache.getBindingLock(
		(entry != null) ? entry.key : BindingKey.LAST);
	    synchronized (lock) {
		if (entry == null) {
		    /* No next entry -- create it */
		    entry = context.noteLastBinding();
		} else if (!entry.awaitReadable(lock, stop)) {
		    /* The entry is not in the cache -- try again */
		    continue;
		} else if (entry.getIsNextEntry(nameKey)) {
		    /* This is the next entry in the cache */
		    context.noteAccess(entry);
		    return entry.key.getName();
		}
		/* Confirm that we have the next entry in the cache */
		if (!assureNextEntry(entry, nameKey, lock, stop)) {
		    continue;
		}
		/* Ask server for next bound name */
		entry.setPendingPrevious();
		scheduleFetch(
		    new NextBoundNameRunnable(context, nameKey, entry.key));
		entry.awaitNotPendingPrevious(lock, stop);
		if (entry.getReadable() && entry.getIsNextEntry(nameKey)) {
		    /* This entry holds the next bound name */
		    context.noteAccess(entry);
		    return entry.key.getName();
		} else {
		    /*
		     * Either another entry was inserted between the name and
		     * this entry or else this entry got decached -- try again
		     */
		    continue;
		}
	    }
	}
    }

    /**
     * A {@link Runnable} that calls {@code nextBoundName} on the server to get
     * information about the next bound name after a specified name when that
     * information was not in the cache.
     */
    private class NextBoundNameRunnable
	extends ReserveCacheRetryIoRunnable<NextBoundNameResults>
    {
	private final TxnContext context;
	private final BindingKey key;
	private final BindingKey nextKey;
	NextBoundNameRunnable(
	    TxnContext context, BindingKey key, BindingKey nextKey)
	{
	    this.context = context;
	    this.key = key;
	    this.nextKey = nextKey;
	}
	NextBoundNameResults callOnce() throws IOException {
	    return server.nextBoundName(nodeId, key.getName());
	}
	void callWithResult(NextBoundNameResults results) {
	    BindingKey realNextKey = BindingKey.get(results.nextName);	    
	    Object lock = cache.getBindingLock(realNextKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(realNextKey);
		if (nextKey.equals(realNextKey)) {
		    /* We already had the right next entry */
		    context.noteAccess(entry);
		    entry.updatePreviousKey(key, false);
		    entry.setNotPendingPrevious(lock);
		} else {
		    /* Create an entry for the actual next bound name */
		    context.noteCachedReservedBinding(
			realNextKey, results.oid, false);
		}
	    }
	    if (results.callbackEvict) {
		scheduleTask(new EvictBindingTask(results.nextName));
	    }
	}
    }

    /* Shutdown */

    /** {@inheritDoc} */
    protected void shutdownInternal() {
	synchronized (shutdownSync) {
	    switch (shutdownState) {
	    case NOT_REQUESTED:
		shutdownState = ShutdownState.REQUESTED;
		break;
	    case REQUESTED:
	    case TXNS_COMPLETED:
		do {
		    try {
			shutdownState.wait();
		    } catch (InterruptedException e) {
		    }
		} while (shutdownState != ShutdownState.COMPLETED);
		return;
	    case COMPLETED:
		return;
	    }
	}
	callbackExporter.unexport();
	contextMap.shutdown();
	synchronized (shutdownSync) {
	    shutdownState = ShutdownState.TXNS_COMPLETED;
	}
	/* Continue evictions until all transactions are complete */
	evictionThread.interrupt();
	if (newObjectIdCache != null) {
	    newObjectIdCache.shutdown();
	}
	fetchExecutor.shutdownNow();
	try {
	    evictionThread.join(10000);
	    fetchExecutor.awaitTermination(10000, MILLISECONDS);
	} catch (InterruptedException e) {
	}
	if (updateQueue != null) {
	    updateQueue.shutdown();
	}
	if (localServer != null) {
	    localServer.shutdown();
	}
	synchronized (shutdownSync) {
	    shutdownState = ShutdownState.COMPLETED;
	    shutdownSync.notifyAll();
	}
    }

    /* getClassId */

    /** {@inheritDoc} */
    protected int getClassIdInternal(Transaction txn, byte[] classInfo) {
	contextMap.join(txn);
	return new GetClassIdCallable(classInfo).call();
    }

    private class GetClassIdCallable extends RetryIoCallable<Integer> {
	private final byte[] classInfo;
	GetClassIdCallable(byte[] classInfo) {
	    super(CachingDataStore.this);
	    this.classInfo = classInfo;
	}
	Integer callOnce() throws IOException {
	    return server.getClassId(classInfo);
	}
    }

    /* getClassInfo */

    /** {@inheritDoc} */
    protected byte[] getClassInfoInternal(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	contextMap.join(txn);
	return new GetClassInfoCallable(classId).call();
    }

    private class GetClassInfoCallable extends RetryIoCallable<byte[]> {
	private final int classId;
	GetClassInfoCallable(int classId) {
	    super(CachingDataStore.this);
	    this.classId = classId;
	}
	byte[] callOnce() throws IOException {
	    return server.getClassInfo(classId);
	}
    }

    /* nextObjectId */

    /** {@inheritDoc} */
    protected long nextObjectIdInternal(Transaction txn, long oid) {
	TxnContext context = contextMap.join(txn);
	NextObjectResults results = new NextObjectIdCallable(oid).call();
	if (results == null) {
	    return -1;
	}
	Object lock = cache.getObjectLock(results.oid);
	synchronized (lock) {
	    ObjectCacheEntry entry = cache.getObjectEntry(results.oid);
	    if (entry == null) {
		/* No entry -- create it */
		entry = context.noteCachedObject(results.oid, results.data);
	    } else {
		context.noteAccess(entry);
	    }
	}
	if (results.callbackEvict) {
	    scheduleTask(new EvictObjectTask(results.oid));
	}
	return results.oid;
    }

    private class NextObjectIdCallable
	extends RetryIoCallable<NextObjectResults>
    {
	private final long oid;
	NextObjectIdCallable(long oid) {
	    super(CachingDataStore.this);
	    this.oid = oid;
	}
	NextObjectResults callOnce() throws IOException {
	    return server.nextObjectId(nodeId, oid);
	}
    }

    /* -- Implement AbstractDataStore's TransactionParticipant methods -- */

    /** {@inheritDoc} */
    protected boolean prepareInternal(Transaction txn) {
	return contextMap.prepare(txn);
    }

    /** {@inheritDoc} */
    protected void prepareAndCommitInternal(Transaction txn) {
	contextMap.prepareAndCommit(txn);
    }

    /** {@inheritDoc} */
    protected void commitInternal(Transaction txn) {
	contextMap.commit(txn);
    }

    /** {@inheritDoc} */
    protected void abortInternal(Transaction txn) {
	contextMap.abort(txn);
    }

    /* -- Implement CallbackServer -- */

    /* CallbackServer.requestDowngradeObject */

    /** {@inheritDoc} */
    public boolean requestDowngradeObject(long oid, long nodeId) {
	Object lock = cache.getObjectLock(oid);
	synchronized (lock) {
	    ObjectCacheEntry entry = cache.getObjectEntry(oid);
	    if (entry == null) {
		/* Already evicted */
		return true;
	    } else if (entry.getDowngrading()) {
		/* Already being downgraded, but need to wait for completion */
		return false;
	    } else if (!entry.getWritable() && !entry.getUpgrading()) {
		/* Already downgraded and not being upgraded */
		return true;
	    } else if (!inUseForWrite(entry)) {
		/* OK to downgrade immediately */
		entry.setEvictedDowngradeImmediate(lock);
		return true;
	    } else {
		/* Downgrade when not in use */
		scheduleTask(new DowngradeObjectTask(oid));
		return false;
	    }
	}
    }
    
    /**
     * A {@link KernelRunnable} that downgrades an object after accessing it
     * for read.
     */
    private class DowngradeObjectTask extends AbstractKernelRunnable
	implements SimpleCompletionHandler
    {
	private final long oid;
	DowngradeObjectTask(long oid) {
	    super(String.valueOf(oid));
	    this.oid = oid;
	}
	public void run() {
	    reportObjectAccess(txnProxy.getCurrentTransaction(), oid, READ);
	    synchronized (cache.getObjectLock(oid)) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		/* Check if cached for write and not downgrading */
		if (entry != null &&
		    entry.getWritable() &&
		    !entry.getDowngrading())
		{
		    assert !inUseForWrite(entry);
		    entry.setEvictingDowngrade();
		    updateQueue.downgradeObject(
			entry.getContextId(), oid, this);
		}
	    }
	}
	public void completed() {
	    Object lock = cache.getObjectLock(oid);
	    synchronized (lock) {
		cache.getObjectEntry(oid).setEvictedDowngrade(lock);
	    }
	}
    }

    /* CallbackServer.requestEvictObject */

    /** {@inheritDoc} */
    public boolean requestEvictObject(long oid, long nodeId) {
	Object lock = cache.getObjectLock(oid);
	synchronized (lock) {
	    ObjectCacheEntry entry = cache.getObjectEntry(oid);
	    if (entry == null) {
		/* Already evicted */
		return true;
	    } else if (entry.getDecaching()) {
		/* Already being evicted, but need to wait for completion */
		return false;
	    } else if (!entry.getReading() &&
		       !entry.getUpgrading() &&
		       !inUse(entry))
	    {
		/*
		 * Not reading, not upgrading, and not in use, so OK to evict
		 * immediately
		 */
		entry.setEvictedImmediate(lock);
		cache.removeObjectEntry(oid);
		return true;
	    } else {
		/* Evict when not in use */
		scheduleTask(new EvictObjectTask(oid));
		return false;
	    }
	}
    }

    /**
     * A {@link KernelRunnable} that evicts an object after accessing it for
     * write.
     */
    private class EvictObjectTask extends AbstractKernelRunnable
	implements SimpleCompletionHandler
    {
	private final long oid;
	EvictObjectTask(long oid) {
	    super(String.valueOf(oid));
	    this.oid = oid;
	    pendingEvictions.incrementAndGet();
	}
	public void run() {
	    reportObjectAccess(txnProxy.getCurrentTransaction(), oid, WRITE);
	    synchronized (cache.getObjectLock(oid)) {
		ObjectCacheEntry entry = cache.getObjectEntry(oid);
		/* Check if cached and not evicting */
		if (entry != null &&
		    entry.getReadable() &&
		    !entry.getDecaching())
		{
		    assert !inUse(entry);
		    if (entry.getWritable()) {
			entry.setEvictingWrite();
		    } else {
			entry.setEvictingRead();
		    }
		    updateQueue.evictObject(entry.getContextId(), oid, this);
		}
	    }
	}
	public void completed() {
	    Object lock = cache.getObjectLock(oid);
	    synchronized (lock) {
		cache.getObjectEntry(oid).setEvictedRead(lock);
		cache.removeObjectEntry(oid);
		pendingEvictions.decrementAndGet();
	    }
	}
    }

    /* CallbackServer.requestDowngradeBinding */

    /** {@inheritDoc} */
    public boolean requestDowngradeBinding(String name, long nodeId) {
	BindingKey nameKey = BindingKey.get(name);
	Object lock = cache.getBindingLock(nameKey);
	synchronized (lock) {
	    BindingCacheEntry entry = cache.getBindingEntry(nameKey);
	    if (entry == null) {
		/* Already evicted */
		return true;
	    } else if (entry.getDowngrading()) {
		/* Already being downgraded, but need to wait for completion */
		return false;
	    } else if (!entry.getWritable() &&
		       !entry.getUpgrading() &&
		       !entry.getPendingPrevious())
	    {
		/*
		 * Already downgraded, and not being upgraded or the next entry
		 * after a previous entry request
		 */
		return true;
	    } else if (!inUseForWrite(entry)) {
		/* OK to downgrade immediately */
		entry.setEvictedDowngradeImmediate(lock);
		return true;
	    } else {
		/* Downgrade when not in use */
		scheduleTask(new DowngradeBindingTask(name));
		return false;
	    }
	}
    }

    /**
     * A {@link KernelRunnable} that downgrades a binding after accessing it
     * for read.
     */
    private class DowngradeBindingTask extends AbstractKernelRunnable
	implements SimpleCompletionHandler
    {
	private final String name;
	DowngradeBindingTask(String name) {
	    super(name);
	    this.name = name;
	}
	public void run() {
	    reportNameAccess(txnProxy.getCurrentTransaction(), name, READ);
	    BindingKey nameKey = BindingKey.get(name);
	    Object lock = cache.getBindingLock(nameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nameKey);
		entry.awaitNotPendingPrevious(
		    lock, System.currentTimeMillis() + lockTimeout);
		/* Check if cached for write and not downgrading */
		if (entry != null &&
		    entry.getWritable() &&
		    !entry.getDowngrading())
		{
		    assert !inUseForWrite(entry);
		    entry.setEvictingDowngrade();
		    updateQueue.downgradeBinding(
			entry.getContextId(), name, this);
		}
	    }
	}
	public void completed() {
	    BindingKey nameKey = BindingKey.get(name);
	    Object lock = cache.getBindingLock(nameKey);
	    synchronized (lock) {
		cache.getBindingEntry(nameKey).setEvictedDowngrade(lock);
	    }
	}
    }

    /* CallbackServer.requestEvictBinding */

    /** {@inheritDoc} */
    public boolean requestEvictBinding(String name, long nodeId) {
	BindingKey nameKey = BindingKey.get(name);
	Object lock = cache.getBindingLock(nameKey);
	synchronized (lock) {
	    BindingCacheEntry entry = cache.getBindingEntry(nameKey);
	    if (entry == null) {
		/* Already evicted */
		return true;
	    } else if (entry.getDecaching()) {
		/* Already being evicted, but need to wait for completion */
		return false;
	    } else if (!entry.getReading() &&
		       !entry.getUpgrading() &&
		       !inUse(entry))
	    {
		/*
		 * Not reading, not upgrading, and not in use, so OK to evict
		 * immediately
		 */
		entry.setEvictedImmediate(lock);
		cache.removeBindingEntry(nameKey);
		return true;
	    } else {
		/* Evict when not in use */
		scheduleTask(new EvictBindingTask(name));
		return false;
	    }
	}
    }

    /**
     * A {@link KernelRunnable} that evicts a binding after accessing it for
     * write.
     */
    private class EvictBindingTask extends AbstractKernelRunnable
	implements SimpleCompletionHandler
    {
	private final String name;
	EvictBindingTask(String name) {
	    super(name);
	    this.name = name;
	    pendingEvictions.incrementAndGet();
	}
	public void run() {
	    reportNameAccess(txnProxy.getCurrentTransaction(), name, WRITE);
	    BindingKey nameKey = BindingKey.get(name);
	    Object lock = cache.getBindingLock(nameKey);
	    synchronized (lock) {
		BindingCacheEntry entry = cache.getBindingEntry(nameKey);
		entry.awaitNotPendingPrevious(
		    lock, System.currentTimeMillis() + lockTimeout);
		/* Check if cached and not evicting */
		if (entry != null &&
		    entry.getReadable() &&
		    !entry.getDecaching())
		{
		    assert !inUse(entry);
		    if (entry.getWritable()) {
			entry.setEvictingWrite();
		    } else {
			entry.setEvictingRead();
		    }
		    updateQueue.evictBinding(entry.getContextId(), name, this);
		}
	    }
	}
	public void completed() {
	    BindingKey nameKey = BindingKey.get(name);
	    Object lock = cache.getBindingLock(nameKey);
	    synchronized (lock) {
		cache.getBindingEntry(nameKey).setEvictedRead(lock);
		cache.removeBindingEntry(nameKey);
		pendingEvictions.decrementAndGet();
	    }
	}
    }

    /* -- Methods related to shutdown -- */

    /**
     * Checks whether a shutdown has been requested.
     *
     * @return	whether a shutdown has been requested
     */
    boolean getShutdownRequested() {
	synchronized (shutdownSync) {
	    return shutdownState != ShutdownState.NOT_REQUESTED;
	}
    }

    /**
     * Checks whether a shutdown has been requested and all active transactions
     * have completed.
     */
    boolean getShutdownTxnsCompleted() {
	synchronized (shutdownSync) {
	    switch (shutdownState) {
	    case NOT_REQUESTED:
	    case REQUESTED:
		return false;
	    case TXNS_COMPLETED:
	    case COMPLETED:
		return true;
	    default:
		throw new AssertionError();
	    }
	}
    }

    /**
     * Report that the local node should be marked as failed because of a
     * failure within the data store.
     */
    void reportFailure() {
	new Thread("CachingDataStore reportFailure") {
	    public void run() {
		watchdogService.reportFailure(
		    nodeId, CachingDataStore.class.getName());
	    }
	}.start();
    }

    /* -- Other methods -- */

    CachingDataStoreServer getServer() {
	return server;
    }

    UpdateQueue getUpdateQueue() {
	return updateQueue;
    }

    Cache getCache() {
	return cache;
    }

    void scheduleFetch(Runnable runnable) {
	fetchExecutor.execute(runnable);
    }

    void scheduleTask(KernelRunnable task) {
	txnScheduler.scheduleTask(task, taskOwner);
    }

    long getNodeId() {
	return nodeId;
    }

    long getMaxRetry() {
	return maxRetry;
    }

    /* -- Utility methods -- */

    private boolean inUse(BasicCacheEntry<?, ?> cacheEntry) {
	/* Also consider previousPending */
	return cacheEntry.getContextId() <
	    updateQueue.highestPendingContextId();
    }

    private boolean inUseForWrite(BasicCacheEntry<?, ?> cacheEntry) {
	return inUse(cacheEntry) && cacheEntry.getModified();
    }

    /* -- Utility classes -- */

    /**
     * A {@code Runnable} that chooses least recently used entries to evict
     * from the cache as needed to make space for new entries.
     */
    private class EvictionThread extends Thread {

	/**
	 * Whether the evictor has reserved cache entries for use during
	 * eviction.
	 */
	private boolean reserved;

	/** A iterator that repeatedly circles over all cache entries. */
	private final Iterator<BasicCacheEntry<?, ?>> entryIterator =
	    cache.getEntryIterator(evictionBatchSize);

	/** Creates an instance of this class. */
	EvictionThread() {
	    super("CachingDataStore eviction");
	    if (cache.tryReserve(evictionReserveSize)) {
		reserved = true;
	    }
	}

	/* -- Implement Runnable -- */

	@Override
	public void run() {
	    while (true) {
		if (getShutdownTxnsCompleted()) {
		    break;
		}
		if (reserved) {
		    if (cache.available() == 0) {
			/*
			 * The cache is full -- release the reserve and start
			 * evicting
			 */
			cache.release(evictionReserveSize);
			reserved = false;
			tryEvict();
			continue;
		    }
		} else if (cache.available() >= 2 * evictionReserveSize) {
		    /* The cache has plenty of space -- set up the reserve */
		    if (cache.tryReserve(evictionReserveSize)) {
			reserved = true;
		    }
		} else if (cache.available() + pendingEvictions.get() <
			   2 * evictionReserveSize)
		{
		    /*
		     * Need to initiate more evictions to be on target for
		     * obtaining two times the reserve size of free entries
		     */
		    tryEvict();
		    continue;
		}
		/* Enough space -- wait to get full */
		try {
		    synchronized (this) {
			wait();
		    }
		} catch (InterruptedException e) {
		}
	    }
	}

	/**
	 * Scan evictionBatchSize entries in the cache, and evict the best
	 * candidate.
	 */
	private void tryEvict() {
	    BasicCacheEntry<?, ?> bestEntry = null;
	    EntryInfo bestInfo = null;
	    int i = 0;
	    for (Iterator<BasicCacheEntry<?, ?>> iter =
		     cache.getEntryIterator(evictionBatchSize);
		 iter.hasNext();
		 )
	    {
		BasicCacheEntry<?, ?> entry = iter.next();
		if (getShutdownTxnsCompleted()) {
		    return;
		} else if (i >= evictionBatchSize) {
		    break;
		}
		synchronized (cache.getEntryLock(entry)) {
		    if (entry.getDecached() || entry.getDecaching()) {
			/* Already decached or decaching */
			continue;
		    }
		    EntryInfo entryInfo = new EntryInfo(
			inUse(entry),
			entry.getModified(),
			entry.getContextId());
		    if (bestEntry == null || entryInfo.preferTo(bestInfo)) {
			bestEntry = entry;
			bestInfo = entryInfo;
		    }
		}
	    }
	    if (bestEntry != null) {
		synchronized (cache.getEntryLock(bestEntry)) {
		    if (!bestEntry.getDecached()) {
			if (!inUse(bestEntry)) {
			    if (bestEntry instanceof ObjectCacheEntry) {
				evictObjectNow((ObjectCacheEntry) bestEntry);
			    } else {
				evictBindingNow((BindingCacheEntry) bestEntry);
			    }
			} else {
			    scheduleTask(
				(bestEntry instanceof ObjectCacheEntry)
				? new EvictObjectTask(
				    ((ObjectCacheEntry) bestEntry).key)
				: new EvictBindingTask(
				    ((BindingCacheEntry) bestEntry)
				    .key.getName()));
			}
		    }
		}
	    }
	}

	private void evictObjectNow(ObjectCacheEntry entry) {
	    pendingEvictions.incrementAndGet();
	    final long oid = entry.key;
	    updateQueue.evictObject(
		entry.getContextId(), oid,
		new SimpleCompletionHandler() {
		    public void completed() {
			Object lock = cache.getObjectLock(oid);
			synchronized (lock) {
			    cache.getObjectEntry(oid).setEvictedRead(lock);
			    cache.removeObjectEntry(oid);
			    pendingEvictions.decrementAndGet();
			}
		    }
		});
	} 

	private void evictBindingNow(BindingCacheEntry entry) {
	    pendingEvictions.incrementAndGet();
	    final BindingKey nameKey = entry.key;
	    updateQueue.evictBinding(
		entry.getContextId(), nameKey.getName(),
		new SimpleCompletionHandler() {
		    public void completed() {
			Object lock = cache.getBindingLock(nameKey);
			synchronized (lock) {
			    cache.getBindingEntry(nameKey).setEvictedRead(
				lock);
			    cache.removeBindingEntry(nameKey);
			    pendingEvictions.decrementAndGet();
			}
		    }
		});
	}
    }

    /**
     * Records information about a cache entry for use by the evictor when
     * comparing entries for LRU eviction.
     */
    private static class EntryInfo {

	/** Whether the entry was in use. */
	private final boolean inUse;

	/** Whether the entry was in use for write. */
	private final boolean inUseForWrite;

	/** The transaction context ID when the entry was last used. */
	private final long contextId;

	/** Creates an instance of this class. */
	EntryInfo(boolean inUse, boolean inUseForWrite, long contextId) {
	    this.inUse = inUse;
	    this.inUseForWrite = inUseForWrite;
	    this.contextId = contextId;
	}

	/**
	 * Determines whether the entry associated with this instance should be
	 * preferred to the one associated with the argument.  Entries are
	 * preferred if they are not in use, if they are not in use for read,
	 * and if they were last used by an older transaction.
	 */
	boolean preferTo(EntryInfo other) {
	    if (inUse != other.inUse) {
		return !inUse;
	    } else if (inUseForWrite != other.inUseForWrite) {
		return !inUseForWrite;
	    } else {
		return contextId < other.contextId;
	    }
	}
    }

    /**
     * A {@link Runnable} that calls {@code registerNode} on the server and
     * records the result.
     */
    private class RegisterNodeCallable
	extends RetryIoRunnable<RegisterNodeResult>
	implements Callable<RegisterNodeResult>
    {
	/** The result of calling {@code registerNode} on the server. */
	RegisterNodeResult result;

	/** Creates an instance of this class. */
	RegisterNodeCallable() {
	    super(CachingDataStore.this);
	}

	/** Abandon the call if shutdown is requested. */
	@Override
	boolean shouldShutdown() {
	    return getShutdownRequested();
	}

	RegisterNodeResult callOnce() throws IOException {
	    return server.registerNode(callbackProxy);
	}

	void callWithResult(RegisterNodeResult result) {
	    this.result = result;
	}

	public RegisterNodeResult call() {
	    run();
	    if (result == null) {
		throw new IllegalStateException(
		    "CacingDataStore has been shut down");
	    }
	    return result;
	}
    }

    private class GetServerCallable
	extends RetryIoRunnable<CachingDataStoreServer>
	implements Callable<CachingDataStoreServer>
    {
	private final String serverHost;
	private final int serverPort;
	private CachingDataStoreServer server;
	GetServerCallable(String serverHost, int serverPort) {
	    super(CachingDataStore.this);
	    this.serverHost = serverHost;
	    this.serverPort = serverPort;
	}
	/** Abandon the call if shutdown is requested. */
	@Override
	boolean shouldShutdown() {
	    return getShutdownRequested();
	}
	CachingDataStoreServer callOnce() throws IOException {
	    Registry registry = LocateRegistry.getRegistry(
		serverHost, serverPort);
	    try {
		return (CachingDataStoreServer) registry.lookup(
		    "CachingDataStoreServer");
	    } catch (NotBoundException e) {
		reportFailure();
		return null;
	    }
	}
	void callWithResult(CachingDataStoreServer result) {
	    server = result;
	}
	public CachingDataStoreServer call() {
	    run();
	    if (server == null) {
		throw new IllegalStateException(
		    "CachingDataStore has been shut down");
	    }
	    return server;
	}
    }
}
