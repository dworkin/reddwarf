



package com.sun.gi.objectstore;

/**
 * <p>Title: ObjectStore.java</p>
 * <p>Description: This interface defines a Darkstar ObjectStore</p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface ObjectStore {
	/**
	 * This constant is used as an error return by Transaction.lookupObject
	 * @see Transaction
	 */
  public static final long INVALID_ID = Long.MIN_VALUE;
  
  /**
   * <p>All interractions with data stored in the ObejctStore must ocurr under a transactional context.
   * This method returns a Transaction object that implements that context.</p>
   * <p>Every app has a seperate space in the ObjectStore that is segregated by AppID. AppIDs are assigned 
   * to apps when they are installed into the Darkstar back-end.  
   * (The true primary key of any Object stored in the Objectstore is a composite of the AppID and 
   * its ObjectID.)</p>
   * <p>Every app also has its own class loader for loading the classes as required by the 
   * deserialization during fetch from the ObjectStore</p>
   * 
   * @param appID The ID of the app requesting a Transaction.  
   * @param loader The App's class loader
   * @return A Transaction that is ready for use.
   * @see Transaction
   */
  public Transaction newTransaction(long appID, ClassLoader loader);

  /**
   * <p>A utility routine for wiping the object store.  This resets it to a fresh just-installed state.</p>
   * <p><b>NOte that currently it wipes the entire ObjectStore. It is likley that we will
   * have to add an entry to just wipe a single app's objects.</b></p>
   */
  public void clear();

  /**
   * <p>This method returns the amount of time this object store will wait for a GET lock
   * on an object before over-riding the existing timestamp record as moribund.  
   * This is a necssary part of the time-stamp ordered deadlock detection scheme used here.</p>
   * <p><b>NOTE: This method was added by Calvin to support his caching layer ontop of the 
   * Derby ObejctStore.  It probably should be made package private or part of the implementation
   * definitionas it doesn't really belong in a user interface.</p></b>
   * @return The wait time before a timeout occurs on a GET
   */
  public long getTimestampTimeout();

  /**
   * This method returns the next available objectID for the creation of a new GLO.
   * It is used by the Transaction implementations and the cache and probably again should not
   * be public.
   * @return The next available object ID.
   */
  
  public long getObjectID();
  
  /** 
   * This method returns the objectstore meta-data as a non-exclusive non-repetable (PEEKed) GLO.
   * Again this is used by Transaction implementations and the cache implementaion
   * and probably should not be part of the public interface,
   * 
   * @param trans A transactional context to fetch the meta data under. 
   * @return A GLO reprsenting the meta-data.
   * @see OStoreMetaData
   */
  public OStoreMetaData peekMetaData(Transaction trans);

  /** 
   * This method returns the objectstore meta-data as a write locked (GETed) GLO.
   * Again this is used by Transaction implementations and the cache implementaion
   * and probably should not be part of the public interface,
   * 
   * @param trans A transactional context to fetch the meta data under. 
   * @return A GLO reprsenting the meta-data.
   * @see OStoreMetaData
   */
  public OStoreMetaData lockMetaData(Transaction trans);

  // DJE
  public void returnTransaction(Transaction trans);
  public void tstampChanged(long objectID);
  public void waitForTstampChange(long objectID);
  public void setObjectHolder(long objectID, Transaction trans);
  public void tstampInterrupt(long objectID);

}
