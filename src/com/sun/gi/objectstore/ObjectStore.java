



package com.sun.gi.objectstore;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

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
  public static final long INVALID_ID = DataSpace.INVALID_ID;
  
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
  
  public void clearAll();
   

 


}
