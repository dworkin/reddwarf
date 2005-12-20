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

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.Transaction;
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
	class DataHeader implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 5666201439015976463L;
		long time;
		long tiebreaker;
		SGSUUID uuid;
		Serializable dataObject;
		boolean free;
		
		public DataHeader(long time, long tiebreaker,SGSUUID uuid, Serializable obj){
			this.time =time;
			this.tiebreaker = tiebreaker;
			this.uuid = uuid;
			this.dataObject = obj;
			free = true;
		}
		
		public boolean before(DataHeader other){
			if (time<other.time){
				return true;
			} else if ((time==other.time)&&(tiebreaker<other.tiebreaker)){
				
			}
			return false;			
		}
	}
	
	
	TSOObjectStore objectStore;
	long appID;
	ClassLoader loader;
	long time;
	long tiebreaker;
	SGSUUID uuid;
	DataSpaceTransaction dsTrans;
	boolean active = false;
	private boolean timeStampInterrupted;
	/**
	 * @param store
	 * @param dataSpace
	 * @param appID
	 * @param loader
	 * @param l
	 * @param m
	 */
	public TSOTransaction(TSOObjectStore store, long appID, ClassLoader loader, long time, 
			long tiebreaker) {
		this.objectStore = store;
		this.appID = appID;
		this.loader = loader;
		this.time = time;
		this.tiebreaker = tiebreaker;
		uuid = new StatisticalUUID();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#start()
	 */
	public void start() {
		dsTrans = objectStore.getDataSpaceTransaction(appID,loader);
		active = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#create(java.io.Serializable,
	 *      java.lang.String)
	 */
	public long create(Serializable object, String name) {
		long tsID = dsTrans.create(new DataHeader(time,tiebreaker,uuid,object));
		if (name!=null){
			dsTrans.registerName(name,tsID);
		}
		return tsID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#destroy(long)
	 */
	public void destroy(long objectID) throws DeadlockException, NonExistantObjectIDException {
		lock(objectID);		
		dsTrans.destroy(objectID);		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#peek(long)
	 */
	public Serializable peek(long objectID) {		
		DataHeader dh = (DataHeader) dsTrans.read(objectID);
		return dh.dataObject;
	}
	
	public Serializable lock(long objectID) 
		throws DeadlockException, NonExistantObjectIDException {
		return lock(objectID,true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#lock(long)
	 */
	public Serializable lock(long objectID, boolean blocking) throws DeadlockException, NonExistantObjectIDException {
		dsTrans.lock(objectID);
		DataHeader dh = (DataHeader) dsTrans.read(objectID);
		if (dh==null){
			throw new NonExistantObjectIDException();
		}
		while(!dh.free){// data object locked by someone else
			if ((time<dh.time)||
				((time == dh.time)&&(tiebreaker<dh.tiebreaker))){
				// we're older so interrupt the holder
				doTimeStampInterrupt(dh);
			} 
			dsTrans.release(objectID); // get out of the way
			if (!blocking){
				return null;
			}
			waitForFreedSignal(); // will return when free or throw
									// DeadlockException
			dsTrans.lock(objectID); // get header again
			dh = (DataHeader) dsTrans.read(objectID);	
		}
		// its ours
		dh.time = time;
		dh.tiebreaker = tiebreaker;
		dh.uuid = uuid;
		dsTrans.write(objectID,dh);
		dsTrans.release(objectID);
		return dh.dataObject;
	}

	/**
	 * 
	 */
	private void waitForFreedSignal() throws DeadlockException{
		if (timeStampInterrupted){
			throw new TimestampInterruptException();
		}
		synchronized(uuid){
			try {
				uuid.wait();
			} catch (InterruptedException e) {				
				e.printStackTrace();
			}
		}		
	}

	/**
	 * @param dh
	 */
	private void doTimeStampInterrupt(DataHeader dh) {
		objectStore.doTimeStampInterrupt(dh.uuid);		
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#lookup(java.lang.String)
	 */
	public long lookup(String name){		
		return dsTrans.lookupName(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#abort()
	 */
	public void abort() {		
		active=false;
		dsTrans.abort();
		objectStore.returnDataSpaceTransaction(dsTrans);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#commit()
	 */
	public void commit() {		
		active = false;
		dsTrans.commit();
		objectStore.returnDataSpaceTransaction(dsTrans);
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#getCurrentAppID()
	 */
	public long getCurrentAppID() {
		// TODO Auto-generated method stub
		return appID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.Transaction#clear()
	 */
	public void clear() {
		dsTrans.clear();
		
	}

	/**
	 * 
	 */
	public void timeStampInterrupt() {
		timeStampInterrupted = true;
		synchronized(uuid){
			uuid.notifyAll();
		}
		
	}

	

}
