package com.sun.gi.objectstore.test;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.impl.TTObjectStore;
import com.sun.gi.objectstore.Transaction;
import java.io.Serializable;
import com.sun.gi.objectstore.DeadlockException;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

class DataObject
    implements Serializable {
  int i;
  double d;
  String s;

  public DataObject(int i, double d, String s) {
    this.d = d;
    this.s = s;
    this.i = i;
  }

  public String toString() {
    return "Iint = " + i + ", double = " + d + ", String= " + s;
  }
}

public class OstoreTest {
  private static boolean TESTISOLATION = false;
  public OstoreTest() {
  }

  public static void main(String[] args) {
    ObjectStore ostore = new TTObjectStore(10,20);
    System.out.println("Clearing object store");
    ostore.clear();
    System.out.println("Assigning transactions.");
    Transaction t1 = ostore.newTransaction(1,null);
    Transaction t2 = ostore.newTransaction(1,null);
    System.out.println("Creating test object 1.");
    DataObject obj = new DataObject(55, 3.14, "This is a test!");
    final long objid = t1.create(obj, "Test_Data");
    System.out.println("Creating test object 2.");
    obj = new DataObject(100, 1.5, "This is test 2!");
    final long objid2 = t1.create(obj, "Test_Data2");
    obj = new DataObject(500, 22.52, "This is test 3!");
    final long objid3 = t1.create(obj, "Test_Data3");
    System.out.println("Getting object from inside transaction...");
    obj = (DataObject) t1.lock(objid);
    if (obj == null) {
      System.out.println(
          "ERROR: failed to lock object from creation context.");
    }
    else {
      System.out.println("Success: Object: " + obj.toString());
    }
    if (TESTISOLATION) {
      System.out.println("Getting object from OUTSIDE transaction...");
      obj = (DataObject) t2.lock(objid);
      if (obj == null) {
        System.out.println(
            "Success: failed to lock object from outsiden context.");
      }
      else {
        System.out.println("ERROR: returned object.  Object: " + obj.toString());
      }
    }
    System.out.println("Testing lock of a non existant object.");
    obj = (DataObject) t2.lock(5);
    if (obj == null) {
      System.out.println(
          "Success: failed to lock non-existant object.");
    }
    else {
      System.out.println("ERROR: returned object.  Object: " + obj.toString());
    }
    System.out.println("COmitting object");
    t1.commit();
    System.out.println("Attempting to retrieve object from other transaction.");
    obj = (DataObject) t2.lock(objid);
    if (obj != null) {
      System.out.println(
          "Success: Object " + obj.toString());
    }
    else {
      System.out.println("ERROR: failed to find object");
    }
    System.out.println("Looking up ID by name.");
    long lid = t2.lookup("Test_Data");
    if (lid == objid) {
      System.out.println("Success: found id");
    }
    else {
      System.out.println("ERROR: id = " + objid + " but found " + lid);
    }
    System.out.println("Looking up INVALID name.");
    lid = t2.lookup("NOTANOBJECT");
    if (lid == ObjectStore.INVALID_ID) {
      System.out.println("Success: found INVALID_ID");
    }
    else {
      System.out.println("ERROR: wanted INVALID_ID = " + ObjectStore.INVALID_ID
                         + " but found " + lid);
    }
    t2.commit();
    System.out.println("*****  TSO tests ***");
    final Transaction firstTrans = ostore.newTransaction(1,null);
    try {
      Thread.sleep(1000);
    } catch (Exception e){
      e.printStackTrace();
    }
    final Transaction secondTrans = ostore.newTransaction(1,null);
    System.out.println("First Trans = "+ firstTrans);
    System.out.println("Second Trans = "+ secondTrans);
    System.out.println("Timestamp Interrupt test");
    Thread lockThread = new Thread() {
      public void run() {
        try {
          System.out.println("In later trans thread, acquiring first lock.");
          secondTrans.lock(objid);
          System.out.println("acquired first lock.");
          Thread.sleep(3000);
          System.out.println("Acquiring second lock in later..  should abort me.");
          secondTrans.lock(objid2);
          System.out.println("ERROR: Later timstamp got both locks!");
        }
        catch (DeadlockException dle) {
          System.out.println("Later Transaction successfully interrupted");
          secondTrans.abort();
        } catch (Exception e){
          System.out.println("ERROR: Unexpected exception in Later Transaction .");
          e.printStackTrace();
        }
      }
    };
    lockThread.start();
    try {
      Thread.sleep(1000);
      System.out.println("Acquiring lock in earlier transaction.");;
      firstTrans.lock(objid);
      System.out.println("Earlier transaction acquired lock.");
      Thread.sleep(1000);
    } catch (DeadlockException dle) {
      System.out.println("ERROR: Earlier transaction Aborted.");
    } catch (Exception e) {
      System.out.println("ERROR: Unexpected Exception in earlier transaction.");
    }
    firstTrans.commit();
    System.out.println("Earlier trasnaction completed.");
    System.out.println("*** Timestamp Timeout Test ***");
    System.out.println("Non blocked test..... waiting for timeout");
    t1 = ostore.newTransaction(1,null);
    t2 = ostore.newTransaction(1,null);
    Transaction t3 = ostore.newTransaction(1,null);
    t1.lock(objid);
    try {
      Thread.sleep(ostore.getTimestampTimeout()+500); // sleep half a sec longer then timeout
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("Attempting to lock objet that shoudl have timed out...");
    t2.lock(objid);
    System.out.println("Success: acquired after timeout.");
    System.out.println("Blocked test.");
    if (t3.lock(objid)==null){
      System.out.println("ERROR: Object does nto exist (lock returned null.)");
    }
    System.out.println("Success: Woke up from block after timeout.");
    System.out.println("End of tests");
  }
}
