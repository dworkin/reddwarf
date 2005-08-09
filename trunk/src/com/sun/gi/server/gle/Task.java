package com.sun.gi.server.gle;

import java.io.Serializable;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface Task extends Runnable {
    public ObjectReference newSimObject(Serializable object);
    public Serializable getSimObject(long objectID);
    public Serializable peekSimObject(long objectID);
    public void deleteSimObject(long objectID);
    public void queueTask(Task task);
    public long getTimestamp();
}