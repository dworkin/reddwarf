/**
 *
 * <p>Title: TSOTransaction.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceTransactionImpl;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * 
 * <p>
 * Title: TSOTransaction.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSOTransaction implements Transaction {

	private TSOObjectStore ostore;

	private long appID;

	private SGSUUID transactionID;

	private long time;

	private long tiebreaker;

	private ClassLoader loader;

	private DataSpaceTransaction mainTrans, keyTrans;

	private DataSpace mainDataSpace, backupDataSpace;

	private Map<Long, Serializable> lockedObjectsMap = new HashMap<Long, Serializable>();

	private List<Long> newObjectIDs = new ArrayList<Long>();

	private boolean timestampInterrupted;

	// static init

	TSOTransaction(TSOObjectStore ostore, long appID, ClassLoader loader,
			long time, long tiebreaker, DataSpace mainDataSpace,
			DataSpace backupDataSpace) {

		this.ostore = ostore;
		this.appID = appID;
		this.loader = loader;
		transactionID = new StatisticalUUID();
		this.time = time;
		this.tiebreaker = tiebreaker;
		this.mainDataSpace = mainDataSpace;
		this.backupDataSpace = backupDataSpace;
	}

	public SGSUUID getUUID() {
		return transactionID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#start()
	 */
	public void start() {
		mainTrans = new DataSpaceTransactionImpl(appID, loader, mainDataSpace,
				backupDataSpace);
		keyTrans = new DataSpaceTransactionImpl(appID, loader, mainDataSpace,
				backupDataSpace);
		timestampInterrupted = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#create(java.io.Serializable,
	 *      java.lang.String)
	 */
	public long create(Serializable object, String name) {
		long id = mainTrans.create(object);
		long headerID = mainTrans.create(new TSODataHeader(time, tiebreaker,
				transactionID, id));
		if (name != null) {
			mainTrans.registerName(name, headerID);
		}
		lockedObjectsMap.put(headerID, object);
		newObjectIDs.add(headerID);
		return headerID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#destroy(long)
	 */
	public void destroy(long objectID) throws DeadlockException,
			NonExistantObjectIDException {
		TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
		mainTrans.destroy(hdr.objectID); // destroy object
		mainTrans.destroy(objectID); // destroy header
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#peek(long)
	 */
	public Serializable peek(long objectID) throws NonExistantObjectIDException {
		TSODataHeader hdr = (TSODataHeader) mainTrans.read(objectID);
		return mainTrans.read(hdr.objectID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#lock(long, boolean)
	 */
	public Serializable lock(long objectID, boolean block)
			throws DeadlockException, NonExistantObjectIDException {
		Serializable obj = lockedObjectsMap.get(objectID);
		if (obj != null) { // already locked
			return obj;
		}
		keyTrans.lock(objectID);
		TSODataHeader hdr = (TSODataHeader) keyTrans.read(objectID);
		while (!hdr.free) {
			if (timestampInterrupted) {
				keyTrans.abort();
				abort();
				throw new TimestampInterruptException();
			} else if (!block) {
				keyTrans.abort();
				return null;
			}
			if ((time < hdr.time)
					|| ((time == hdr.time) && (tiebreaker < hdr.tiebreaker))) {
				ostore.requestTimestampInterrupt(hdr.uuid);
			}
			// System.out.println("Waiting for wakeup "+transactionID);
			if (!hdr.availabilityListeners.contains(transactionID)) {
				hdr.availabilityListeners.add(transactionID);
				keyTrans.write(objectID, hdr);
				keyTrans.commit();
			} else {
				keyTrans.abort();
			}
			waitForWakeup();
			// System.out.println("wokeup "+transactionID);
			keyTrans.lock(objectID);
			hdr = (TSODataHeader) keyTrans.read(objectID);
		}
		hdr.free = false;
		hdr.time = time;
		hdr.tiebreaker = tiebreaker;
		hdr.availabilityListeners.remove(transactionID);
		hdr.uuid = transactionID;
		keyTrans.write(objectID, hdr);
		keyTrans.commit();
		obj = mainTrans.read(hdr.objectID);
		lockedObjectsMap.put(objectID, obj);
		return obj;
	}

	/**
	 * 
	 */
	private void waitForWakeup() {
		synchronized (this) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#lock(long)
	 */
	public Serializable lock(long objectID) throws DeadlockException,
			NonExistantObjectIDException {
		return lock(objectID, true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#lookup(java.lang.String)
	 */
	public long lookup(String name) {
		return mainTrans.lookupName(name);
	}

	private void freeLockedObjects() {
		List<SGSUUID> listeners = new ArrayList<SGSUUID>();
		for (Long l : lockedObjectsMap.keySet()) {
			TSODataHeader hdr;
			if (!newObjectIDs.contains(l)) {
				try {
					keyTrans.lock(l);
					hdr = (TSODataHeader) keyTrans.read(l);
					hdr.free = true;
					keyTrans.write(l, hdr);
					listeners.addAll(hdr.availabilityListeners);
				} catch (NonExistantObjectIDException e) {
					e.printStackTrace();
				}
			}
		}	
		keyTrans.commit();
		newObjectIDs.clear();
		ostore.notifyAvailabilityListeners(listeners);

	} /*
		 * (non-Javadoc)
		 * 
		 * @see com.sun.gi.objectstore.Transaction#abort()
		 */

	public void abort() {
		mainTrans.abort();
		freeLockedObjects();
		lockedObjectsMap.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#commit()
	 */
	public void commit() {
		mainTrans.commit();
		freeLockedObjects();
		lockedObjectsMap.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#getCurrentAppID()
	 */
	public long getCurrentAppID() {
		return appID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#clear()
	 */
	public void clear() {
		ostore.clear(appID);
	}

	/**
	 * 
	 */
	public void timeStampInterrupt() {
		timestampInterrupted = true;
		synchronized (this) {
			this.notifyAll();
		}

	}

}
