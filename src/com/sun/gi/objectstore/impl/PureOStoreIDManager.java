package com.sun.gi.objectstore.impl;

import com.sun.gi.objectstore.OStoreMetaData;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PureOStoreIDManager implements ObjectIDManager {
  ObjectStore ostore;

  public PureOStoreIDManager(ObjectStore ostore) {
    this.ostore = ostore;
 }

  public synchronized long getNextID() {
    Transaction trans = ostore.newTransaction(0,null);
    OStoreMetaData md = ostore.lockMetaData(trans);
    long value = md.getNextObjectID();
    //System.out.println("("+md+")ID = "+value);
    md.setNextObjectID(value+1);
    trans.commit();
    return value;
  }

}
