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

import java.sql.*;
//import com.timesten.sql.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import com.sun.gi.utils.JRMSSharedDataManager;

public class TTObjectStore
    implements ObjectStore {
  public static final String OBJBASETBL = "OBJECTS";
  public static final String OBJTBLNAME = "SIMSERVER."+OBJBASETBL;
  //public static final String DIRTBLNAME = "ObjectDirectory";
  public static final String  NAMEBASETBL= "NAMEDIRECTORY";
  public static final String NAMETBLNAME = "SIMSERVER."+NAMEBASETBL;
  public static final String NAMEIDXBASE = "NAMEDIRINDEX";
  public static final String NAMEIDXNAME = "SIMSERVER."+NAMEIDXBASE;
  public static final String TSTAMPTBLBASE = "TIMESTAMPS";
  public static final String TSTAMPTBLNAME = "SIMSERVER."+TSTAMPTBLBASE;
  public static final String TIMESTAMPBASE = "TIMESTAMPIDINDEX";
  public static final String TSTAMPIDIDX = "SIMSERVER."+TIMESTAMPBASE;
  public static SecureRandom random = null;
  static String CONNSTR = null;
  private String dataConnURL;
  private static boolean arg_reset;
  public static final long TSTAMPTIMEOUT = 10000; // max thread time is 10 seconds
  private ObjectIDManager objectIDManager;
  private Map objectHolder = new HashMap();
  private Map waitObjects = new HashMap();
  private List transactionPool = new LinkedList();
  private int initialPoolSize;
  private int maxPoolSize;

  private static final boolean DEBUGTRANSPOOL = false;
  private int transactionCount;

  public TTObjectStore() {
    this(10,20);
  }

  public TTObjectStore(int initialPoolSize, int maxPoolSize) {
    this.initialPoolSize = initialPoolSize;
    transactionCount = initialPoolSize;
    try {
      random = SecureRandom.getInstance("SHA1PRNG");
    }
    catch (NoSuchAlgorithmException ex) {
      ex.printStackTrace();
    }
    try {
      // ****** Load TimesTen JDBC Driver *********
      Class.forName("com.timesten.jdbc.TimesTenDriver");
      String ttds = System.getProperty("ostore.timesten.datasource");
      if (ttds == null){
        ttds = "objectstore";
      }
      String ttds_type = System.getProperty("ostore.timesten.driver");
      if (ttds_type == null) { // local
        ttds_type = "direct";
      }
      dataConnURL = "jdbc:timesten:"+ttds_type+":"+ttds;
      //objectIDManager = new SharedDataObjectIDManager(
        //new JRMSSharedDataManager(),this);
      checkTables();
      objectIDManager =new PureOStoreIDManager(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initializePool(int transactionPoolSize) {
    if (DEBUGTRANSPOOL){
      System.out.println("Initializing transaction pool to size "+
                         transactionPoolSize);
    }
    synchronized (transactionPool) {
      transactionPool.clear();
      for (int i = 0; i < transactionPoolSize; i++) {
        transactionPool.add(new TTObjectStoreTransaction(this));
      }
    }
    if (DEBUGTRANSPOOL){
      System.out.println("Transaction pool initialized");
      dumpPool();
    }
  }

  public void eraseTables() {
    try {
      Connection conn = getDataConnection();
      DatabaseMetaData md = conn.getMetaData();
      // print tables
      ResultSet rs1 = md.getTables(null, null, null, null);

      ResultSet rs = md.getTables(null, null, OBJBASETBL, null);
      if (!rs.next()) {
        System.out.println("Objects table not found");
      }
      else {
        System.out.println("Deleting Objects table");
        PreparedStatement stmnt = conn.prepareStatement(
            "DROP TABLE " + OBJTBLNAME + ";");
        stmnt.execute();
      }
      rs = md.getTables(null, null, NAMEBASETBL, null);
      if (!rs.next()) {
        System.out.println("Name table not found");
      }
      else {
        System.out.println("Deleting Name table");
        PreparedStatement stmnt = conn.prepareStatement(
            "DROP TABLE " + NAMETBLNAME + ";");
        stmnt.execute();
      }
      rs = md.getTables(null, null, TSTAMPTBLBASE, null);
      if (!rs.next()) {
        System.out.println("Timestamp table not found");
      }
      else {
        System.out.println("Deleting Timestamp table");
        PreparedStatement stmnt = conn.prepareStatement(
            "DROP TABLE " + TSTAMPTBLNAME + ";");
        stmnt.execute();
      }
      conn.commit();
      conn.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void checkTables() {
    try {
      Connection conn = getDataConnection();
      DatabaseMetaData md = conn.getMetaData();
      ResultSet rs = md.getTables(null, null, OBJBASETBL, null);
      if (rs.next()) {
        System.out.println("Found Objects table");
      }
      else {
        System.out.println("Creating Objects table");
        PreparedStatement stmnt = conn.prepareStatement(
            "CREATE TABLE " + OBJTBLNAME + " (" +
            "APPID BIGINT NOT NULL,"+
            "ID BIGINT NOT NULL," +
            "Object VARBINARY(1024),"+
            "PRIMARY KEY (APPID, ID));");
        stmnt.execute();
      }
      rs = md.getTables(null, null, NAMEBASETBL, null);
      if (rs.next()) {
        System.out.println("Found Name table");
      }
      else {
        System.out.println("Creating Name table");
        PreparedStatement stmnt = conn.prepareStatement(
            "CREATE TABLE " + NAMETBLNAME + " (" +
            "APPID BIGINT NOT NULL,"+
            "ID BIGINT NOT NULL," +
            "Name VARCHAR(35),"+
            "PRIMARY KEY (APPID, ID));");
        stmnt.execute();
      }
      rs = md.getTables(null, null, TSTAMPTBLBASE, null);
      if (rs.next()) {
        System.out.println("Found Timestamp table");
      }
      else {
        System.out.println("Creating Timestamp table");
        PreparedStatement stmnt = conn.prepareStatement(
            "CREATE TABLE " + TSTAMPTBLNAME + " (" +
            "APPID BIGINT NOT NULL,"+
            "ID BIGINT NOT NULL," +
            "TaskTstamp TIMESTAMP," +
            "Tiebreaker BIGINT," +
            "InUse SMALLINT,"+
            "LockTime TIMESTAMP,"+
            "PRIMARY KEY (APPID, ID) );");
        stmnt.execute();
      }
      rs = md.getIndexInfo(null, null, NAMETBLNAME, false, false);
      boolean indexFound = false;
      while (rs.next()) {
        String indexName = rs.getString("INDEX_NAME");
        if ( (indexName != null) && (indexName.equalsIgnoreCase(
            TTObjectStore.NAMEIDXBASE))) {
          indexFound = true;
          break;
        }
      }
      if (indexFound) {
        System.out.println("Found Directory Name index");
      }
      else {
        System.out.println("Making NameIndex on ObjectDirectory");
        PreparedStatement stmnt = conn.prepareStatement(
            "CREATE UNIQUE INDEX " + NAMEIDXNAME + " ON " + NAMETBLNAME +
            "(APPID, Name);");
        stmnt.execute();
      }
      rs = md.getIndexInfo(null, null, TSTAMPTBLNAME, false, false);
      indexFound = false;
      while (rs.next()) {
        String indexName = rs.getString("INDEX_NAME");
        if ( (indexName != null) && (indexName.equalsIgnoreCase(
            TSTAMPTBLBASE))) {
          indexFound = true;
          break;
        }
      }
      if (indexFound) {
        System.out.println("Found Directory Name index");
      }
      else {
        System.out.println("Making IDIndex on Timestamp Table");
        PreparedStatement stmnt = conn.prepareStatement(
            "CREATE UNIQUE INDEX " + TSTAMPIDIDX + " ON " + TSTAMPTBLNAME +
            "(APPID,ID);");
        stmnt.execute();
      }
      conn.commit();
      conn.close();
      initializePool(initialPoolSize);
      initializeMetaData(); // must be done last as it uses a Transaction
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Transaction newTransaction(long appID, ClassLoader loader) {
    // parameter is for future when we have speerated apps
    TTObjectStoreTransaction trans = null;
    synchronized (transactionPool) {
      if (transactionPool.size() > 0) {
        trans = (TTObjectStoreTransaction) transactionPool.remove(0);
      }  else if (transactionCount < maxPoolSize){
          trans = new TTObjectStoreTransaction(this);
          transactionCount++;
      } else {
        while (transactionPool.size() == 0) {
          try {
            transactionPool.wait();
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }
        trans = (TTObjectStoreTransaction) transactionPool.remove(0);
      }
    }
    trans.start(appID, new Timestamp(System.currentTimeMillis())
                , random.nextLong(), loader);
    if (DEBUGTRANSPOOL) {
      System.out.println("New Transaction: " + trans.toString());
      dumpPool();
    }
    return trans;
  }

  public void returnTransaction(TTObjectStoreTransaction trans) {
    synchronized (transactionPool) {
      transactionPool.add(trans);
      transactionPool.notify();
    }
    if (DEBUGTRANSPOOL){
       System.out.println("Transaction Returned: " + trans.toString());
      dumpPool();
    }
  }

  public Connection getDataConnection() {
    Connection conn;
    try {
      conn = DriverManager.getConnection(dataConnURL);
      conn.setAutoCommit(false);
      conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      CallableStatement cs = conn.prepareCall("{CALL ttLockLevel('Row')}");
      cs.execute();
      //cs = conn.prepareCall("{CALL ttLockWait(0)}");
      //cs.execute();
      return conn;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static void main(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-reset")) {
        arg_reset = true;
      }
    }
    TTObjectStore os = new TTObjectStore(10,20);
    if (arg_reset) {
      os.reset();
    }
    System.exit(0);
  }

  public long getObjectID() {
    return objectIDManager.getNextID();
  }

  private void reset() {
    eraseTables();
    checkTables();
    initializePool(initialPoolSize);
  }

  public Connection getTstampConnection() {
    return getDataConnection();
  }

  public void tstampInterrupt(long objectID) {
    TTObjectStoreTransaction holder =
        (TTObjectStoreTransaction) objectHolder.get(new Long(objectID));
    holder.tstampInterrupt();
  }

  public void tstampChanged(long objectID) {
    Object obj = waitObjects.get(new Long(objectID));
    if (obj != null) {
      synchronized (obj) {
        obj.notifyAll();
      }
    }
  }

  public void waitForTstampChange(long objectID) {
    Long lid = new Long(objectID);
    synchronized (waitObjects) {
      Object obj = (Object) waitObjects.get(lid);
      if (obj == null) {
        obj = new Object();
        waitObjects.put(lid, obj);
      }
      synchronized (obj) {
        try {
          obj.wait(getTimestampTimeout()); // rteturn after max timestamp timeout
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void setObjectHolder(long objectID,
                              TTObjectStoreTransaction trans) {
    objectHolder.put(new Long(objectID), trans);
  }

  public void clear() {
    reset();
  }

  public long getTimestampTimeout() {
    return TSTAMPTIMEOUT;
  }

  // debug routines
  private void dumpPool(){
    System.out.println("Transaction Pool:");
    synchronized(transactionPool){
      for (Iterator i = transactionPool.iterator(); i.hasNext(); ) {
        System.out.println("   " + i.next().toString());
      }
    }
  }

  public OStoreMetaData peekMetaData(Transaction trans) {
    OStoreMetaData md = (OStoreMetaData)trans.peek(TTOStoreMetaData.METADATAID);
    return md;
  }

  public OStoreMetaData lockMetaData(Transaction trans) {
    OStoreMetaData md = (OStoreMetaData)trans.lock(TTOStoreMetaData.METADATAID);
    return md;
  }

  private void initializeMetaData() {
    TTObjectStoreTransaction  trans = (TTObjectStoreTransaction )newTransaction(
        0,null);
    if (trans.peek(0) == null) { //doesnt exist yet
      OStoreMetaData md = new TTOStoreMetaData();
      trans.create(TTOStoreMetaData.METADATAID, md, "__OStoreMetaData");
      trans.commit();
    } else {
      trans.abort();
    }
  }

/*
  public void writeMetaData(Transaction trans, OStoreMetaData metaData) {
    trans.write(TTOStoreMetaData.METADATAID,metaData);
  }
*/
}
