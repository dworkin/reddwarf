package com.sun.gi.objectstore;

import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Transaction {
    public long create(Serializable object, String name);
    public boolean create(long objectID, Serializable object, String name);
    public void destroy(long objectID);
    public Serializable peek(long objectID);
    public Serializable lock(long objectID) throws DeadlockException;
    public long lookup(String name); // proxies to Ostore
    public void abort();
    public void commit();
    public long getCurrentAppID();

  /**
   * lookupObject
   *
   * @param sobj Serializable
   */
  public long lookupObject(Serializable sobj);
}
