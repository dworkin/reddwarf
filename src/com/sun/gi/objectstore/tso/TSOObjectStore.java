/**
 *
 * <p>Title: TSOObjectStore.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.DataSpaceTransactionImpl;
import com.sun.gi.utils.SGSUUID;

/**
 *
 * <p>Title: TSOObjectStore.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TSOObjectStore implements ObjectStore {

	DataSpace dataSpace;
	SecureRandom random;
	Map<SGSUUID,TSOTransaction> localTransactionIDMap = new HashMap<SGSUUID,TSOTransaction>();
	
	public TSOObjectStore(DataSpace space) throws InstantiationException {
		dataSpace = space;
	
		try {
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {			
			e.printStackTrace();
			throw new InstantiationException();
		}
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.ObjectStore#newTransaction(long, java.lang.ClassLoader)
	 */
	public Transaction newTransaction(ClassLoader loader) {		
		if (loader==null){
			loader = this.getClass().getClassLoader();
		}
		TSOTransaction trans = new TSOTransaction(this,loader,System.currentTimeMillis(),
				random.nextLong(),dataSpace);	
		return trans;
	}
	
	/**
	 * @param appID
	 * @param loader
	 * @return
	 */
	DataSpaceTransaction getDataSpaceTransaction(ClassLoader loader) {		
		return new DataSpaceTransactionImpl(loader,dataSpace);
	}
	/**
	 * @param dsTrans
	 */
	void returnDataSpaceTransaction(DataSpaceTransaction dsTrans) {
		((DataSpaceTransactionImpl)dsTrans).close();		
	}
	
	/**
	 * @param transaction 
	 * @param uuid2
	 */
	public void requestCompletionSignal(TSOTransaction transaction, SGSUUID uuid2) {
		// TODO Auto-generated method stub
		
	}
	/**
	 * @param uuid
	 */
	public void requestTimestampInterrupt(SGSUUID uuid) {
		TSOTransaction trans = localTransactionIDMap.get(uuid);
		if (trans!=null){
			trans.timeStampInterrupt();
		}
		
	}
	/**
	 * @param appID
	 */
	public void clear() {
		dataSpace.clear();
		
	}
	/**
	 * @param listeners
	 */
	public void notifyAvailabilityListeners(List<SGSUUID> listeners) {
		for(SGSUUID uuid : listeners){
			TSOTransaction trans = localTransactionIDMap.get(uuid);
			if (trans!=null) {
				//System.out.println("Notfying "+uuid);
;				synchronized(trans){
					trans.notifyAll();
				}
			}
		}
		
	}
	/**
	 * @return
	 */
	public long getAppID() {		
		return dataSpace.getAppID();
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.ObjectStore#close()
	 */
	public void close() {
		dataSpace.close();
		
	}
	/**
	 * @param transaction
	 * @param transactionID
	 */
	public void registerActiveTransaction(TSOTransaction trans) {
		localTransactionIDMap.put(trans.getUUID(),trans);		
	}

	
	/**
	 * @param transaction
	 */
	public void deregisterActiveTransaction(TSOTransaction trans) {
		localTransactionIDMap.remove(trans.getUUID());
		
	}
}