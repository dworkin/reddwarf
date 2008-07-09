package com.sun.sgs.impl.contention;

import com.sun.sgs.contention.LockInfo;
import com.sun.sgs.contention.LockInfo.LockType;

import java.math.BigInteger;

final class LockInfoImpl implements LockInfo {

    protected Object locked;

    protected final BigInteger oid;

    protected final String name;

    private final LockType lockType;

    LockInfoImpl(LockType lockType, BigInteger oid) {
	if (oid == null)
	    throw new NullPointerException("Object id cannot be null");
	this.lockType = lockType;
	this.oid = oid;
	this.name = null;
    }

    LockInfoImpl(LockType lockType, String name) {
	if (name == null) 
	    throw new NullPointerException("Name cannot be null");
	this.lockType = lockType;
	this.name = name;
	this.oid = null;
    }

    public boolean equals(Object o) {
	if (o instanceof LockInfoImpl) {
	    LockInfoImpl other = (LockInfoImpl)o;
	    if (lockType.equals(other.lockType)) {
		// if this lock is for an object id, then the other
		// must be as well, otherwise the other must have a
		// matching name
		return (oid != null)
		    ? other.oid != null && oid.equals(other.oid)
		    : other.name != null && name.equals(other.name);
	    }
	}
	return false;
    }
    
    public int hashCode() {
	return (oid == null) ? name.hashCode() : oid.intValue() 
	    ^ lockType.ordinal();
    }

    public String getBoundName() {
	return name;
    }

    public LockType getLockType() {
	return lockType;
    }

    public BigInteger getObjectID() {
	return oid;
    }

    public Object getObject() {
	return locked;
    }

    void setObject(Object locked) {
	this.locked = locked;
    }

    public String toString() {
	return lockType + ((oid == null) 
			   ? "Lock[name: " + name + "]"
			   : "Lock[oid: " + oid.longValue() + "]");
    }

}