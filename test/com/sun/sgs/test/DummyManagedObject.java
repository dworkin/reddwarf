package com.sun.sgs.test;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;

public class DummyManagedObject implements ManagedObject, Serializable {
    private static Object lock = new Object();
    private static long serialVersionUID = 1;
    private static int nextId = 1;
    private final int id;
    private transient final DataManager dataManager;
    private final String name;
    private ManagedReference<DummyManagedObject> next;

    public DummyManagedObject(DataManager dataManager, String name) {
	this(dataManager, name, null);
    }

    public DummyManagedObject(DataManager dataManager,
			      String name,
			      DummyManagedObject next)
    {
	this.dataManager = dataManager;
	synchronized (lock) {
	    id = nextId++;
	}
	this.name = name;
	this.next = (next == null) ? null : dataManager.createReference(next);
    }

    public String getName() {
	return name;
    }

    public DummyManagedObject getNext() {
	if (next == null) {
	    return null;
	} else {
	    return next.get();
	}
    }

    public void setNext(DummyManagedObject next) {
	dataManager.markForUpdate(this);
	this.next = dataManager.createReference(next);
    }

    public boolean equals(Object object) {
	return object instanceof DummyManagedObject &&
	    id == ((DummyManagedObject) object).id;
    }

    public int hashCode() {
	return id;

    }

    public String toString() {
	return "DummyManagedObject[id:" + id + ", name:" + name +
	    ", next:" + next + "]";
    }
}
