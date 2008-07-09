package com.sun.sgs.contention;

import java.math.BigInteger;

public interface LockInfo {

    public static enum LockType { READ, WRITE }

    public BigInteger getObjectID();
    
    public String getBoundName();

    public Object getObject();

    public LockType getLockType();

}