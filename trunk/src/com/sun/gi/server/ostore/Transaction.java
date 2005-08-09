package com.sun.gi.server.ostore;

import java.io.Serializable;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Transaction {
    long create(Serializable object, String name);
    void destroy(long objectID);
    Serializable peek(long objectID);
    Serializable lock(long objectID) throws TimestampException;
    void write(long objectID, Serializable object);
    long lookup(String name); // proxies to Ostore
    void abort();
    void commit();
}