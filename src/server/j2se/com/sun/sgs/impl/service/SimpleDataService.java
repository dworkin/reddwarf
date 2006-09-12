
package com.sun.sgs.impl.service;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.service.ContentionHandler;
import com.sun.sgs.service.ContentionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NotPreparedException;
import com.sun.sgs.service.Transaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This is a simple implementation of <code>DataService</code> that makes
 * no attempt to persist data, and keeps all objects in memory. It is
 * intended for testing use only, and should not be consdered completely
 * correct yet, as the semantics for it and its related components have
 * not been fully defined.
 * <p>
 * NOTE: A significant problem is that there is no clear way to define how
 * objects are marked as immutable, or copied when written. This means
 * that all objects managed by this service are single instances that are
 * shared with all developer code and are always valid. Obviously this
 * causes significant problems if you break the rules and muck with an
 * object you haen't called get on. Since there is no roll-back yet,
 * however, this problem isn't too significant. Again, this is a useful
 * test class, but for now that's all that it is.
 * <p>
 * A much smaller issue to note is that this class does not support the
 * case where a named object is destroyed and then a new object is managed
 * with the same name, both in the same transaction. For testing code,
 * this is reasonable, but were this implementation to become more critical,
 * this bug would obviously need to be fixed.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleDataService implements DataService
{

    /**
     * The identifier for this <code>Service</code>.
     */
    public static final String IDENTIFIER =
        SimpleDataService.class.getName();

    // the proxy used to resolve transaction state as needed
    private TransactionProxy transactionProxy;

    // the context in which this instance runs
    private AppContext appContext;

    // the contention service
    private ContentionService contentionService;

    // the state map for each active transaction
    private ConcurrentHashMap<Long,TxnState> txnMap;

    // the generator for object identifiers
    private AtomicLong objectIdGenerator;

    // the namespace mapping, in both directions
    private ConcurrentHashMap<String,Long> nameSpace;
    private ConcurrentHashMap<Long,String> reverseNameSpace;

    // the locks that are currently held, and who holds them
    private ConcurrentHashMap<Long,LockState> lockMap;

    // the actual data space
    private ConcurrentHashMap<Long,ManagedObject> dataSpace;

    /**
     * Creates an instance of <code>SimpleDataService</code>.
     */
    public SimpleDataService() {
        txnMap = new ConcurrentHashMap<Long,TxnState>();

        nameSpace = new ConcurrentHashMap<String,Long>();
        reverseNameSpace = new ConcurrentHashMap<Long,String>();
        lockMap = new ConcurrentHashMap<Long,LockState>();
        dataSpace = new ConcurrentHashMap<Long,ManagedObject>();

        objectIdGenerator = new AtomicLong(1);
    }

    /**
     * Returns the identifier used to identify this service.
     *
     * @return the service's identifier
     */
    public String getIdentifier() {
        return IDENTIFIER;
    }

    /**
     * Configures this <code>Service</code>.
     *
     * @param appContext this <code>Service</code>'s <code>AppContext</code>
     * @param transactionProxy a non-null proxy that provides access to the
     *                         current <code>Transaction</code>
     * @param contentionService the <code>Service</code> used to handle
     *                          contention between transactions
     */
    public void configure(AppContext appContext,
                          TransactionProxy transactionProxy,
                          ContentionService contentionService) {
        this.appContext = appContext;
        this.transactionProxy = transactionProxy;
        this.contentionService = contentionService;
    }

    /**
     * Private helper that notifies the next listener for a given object.
     */
    private void notifyNext(Object listener) {
        synchronized(listener) {
            listener.notify();
        }
    }

    /**
     * Private helper that acquires a particular lock. It returns true if
     * the lock was taken. False is returned only if the object doesn't
     * exist (which could happen while we're trying to get the lock).
     */
    private boolean acquireLock(long objectId, TxnState txnState) {
        // get the lock state for the object, if it exists
        LockState lockState = lockMap.get(objectId);
        if (lockState == null)
            return false;

        // aquire the lock by atomically flipping the flag
        while (! lockState.lock.compareAndSet(false, true)) {
            // we failed to acquire the lock, so someone else already
            // has it...call the contention handler for this transaction,
            // first making sure that we have one
            if (txnState.contentionHandler == null)
                txnState.contentionHandler =
                    contentionService.getContentionHandler(txnState.txn);

            // NOTE: if this returns true, then we stole the lock from
            // someone else, which means that we should just have the lock
            // and proceed...the problem is that the current handler
            // semantics aren't well defined, and so it's possible that
            // we got the lock because the other party was just committing
            // and has released the lock. While this happens, a third party
            // could have come along and grabbed the lock. So, at this point
            // we loop through, trying again to get the lock and calling
            // the handler if there's a problem. This will be fixed after
            // it's clear exactly what the handler provides.
            if (! txnState.contentionHandler.
                  resolveConflict(lockState.holder.txn)) {
                try {
                    txnState.contentionHandler.approveBlockingCall();
                    synchronized(lockState.lock) {
                        lockState.lock.wait();
                    }
                } catch (InterruptedException ie) {
                    System.err.println("interrupted while waiting for lock " +
                                       "on " + objectId + ": " +
                                       ie.getMessage());
                }
            }
        }

        // we got the lock, so track that in the map
        // FIXME: if someone comes in right behind us, they could possibly
        // try the above lookup for the holder before we get here, which
        // means that we need to change the scheme to make both of those
        // opertations (getting the lock and setting the holder name) atomic
        lockState.holder = txnState;

        // double-check that the object wasn't deleted, since it could have
        // been removed after we fetched the boolean but while we were
        // waiting to flip the bit...a rare but valid case, so we re-set
        // the lock and notify anyone else waiting for this lock, who will
        // in-turn fall into this same block
        if (! lockMap.containsKey(objectId)) {
            lockState.lock.set(false);
            notifyNext(lockState.lock);
            return false;
        }

        return true;
    }

    /**
     * Private helper that release a particular lock, notifying the next
     * listener that the lock is now available.
     */
    private void releaseLock(long objectId) {
        // FIXME: check that the lock exists, and that we flipped the
        // flag correctly
        LockState lockState = lockMap.get(objectId);
        lockState.lock.compareAndSet(true, false);
        notifyNext(lockState.lock);
    }

    /**
     * Private helper that release all locks held in this transaction.
     */
    private void releaseAllLocks(TxnState txnState) {
        for (long objectId : txnState.lockedObjects.keySet())
            releaseLock(objectId);

        for (long objectId : txnState.deletedObjects)
            releaseLock(objectId);
    }

    /**
     * Tells the <code>Service</code> to prepare for commiting its
     * state assciated with the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void prepare(Transaction txn) throws Exception {
        // get the transaction state
        TxnState txnState = txnMap.get(txn.getId());

        // verify that the transaction is valid
        if (txnState == null)
            throw new Exception("Preparing invalid transaction in " +
                                "SimpleDataService: " + txn.getId());

        // reserve all the namespace entries with a negative value, tracking
        // which names we reserved in case we need to back out
        // FIXME: this doesn't actually protect against two transactions
        // that are trying to get overlapping namespace sets in different
        // order, but for now we'll assume that isn't a problem
        HashSet<String> takenNames = new HashSet<String>();
        for (String name : txnState.createdNamespaceMap.keySet()) {
            if (nameSpace.putIfAbsent(name, -1L) != null) {
                // someone already owns this namespace, so remove all the
                // names we've claimed...
                for (String takenName : takenNames)
                    nameSpace.remove(takenName);

                // ...release all our locks...
                // FIXME: am I right to think that abort is always called,
                // so we can do the release there?
                //releaseAllLocks(txnState);

                // ...and throw an exception
                throw new Exception("Namespace collision: " + name);
            }

            takenNames.add(name);
        }

        // finally, mark the transaction state as prepared
        txnState.prepared = true;
    }

    /**
     * Tells the <code>Service</code> to commit its state associated
     * with the previously prepared transaction.
     *
     * @param txn the <code>Transaction</code> state
     *
     * @throws NotPreparedException if prepare wasn't previously called
     *                              on this service for this transaction
     */
    public void commit(Transaction txn) throws NotPreparedException {
        // get the transaction state
        TxnState txnState = txnMap.get(txn.getId());

        // verify that the transaction is valid and has been prepared
        if ((txnState == null) || (! txnState.prepared))
            throw new NotPreparedException("SimpleDataService: Transaction " +
                                           txn.getId() + " not prepared");

        // commit the new objects and create the lock structure...all object
        // identifiers are unique, so there should never be contention here
        for (Map.Entry<Long,ManagedObject> entry :
                 txnState.createdObjects.entrySet()) {
            lockMap.put(entry.getKey(), new LockState());
            dataSpace.put(entry.getKey(), entry.getValue());
        }

        // commit the locked objects and then unlock them
        for (Map.Entry<Long,ManagedObject> entry :
                 txnState.lockedObjects.entrySet()) {
            dataSpace.put(entry.getKey(), entry.getValue());
            releaseLock(entry.getKey());
        }

        // update the previously reserved namespaces with the object ids
        // NOTE: if an object was managed and then destroyed in this
        // transaction, and was managed with a name, then for a breif
        // moment (before the next loop) that name will be exposed even
        // though the object will never be available. It's a small enough
        // issue that the reverse map required to fix it isn't being
        // added, but if this test code became more critical, then this
        // feature would be needed
        for (Map.Entry<String,Long> entry :
                 txnState.createdNamespaceMap.entrySet()) {
            nameSpace.put(entry.getKey(), entry.getValue());
            reverseNameSpace.put(entry.getValue(), entry.getKey());
        }

        // remove the forward namespace mapping for objects that are
        // about to be deleted
        for (String name : txnState.deletedNamespaceSet)
            nameSpace.remove(name);

        // commit the deleted objects, removing the locks too
        for (long objId : txnState.deletedObjects) {
            reverseNameSpace.remove(objId);
            dataSpace.remove(objId);
            AtomicBoolean lockFlag = lockMap.remove(objId).lock;
            lockFlag.set(false);
            notifyNext(lockFlag);
        }

        // finally, remove this transaction state from the map
        txnMap.remove(txn.getId());
    }

    /**
     * Tells the <code>Service</code> to both prepare and commit its
     * state associated with the given transaction. This is provided as
     * an optimization for cases where the sysem knows that a given
     * transaction cannot fail, or can be partially backed out.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        prepare(txn);
        commit(txn);
    }

    /**
     * Tells the <code>Service</code> to abort its involvement with
     * the given transaction.
     *
     * @param txn the <code>Transaction</code> state
     */
    public void abort(Transaction txn) {
        // remove the state so it can't be used any more
        TxnState txnState = txnMap.remove(txn.getId());

        // release any locks we held
        releaseAllLocks(txnState);
    }

    /**
     * Private helper that gets the transaction state, or creates it (and
     * joins to the transaction) if the state doesn't exist. This is only
     * used by the methods that follow in this class (ie, not prepare and
     * commit).
     */
    private TxnState getTxnState() {
        // resolve the current transaction
        Transaction txn = transactionProxy.getCurrentTransaction();

        // try to get the current state
        TxnState txnState = txnMap.get(txn.getId());

        // if it didn't exist yet then create it and joing the transaction
        if (txnState == null) {
            txnState = new TxnState();
            txnState.txn = txn;
            txnMap.put(txn.getId(), txnState);
            txn.join(this);
        } else {
            // if it's already been prepared then we shouldn't be using
            // it...note that this shouldn't be a problem, since the system
            // shouldn't let this case get tripped, so this is just defensive
            // FIXME: what exception should actually get thrown?
            if (txnState.prepared)
                throw new RuntimeException("Trying to access prepared txn");
        }

        return txnState;
    }

    /**
     * Tells the service to manage this object. The object has no name
     * associated with it.
     *
     * @param object the object to manage
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object) {
        // generate a new identifier
        long objId = objectIdGenerator.getAndIncrement();

        // add this to the created objects for this transaction
        TxnState txnState = getTxnState();
        txnState.createdObjects.put(objId, object);

        // put this in the peek set for future access
        txnState.peekedObjects.put(objId, object);

        // return the new reference
        return new SimpleManagedReference<T>(objId, this);
    }

    /**
     * Tells the service to manage this object. The object is associated
     * with the given name for future searching.
     * <p>
     * FIXME: this returns null if the name is taken, right?
     *
     * @param object the object to manage
     * @param objectName the name of the object
     *
     * @return a reference to the newly managed object
     */
    public <T extends ManagedObject>
            ManagedReference<T> manageObject(T object, String objectName) {
        // start by checking that the name is available
        if (nameSpace.containsKey(objectName))
            return null;

        // manage the object
        SimpleManagedReference<T> ref =
            (SimpleManagedReference<T>)(manageObject(object));

        // now get the transaction state and track the name addition
        TxnState txnState = getTxnState();
        txnState.createdNamespaceMap.put(objectName, ref.getObjectId());

        // return the new reference
        return ref;
    }

    /**
     * Tries to find an already managed object based on that object's
     * name. If no object can be found with the given name then null
     * is returned.
     *
     * @param objectName the name of the object
     *
     * @return a reference to the object, or null
     */
    public ManagedReference<? extends ManagedObject>
            findManagedObject(String objectName) {
        // get the transaction state
        TxnState txnState = getTxnState();
        
        if (txnState.createdNamespaceMap.containsKey(objectName)) {
            // the name was defined in this transaction
            Long objId = txnState.createdNamespaceMap.get(objectName);
            return new SimpleManagedReference<ManagedObject>(objId, this);
        }

        if (nameSpace.containsKey(objectName)) {
            // the name exists in the data space
            Long objId = nameSpace.get(objectName);
            return new SimpleManagedReference<ManagedObject>(objId, this);
        }

        // there's no match for this name
        return null;
    }

    /**
     * Locks the referenced object and returns the associated value.
     *
     * @param reference the object's reference
     *
     * @return the object
     */
    public <T extends ManagedObject> T get(ManagedReference<T> reference) {
        // get the transaction state and the object id
        TxnState txnState = getTxnState();
        long objId = ((SimpleManagedReference)reference).getObjectId();

        // make sure the object hasn't been deleted
        if (txnState.deletedObjects.contains(objId))
            return null;

        // see if this is already in the lock set
        if (txnState.lockedObjects.containsKey(objId))
            return (T)(txnState.lockedObjects.get(objId));

        // see if this was created in this transaction
        if (txnState.createdObjects.containsKey(objId))
            return (T)(txnState.createdObjects.get(objId));

        // the object isn't already locked, so lock it, fetch it from the
        // data space, and store it in the lock & peek sets before returning

        // if we don't get the lock, it's because the object was deleted
        if (! acquireLock(objId, txnState))
            return null;

        T obj = (T)(dataSpace.get(objId));
        txnState.lockedObjects.put(objId, obj);
        txnState.peekedObjects.put(objId, obj);
        return obj;
    }

    /**
     * Returns the referenced object for read-only access without locking it.
     *
     * @param reference the object's reference
     *
     * @return the object
     */
    public <T extends ManagedObject> T peek(ManagedReference<T> reference) {
        // get the transaction state and the object id
        TxnState txnState = getTxnState();
        long objId = ((SimpleManagedReference)reference).getObjectId();

        // make sure the object hasn't been deleted
        if (txnState.deletedObjects.contains(objId))
            return null;

        // if the object exists in the peek set, then it's always the
        // right one to use because it's either the case that we've only
        // ever peeked, or we did a manage/get and put that reference
        // in the peek set
        if (txnState.peekedObjects.containsKey(objId))
            return (T)(txnState.peekedObjects.get(objId));

        // if the object isn't in the peek set, then fetch it from the
        // data space (without locking it, obviously)
        T obj = (T)(dataSpace.get(objId));
        txnState.peekedObjects.put(objId, obj);
        return obj;
    }

    /**
     * Destroys the referenced object.
     *
     * @param reference a reference to the object to destroy
     */
    public void destroyManagedObject(
            ManagedReference<? extends ManagedObject> reference) {
        // get the transaction state and the object id
        TxnState txnState = getTxnState();
        long objId = ((SimpleManagedReference)reference).getObjectId();
        
        // FIXME: Check if it's already in deletedObjects -- someone
        // might have obtained a different ManagedReference to it and
        // called destroy on that reference. -jm

        // check if this is something we've created within this transaction,
        // and if so just clear it from the state
        if (txnState.createdObjects.containsKey(objId)) {
            txnState.createdObjects.remove(objId);
            return;
        }

        // get the name of the object, and if it's non-null, track it
        // for removal
        String name = reverseNameSpace.get(objId);
        if (name != null)
            txnState.deletedNamespaceSet.add(name);

        // check if it's something that we've already locked
        if (txnState.lockedObjects.containsKey(objId)) {
            txnState.lockedObjects.remove(objId);
            txnState.deletedObjects.add(objId);
            return;
        }

        // lock the object...if this fails, it means that the object was
        // already destroyed, so we're done
        if (! acquireLock(objId, txnState))
            return;

        // put the identifier in the deleted set
        txnState.deletedObjects.add(objId);
    }

    /**
     *
     */
    class TxnState {
        // true if this state has been prepared, false otherwise
        public boolean prepared = false;

        // the transaction and its contention handler, which are cached here
        // just as an optimization
        public Transaction txn = null;
        public ContentionHandler contentionHandler = null;

        // the set of objects that have been created in a given transaction
        public HashMap<Long,ManagedObject> createdObjects =
            new HashMap<Long,ManagedObject>();

        // the set of objects that have been locked in a given transaction
        public HashMap<Long,ManagedObject> lockedObjects =
            new HashMap<Long,ManagedObject>();

        // the set of objects that have been peeked in a given transaction
        public HashMap<Long,ManagedObject> peekedObjects =
            new HashMap<Long,ManagedObject>();

        // the set of objects that were deleted in a given transaction
        public HashSet<Long> deletedObjects = new HashSet<Long>();

        // the object names created during a given transaction
        public HashMap<String,Long> createdNamespaceMap =
            new HashMap<String,Long>();

        // the object names deleted during a given transaction
        public HashSet<String> deletedNamespaceSet = new HashSet<String>();
    }

    /**
     *
     */
    class LockState {
        // the actual lock
        public AtomicBoolean lock = new AtomicBoolean(false);

        // the holder of the lock
        public TxnState holder = null;
    }

}
