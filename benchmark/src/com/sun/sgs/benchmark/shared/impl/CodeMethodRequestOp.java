/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.shared.impl;

/**
 * This class maps between opCodes and method names used by the MethodRequest
 * class.  Note that the "method name" values are not actually names of methods
 * called on the server, but instead of just keys that index into a map of code
 * handlers (which could be named anything).  Thus, the method names listed here
 * do not need to be kept in sync with any server code; just with the properties
 * file that defines the mapping of methods to code modules.
 *
 * @see com.sun.sgs.benchmark.shared.MethodRequest
 */
public enum CodeMethodRequestOp {
    /** Enum types */
    CPU               ("cpu"),
    CREATE_CHANNEL    ("createChannel"),
    DATASTORE_CREATE  ("datastoreCreate"),
    DATASTORE_GET     ("datastoreAccess"),
    JOIN_CHANNEL      ("joinChannel"),
    LEAVE_CHANNEL     ("leaveChannel"),
    MALLOC            ("malloc"),
    NOOP              ("noop"),
    SEND_CHANNEL      ("sendChannelMessage"),
    SEND_DIRECT       ("sendDirectMessage"),
    START_TASK        ("startTask");
    
    /** Member variables */
    
    private String methodName;
    
    private static CodeMethodRequestOp[] ordinalIndex;
    
    static {
        CodeMethodRequestOp[] vals = CodeMethodRequestOp.values();
        ordinalIndex = new CodeMethodRequestOp[vals.length];
        
        for (CodeMethodRequestOp cmro : vals)
            ordinalIndex[cmro.ordinal()] = cmro;
    }
    
    /** Constructors */
    
    CodeMethodRequestOp(String methodName) {
        this.methodName = methodName;
    }
    
    /** Public Methods */
    
    /**
     * Slightly kludgy, but Enum doesn't give us a method to return an Enum
     * instance from a given ordinal value, so we do our best to replicate it.
     */
    public static CodeMethodRequestOp fromOpCode(byte opcode) {
        if (opcode >= ordinalIndex.length)
            throw new IllegalArgumentException("Unknown opcode: " + opcode);
        
        return ordinalIndex[opcode];
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public byte getOpCode() {
        int ord = ordinal();
        
        if (ord > 255)
            throw new IllegalStateException("Too many enums declared (" +
                this.values().length + ") - should be <= 256.");
        
        return (byte)ord;
    }
}
