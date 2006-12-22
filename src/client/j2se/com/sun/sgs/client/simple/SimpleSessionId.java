package com.sun.sgs.client.simple;

import java.util.Arrays;

import com.sun.sgs.client.SessionId;

/**
 * This is just a simple implementation of a SessionId that is a 
 * wrapper around a byte array.  Currently this is acting as a place-holder
 * so the rest of the Client API can be written.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleSessionId extends SessionId {

    private byte[] id;
    
    public SimpleSessionId(byte[] id) {
        this.id = id;
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof SessionId && 
                Arrays.equals(((SessionId) obj).toBytes(), toBytes()));
    }
 
    @Override
    public byte[] toBytes() {
        return id;
    }

}
