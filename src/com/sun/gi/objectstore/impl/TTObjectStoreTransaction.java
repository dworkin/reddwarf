package com.sun.gi.objectstore.impl;

import com.sun.gi.objectstore.Transaction;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.*;
import java.util.Map;
import java.util.HashMap;
import com.sun.gi.objectstore.DeadlockException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import com.sun.gi.objectstore.TransactionClosedException;
import java.util.Map.Entry;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class TTObjectStoreTransaction
    implements Transaction {
  private static final boolean TTWORKAROUND = false;
  TTObjectStore ostore;
  Timestamp timestamp;
  long tiebreaker;
  Connection dataConn;
  Connection tstampConn;
  PreparedStatement insertStmnt;
  PreparedStatement updateStmnt;
  PreparedStatement insertNameStmnt;
  PreparedStatement insertTstampStmnt;
  PreparedStatement updateTstampStmnt;
  PreparedStatement destroyStmnt;
  PreparedStatement destroyNameStmnt;
  PreparedStatement destroyTstampStmnt;
  PreparedStatement readNameStmnt;
  PreparedStatement peekStmnt;
  PreparedStatement lockTstampStmnt;
  PreparedStatement writeTstampStmnt;
  PreparedStatement testTstampStmnt;

  private Map lockedObjectCache = new HashMap();
  private boolean timestampInterrupted = false;
  private Map peekedObjectCache = new HashMap();
  private List inuseList = new ArrayList();
  private Map nameCache = new HashMap();
  private Map idCache = new HashMap();
  private int timesUsed = 0;
  private boolean closed = false;

  private static final boolean DEBUGTSTAMP = false;
  private static final boolean DEBUGLOCK = false;
  private long appID = -1;
  private ClassLoader currentClassLoader = null;
  public TTObjectStoreTransaction(TTObjectStore ostore) {

    this.ostore = ostore;
    this.timestamp = timestamp;
    this.tiebreaker = tiebreaker;
    dataConn = ostore.getDataConnection();
    tstampConn = ostore.getTstampConnection();
    if (dataConn == tstampConn) {
      System.out.println(
          "Error! Did nto get unqiue connections for data and tstamp!");
      System.exit(1);
    }
    try {
      insertStmnt = dataConn.prepareStatement("INSERT INTO " +
                                              TTObjectStore.OBJTBLNAME +
                                              " VALUES(?,?,?);");
      updateStmnt = dataConn.prepareStatement(
          "UPDATE " + TTObjectStore.OBJTBLNAME + " SET " +
          "Object = ? WHERE APPID = ? AND ID = ?;");
      insertNameStmnt = dataConn.prepareStatement(
          "INSERT INTO " + TTObjectStore.NAMETBLNAME + "  VALUES (?,?,?);");
      insertTstampStmnt = dataConn.prepareStatement(
          "INSERT INTO " + TTObjectStore.TSTAMPTBLNAME +
          " VALUES (?,?,?,?,?,?);");
      destroyStmnt = dataConn.prepareStatement(
          "DELETE FROM " + TTObjectStore.OBJTBLNAME +
          " WHERE APPID = ? AND ID = ? ;");
      destroyNameStmnt = dataConn.prepareStatement(
          "DELETE FROM " + TTObjectStore.NAMETBLNAME +
          " WHERE APPID = ? AND ID = ?;");
      destroyTstampStmnt = dataConn.prepareStatement(
          "DELETE FROM " + TTObjectStore.TSTAMPTBLNAME +
          " WHERE APPID = ? AND ID  = ?;");
      peekStmnt = dataConn.prepareStatement(
          "SELECT * FROM " + TTObjectStore.OBJTBLNAME + " T " +
          "WHERE T.APPID = ? AND T.ID = ?;");
      lockTstampStmnt = tstampConn.prepareStatement(
          "SELECT * FROM " + TTObjectStore.TSTAMPTBLNAME + " T  " +
          "WHERE " + "T.APPID = ? AND T.ID  = ? FOR UPDATE;");
      testTstampStmnt = tstampConn.prepareStatement(
          "SELECT * FROM " + TTObjectStore.TSTAMPTBLNAME + " T  " +
          "WHERE " + "T.APPID = ? AND T.ID  = ?;");
      updateTstampStmnt = tstampConn.prepareStatement(
          "UPDATE " + TTObjectStore.TSTAMPTBLNAME + " SET " +
          "TaskTstamp = ?, " +
          "Tiebreaker = ?, " +
          "InUse = ?, " +
          "LockTime = ? WHERE APPID = ? AND ID = ? ;");
      readNameStmnt = dataConn.prepareStatement(
          "SELECT * FROM " + TTObjectStore.NAMETBLNAME + " T " +
          "WHERE T.APPID = ? AND T.NAME = ?;");
      writeTstampStmnt = tstampConn.prepareStatement(
          "INSERT INTO " + TTObjectStore.TSTAMPTBLNAME +
          " VALUES (?,?,?,?,?,?);");
    }
    catch (SQLException ex) {
      ex.printStackTrace();
    }
  }

  public void start(long appID, Timestamp tstamp, long tiebreaker,
                    ClassLoader cl) {
    //System.out.println("TIMES USEED: "+timesUsed++);
    this.appID = appID;
    this.timestamp = tstamp;
    this.tiebreaker = tiebreaker;
    this.timestampInterrupted = false;
    closed = false;
    currentClassLoader = cl;
  }

  // serialization routines
  private byte[] objectToBytes(Serializable obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(obj);
    oos.flush();
    byte[] buff = baos.toByteArray();
    oos.close();
    baos.close();
    return buff;
  }

  private Serializable bytesToObject(byte[] buff, ClassLoader cl) throws
      IOException, ClassNotFoundException {
    ByteArrayInputStream bais = new ByteArrayInputStream(buff);
    CLObjectInputStream ois = new CLObjectInputStream(bais, cl);
    Serializable obj = null;
    obj = (Serializable) ois.readObject();
    ois.close();
    bais.close();
    return obj;
  }

  // Database manipulation routines
  private void insertObject(long oid, Serializable object) throws SQLException,
      IOException {
    insertStmnt.setLong(1, appID);
    insertStmnt.setLong(2, oid);
    insertStmnt.setBytes(3, objectToBytes(object));
    insertStmnt.executeUpdate();
  }

  private void insertTimestamp(long oid, Timestamp timestamp, long tiebreaker,
                               boolean inUse) throws SQLException {
    insertTstampStmnt.setLong(1, appID);
    insertTstampStmnt.setLong(2, oid);
    insertTstampStmnt.setTimestamp(3, timestamp);
    insertTstampStmnt.setLong(4, tiebreaker);
    if (!inUse) {
      insertTstampStmnt.setByte(5, (byte) 0);
    }
    else {
      insertTstampStmnt.setByte(5, (byte) 1);
    }
    insertTstampStmnt.setTimestamp(
        6, new Timestamp(System.currentTimeMillis()));
    insertTstampStmnt.executeUpdate();
  }

  private void insertName(long oid, String name) throws SQLException {
    insertNameStmnt.setLong(1, appID);
    insertNameStmnt.setLong(2, oid);
    insertNameStmnt.setString(3, name);
    insertNameStmnt.executeUpdate();
  }

  private void deleteObject(long objectID) throws SQLException {
    destroyStmnt.setLong(1, appID);
    destroyStmnt.setLong(2, objectID);
    destroyStmnt.executeUpdate();
  }

  private void deleteTimestamp(long objectID) throws SQLException {
    destroyTstampStmnt.setLong(1, appID);
    destroyTstampStmnt.setLong(2, objectID);
    destroyTstampStmnt.executeUpdate();
  }

  private void deleteName(long objectID) throws SQLException {
    destroyNameStmnt.setLong(1, appID);
    destroyNameStmnt.setLong(2, objectID);
    destroyNameStmnt.executeUpdate();
  }

  private Serializable readObject(long objectID, ClassLoader cl) throws
      SQLException, ClassNotFoundException, IOException {
    peekStmnt.setLong(1, appID);
    peekStmnt.setLong(2, objectID);
    ResultSet rs = peekStmnt.executeQuery();
    if (rs.next()) {
      byte[] buff = rs.getBytes(3);
      return bytesToObject(buff, cl);
    }
    else {
      return null;
    }
  }

  private TimestampRecord lockTimestamp(long objectID) throws SQLException {
    ResultSet rs;
    if (TTWORKAROUND) {
      // workaround for isolation bug in TimesTen over SELECT ... FOR UPDATE
      testTstampStmnt.setLong(1, appID);
      testTstampStmnt.setLong(2, objectID);
      rs = testTstampStmnt.executeQuery();
      if (!rs.next()) { // doesn't exist
        return null;
      }
    }
    lockTstampStmnt.setLong(1, appID);
    lockTstampStmnt.setLong(2, objectID);
    rs = lockTstampStmnt.executeQuery();
    if (DEBUGTSTAMP) {
      System.out.println("Timestamp locked.");
    }
    if (!rs.next()) {
      return null; // object not found
    }
    else {
      return new TimestampRecord(rs.getTimestamp(3), rs.getLong(4),
                                 rs.getByte(5), rs.getTimestamp(6));
    }
  }

  private void setTimestamp(long oid, Timestamp timestamp, long tiebreaker,
                            boolean inUse) throws SQLException {
    updateTstampStmnt.setLong(5, appID);
    updateTstampStmnt.setLong(6, oid);
    updateTstampStmnt.setTimestamp(1, timestamp);
    updateTstampStmnt.setLong(2, tiebreaker);
    if (inUse) {
      updateTstampStmnt.setByte(3, (byte) 1);
    }
    else {
      updateTstampStmnt.setByte(3, (byte) 0);
    }
    updateTstampStmnt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
    updateTstampStmnt.executeUpdate();
  }

  private void releaseTimestamp(long objectID) throws SQLException {
    tstampConn.commit();
    if (DEBUGTSTAMP) {
      System.out.println("Timestamp unlocked.");
    }
    ostore.tstampChanged(objectID);
  }

  private void resetTimestamps() {
    for (Iterator i = inuseList.iterator(); i.hasNext(); ) {
      Long objid = (Long) i.next();
      try {
        TimestampRecord tsr = lockTimestamp(objid.longValue());
        setTimestamp(objid.longValue(), tsr.timestamp,
                     tsr.tiebreaker, false);
        releaseTimestamp(objid.longValue());
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

  }

  private void checkClosed() {
    if (closed) { // error, thorw exception
      throw new TransactionClosedException();
    }
  }

  // public functions
  public long create(Serializable object, String name) {
    checkClosed();
    long oid = ostore.getObjectID();
    if (create(oid, object, name)) {
      return oid;
    }
    else {
      return ostore.INVALID_ID;
    }
  }

  public boolean create(long id, Serializable object, String name) {
    Long oid = new Long(id);
    try {
      insertObject(oid.longValue(), object);
      // this is false ebcause it wont get wrriten til the end of the task
      insertTimestamp(oid.longValue(), timestamp, tiebreaker, false);
      lockedObjectCache.put(oid, object);
      if (name != null) {
        nameCache.put(name, oid);
        insertName(oid.longValue(), name);
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
      lockedObjectCache.remove(oid);
      if (name != null) {
        nameCache.remove(name);
      }
      return false;
    }
    return true;
  }

  public void destroy(long objectID) {
    checkClosed();
    try {
      deleteObject(objectID);
      deleteTimestamp(objectID);
      deleteName(objectID);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Serializable peek(long objectID) {
    checkClosed();
    Long lid = new Long(objectID);
    Serializable obj = (Serializable) peekedObjectCache.get(lid);
    if (obj != null) {
      return obj;
    }
    try {
      obj = readObject(objectID,currentClassLoader);
      if (obj != null) {
        peekedObjectCache.put(lid, obj);
        idCache.put(obj,lid);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return obj;
  }

  private boolean checkTimestamp(long objectID) {
    if (timestampInterrupted) { // throw interrupt
      throw new DeadlockException("Timestamp Interrupt");
    }
    while (true) {
      try {
        TimestampRecord tsr = lockTimestamp(objectID);
        if (tsr == null) {
          return false;
        }
        if (DEBUGTSTAMP) {
          System.out.println("Tsatmp for objid= " + objectID + ": " +
                             tsr.timestamp + "(" + tsr.tiebreaker + ") " +
                             " InUse=" + tsr.inUse);

        }
        if ( (!tsr.inUse) || // not in use, grab it
            (tsr.lockTime.before(
            new Timestamp(System.currentTimeMillis() -
                          ostore.getTimestampTimeout())))) { // timed out grab it
          setTimestamp(objectID, timestamp, tiebreaker, true);
          inuseList.add(new Long(objectID));
          ostore.setObjectHolder(objectID, this);
          releaseTimestamp(objectID);
          return true;
        }
        else { // held, do we interrupt?
          if (tsr.timestamp.after(timestamp) ||
              (tsr.timestamp.equals(timestamp) && (tsr.tiebreaker > tiebreaker))) {
            ostore.tstampInterrupt(objectID);
          }
          releaseTimestamp(objectID);
          ostore.waitForTstampChange(objectID);
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public Serializable lock(long objectID) {
    checkClosed();
    Long lid = new Long(objectID);
    Serializable obj = (Serializable) lockedObjectCache.get(lid);
    if (obj != null) { // object already locked
      if (DEBUGLOCK) {
        System.out.println("Object found in transaction locked obj cache!");
      }
      return obj;
    }
    if (!checkTimestamp(objectID)) { // object not in objectstore
      return null;
    }
    obj = peek(objectID);
    lockedObjectCache.put(lid, obj);
    idCache.put(obj,lid);
    return obj;
  }

  private void write(long objectID, Serializable object) {
    checkClosed();
    try {
      updateStmnt.setLong(2, appID);
      updateStmnt.setLong(3, objectID);
      updateStmnt.setObject(1, objectToBytes(object));
      updateStmnt.executeUpdate();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public long lookup(String name) {
    checkClosed();
    Long lid = (Long) nameCache.get(name);
    if (lid != null) {
      return lid.longValue();
    }
    try {
      readNameStmnt.setLong(1, appID);
      readNameStmnt.setString(2, name);
      ResultSet rs = readNameStmnt.executeQuery();
      if (rs.next()) {
        return rs.getLong(2);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return Long.MIN_VALUE;
  }

  public long lookupObject(Serializable sobject) {
    checkClosed();
    Long lid = (Long) idCache.get(sobject);
    if (lid != null) {
      return lid.longValue();
    }
    return Long.MIN_VALUE;
  }


  public void abort() {
    checkClosed();
    try {
      resetTimestamps();
      dataConn.rollback();
      tstampConn.rollback();
      lockedObjectCache.clear();
      peekedObjectCache.clear();
      inuseList.clear();
      nameCache.clear();
      closed = true;
      ostore.returnTransaction(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void commit() {
    checkClosed();
    try {
      // write dirty objects
      for(Iterator i = lockedObjectCache.entrySet().iterator();i.hasNext();){
        Entry entry = (Entry)i.next();
        write(((Long)entry.getKey()).longValue(),(Serializable)entry.getValue());
      }
      // shut down task
      resetTimestamps();
      dataConn.commit();
      tstampConn.commit();
      lockedObjectCache.clear();
      peekedObjectCache.clear();
      inuseList.clear();
      nameCache.clear();
      closed = true;
      ostore.returnTransaction(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void tstampInterrupt() {
    timestampInterrupted = true;
    synchronized (this) {
      this.notifyAll();
    }
  }

  public String toString() {
    return "Transaction: " + this.hashCode() + " Timestamp = " +
        timestamp + " Tiebreaker = " + tiebreaker;
  }

  public long getCurrentAppID() {
    return appID;
  }
}
