package com.sun.gi.server.gle.impl;

import com.sun.gi.server.gle.ObjectReference;
import java.io.Serializable;
import com.sun.gi.server.gle.Task;
import java.lang.ref.WeakReference;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class GLEObjectReference
    implements ObjectReference {
  long objectID = -1;
  WeakReference obj = null;

  public GLEObjectReference(long oid) {
    objectID = oid;
  }

  public Serializable get(Task task) {
    if ( (obj == null) || (obj.get() == null)) {
      obj = new WeakReference(task.getSimObject(objectID));
    }
    return (Serializable) obj.get();
  }

  public Serializable peek(Task task) {
    if ( (obj == null) || (obj.get() == null)) {
      obj = new WeakReference(task.peekSimObject(objectID));
    }
    return (Serializable) obj.get();
  }

  public void set(ObjectReference ref) {
    objectID = ( (GLEObjectReference) ref).objectID;
    obj = null;
  }

}