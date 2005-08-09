package com.sun.gi.server.gle.impl;

import com.sun.gi.server.gle.Task;
import com.sun.gi.server.gle.Kernel;
import com.sun.gi.server.gle.ObjectReference;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class GLETask
    implements Task {
  ObjectReference objRef;
  String startMethodName;
  Method startMethod = null;
  Object[] startParams;
  Kernel kernel;
  long timestamp;

  public GLETask(Kernel k, long timeStamp, ObjectReference oref, String methodName,
                 Object[] params) {
    objRef = oref;
    kernel = k;
    startMethodName = methodName;
    if (params == null) {
      params = new Object[0];
    }
    startParams = new Object[params.length + 1];
    startParams[0] = this;
    System.arraycopy(params, 0, startParams, 1, params.length);
    timestamp = timeStamp;
  }

  public void run() {
    Object startObject = objRef.get(this);
    if (startMethod == null) { // need to resolve method
      Class[] clarray = new Class[startParams.length];
      for (int i = 0; i < startParams.length; i++) {
        clarray[i] = startParams[i].getClass();
      }
      try {
        startMethod = startObject.getClass().getMethod(startMethodName,
            clarray);
      }
      catch (SecurityException ex) {
        ex.printStackTrace();
      }
      catch (NoSuchMethodException ex) {
        ex.printStackTrace();
      }
    }
    try {
      startMethod.invoke(startObject, startParams);
    }
    catch (InvocationTargetException ex1) {
      ex1.printStackTrace();
    }
    catch (IllegalArgumentException ex1) {
      ex1.printStackTrace();
    }
    catch (IllegalAccessException ex1) {
      ex1.printStackTrace();
    }
  }

  public ObjectReference newSimObject(Serializable object) {
    return null;
  }

  public Serializable getSimObject(long objectID) {
    return null;
  }

  public Serializable peekSimObject(long objectID) {
    return null;
  }

  public void deleteSimObject(long objectID) {
  }

  public void queueTask(Task task) {
    kernel.queueTask(task);
  }

  public long getTimestamp() {
    return timestamp;
  }
}