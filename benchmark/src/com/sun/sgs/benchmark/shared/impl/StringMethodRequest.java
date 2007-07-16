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
 * An implementation of {@code MethodRequest} that uses full {@code String}
 * objects to represent the method names.
 *
 * @see com.sun.sgs.benchmark.shared.MethodRequest
 */
public class StringMethodRequest implements MethodRequest {

    private static final long serialVersionUID = 0x7CABDAD;

    /** The arguments to the method in {@code byte} form. */
    private final byte[] args;

    /** Byte-encoding of the name of the method to be invoked. */
    private final byte[] methodName;

    /** The arguments to the method in {@code Object} form. */
    private transient Object[] objectArgs;

    /** Whether this object was created with {@code Object} arguments. */
    private final boolean hasObjectArgs;
    
    /**
     * Constructs a method request with the provided method name and
     * an empty list of arguments.
     *
     * @param method the name of the method to be invoked
     *
     * @throws NullPointerException if {@code method} is null
     */
    public StringMethodRequest(String method) {
        args = null;
        methodName = method.getBytes();
        hasObjectArgs = false;
    }

    /**
     * Constructs a method request with the provided method name and
     * a list of {@code Object} arguments.
     *
     * @param method  the name of the method to be invoked
     * @param args    the arguments with which the method should
     *                be invoked.
     *
     * @throws NullPointerException if {@code method} or {@code args} are null
     */
    public StringMethodRequest(String method, byte[] args) {
        if (args == null) throw new NullPointerException();
        
        if (args.length == 0) args = null;  /** Save space if its empty. */
        this.args = args;
        methodName = method.getBytes();
        hasObjectArgs = false;
    }

    /**
     * Constructs a method request with the provided method name and
     * a {@code byte} array of arguments.
     *
     * @param method  the name of the method to be invoked
     * @param args    the arguments with which the method should
     *                be invoked.
     *
     * @throws NullPointerException if {@code method} is null
     */
    public StringMethodRequest(String method, Object[] args) {
        if (args == null) throw new NullPointerException();
        
        if (args.length == 0) {
            objectArgs = null;  /** Save space if its empty. */
            this.args = null;
        } else {
            try {
                objectArgs = args;
                this.args = serializeArgs(objectArgs);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        
        methodName = method.getBytes();
        hasObjectArgs = true;
    }

    /**
     * Returns the string description of this method.
     * 
     * @return the string description of this method.
     */
    public String toString() {
        return getMethodName() + ".(" +
            (hasObjectArgs() ?
                Arrays.toString(getObjectArgs()) :
                Arrays.toString(getByteArgs())) + ")";
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
    public String getMethodName() {
        return new String(methodName);
    }

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
