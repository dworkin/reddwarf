package com.sun.gi.objectstore.impl;

import java.net.*;
import java.io.*;
import java.util.*;
import com.sun.gi.utils.SharedDataManager;
import com.sun.gi.utils.SharedData;
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

public class SharedDataObjectIDManager
    implements ObjectIDManager {

  private SharedDataManager mgr;
  private SharedData ID;
  private ObjectStore ostore;
  private long appID;

  public SharedDataObjectIDManager(long appID,SharedDataManager mgr, ObjectStore ostore) {
    this.mgr = mgr;
    this.ostore = ostore;
    this.appID = appID;
    ID = mgr.getSharedData("TTOSObjectID_"+appID);
    ID.lock();
    if (ID.getValue() == null) { // first one this run
      Transaction mdtrans = ostore.newTransaction(appID,null);
      OStoreMetaData metaData = ostore.peekMetaData(mdtrans);
      ID.setValue(new Long(metaData.getNextObjectID()));
      mdtrans.commit();
    }
    ID.release();
  }

  public long getNextID() {
    ID.lock();
    long value = ((Long)ID.getValue()).longValue();
    ID.setValue(new Long(value+1));
    Transaction mdtrans = ostore.newTransaction(appID,null);
    OStoreMetaData metaData = ostore.lockMetaData(mdtrans);
    metaData.setNextObjectID(value+1);
    ID.release();
    mdtrans.commit();
    return value;
  }

}
