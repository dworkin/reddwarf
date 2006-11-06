package com.sun.sgs.test.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class DummyManagedObject implements ManagedObject, Serializable {
    private static long serialVersionUID = 1;
    private static AtomicInteger nextId = new AtomicInteger(1);
    private final int id = nextId.getAndIncrement();
    private transient final DataManager dataManager;
    public Object value = null;
    private ManagedReference<DummyManagedObject> next = null;

    public DummyManagedObject(DataManager dataManager) {
	this.dataManager = dataManager;
    }

    public void setValue(Object value) {
	dataManager.markForUpdate(this);
	this.value = value;
    }

    public DummyManagedObject getNext() {
	if (next == null) {
	    return null;
	} else {
	    return next.get();
	}
    }

    public DummyManagedObject getNextForUpdate() {
	if (next == null) {
	    return null;
	} else {
	    return next.getForUpdate();
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
	return "DummyManagedObject[id:" + id +
	    (value != null ? ", value:" + value : "") +
	    ", next:" + next + "]";
    }
}
