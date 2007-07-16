/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.shared.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;

import java.util.Arrays;

import com.sun.sgs.benchmark.shared.MethodRequest;

/**
 * This class provides a common implementation for {@code MethodRequest}
 * implementations to work off of.
 */
public abstract class AbstractMethodRequestImpl implements MethodRequest {

    /** The arguments to the method in {@code byte} form. */
    private final byte[] args;

    /** The arguments to the method in {@code Object} form. */
    private transient Object[] objectArgs;

    /** Whether this object was created with {@code Object} arguments. */
    private final boolean hasObjectArgs;

    /** Only exposes protected constructors. */
    
    /** Initializes the object with {@code byte} array arguments. */
    protected AbstractMethodRequestImpl(byte[] args) {
        /** Save space if its empty. */
        if (args != null && args.length == 0) args = null;
        
        this.args = args;
        hasObjectArgs = false;
    }
    
    /** Initializes the object with {@code Object} arguments. */
    protected AbstractMethodRequestImpl(Object[] args) {
        /** Save space if its empty. */
        if (args != null && args.length == 0) args = null;  
        
        objectArgs = args;
        
        if (objectArgs == null) {
            this.args = null;
        } else {
            try {
                this.args = serializeArgs(objectArgs);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        
        hasObjectArgs = true;
    }
    
    /**
     * Returns the string description of this method.
     * 
     * @return the string description of this method.
     */
    public String toString() {
	return getMethodName() + ".(" + Arrays.toString(hasObjectArgs() ?
            getObjectArgs() : getByteArgs());
    }

    // implement MethodRequest

    /**
     * {@inheritDoc}
     */
    public byte[] getByteArgs() {
        if (hasObjectArgs)
            throw new IllegalStateException("MethodRequest not created with" +
                " byte-array arguments.");
        
        if (args == null)
            return new byte[0];
        else
            return args;
    }

    /**
     * {@inheritDoc}
     */
    public abstract String getMethodName();

    /**
     * {@inheritDoc}
     */
    public Object[] getObjectArgs() {
        if (!hasObjectArgs)
            throw new IllegalStateException("MethodRequest not created with" +
                " Object arguments.");
        
        if (args == null)
            return new Object[0];
        else {
            if (objectArgs == null)
                objectArgs = deserializeArgs(args);
            
            return objectArgs;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasObjectArgs() {
        return hasObjectArgs;
    }

    /**
     * Returns the serialized byte array form of the <tt>Object</tt>
     * array argument, or an empty <tt>byte</tt> array if the
     * <tt>args</tt> cannot be serialized.
     *
     * @return the serialized byte array of <tt>args</tt> or an empty
     *         array if it cannot be serialized.
     *
     * @throws InvalidClassException - Something is wrong with a class used by
     *         serialization.
     * @throws NotSerializableException - Some object to be serialized does not
     *         implement the java.io.Serializable interface.
     * @throws IOException - Any IOException thrown by the
     *         ByteArrayOutputStream, which should not happen.
     */
    private static byte[] serializeArgs(Object[] args)
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        
        oos.writeObject(args);
        oos.close();
        return baos.toByteArray();
    }

    /**
     * Returns the deserialized <tt>Object</tt> array for the
     * requested, or an empty <tt>Object</tt> array if <tt>args</tt>
     * cannot be deserialized.
     *
     * @param  args serialized form of the <tt>Object</tt> argument
     *         array
     *
     * @return the <tt>Object</tt> array from <tt>args</tt> or an
     *         empty <tt>Object</tt> array if <tt>args</tt> cannot be
     *         deserialized.
     *
     * @throws IllegalArgumentException if any exception occur during deserialization
     */
    private static Object[] deserializeArgs(byte[] args)
        throws IllegalArgumentException
    {
	try {
	    ByteArrayInputStream bais = new ByteArrayInputStream(args);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    Object[] objectArgs = (Object[])(ois.readObject());
	    return objectArgs;
	}
	catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
