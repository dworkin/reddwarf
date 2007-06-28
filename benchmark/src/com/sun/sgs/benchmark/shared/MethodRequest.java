package com.sun.sgs.benchmark.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;

/**
 *
 *
 */
public class MethodRequest {

    /**
     * The method to be invoked
     */     
    private final byte[] op;

    /**
     * The arguments for the method
     */
    private final byte[] args;

    /**
     * The name of the requested method
     */
    private transient String methodName;

    /**
     * The arguments to the method in Object form
     */
    private transient Object[] objectArgs;

    /**
     * Whether this method request uses compressed byte op-codes or
     * serialized Java objects
     */
    private final boolean isPacked;

    /**
     * Constructs a method request with byte op-codes denoting the
     * operations to perform.
     *
     * @param opCode the op-codes of the requested method
     *
     * @throws IllegalArgumentException if {@code method} is null
     *         
     */
    public MethodRequest(byte[] opCode) {
	this(opCode, new byte[]{}, true);
    }

    /**
     * Constructs a method request with byte op-codes denoting the
     * operations to perform, and op-codes for the arguments of the
     * method.
     *
     * @param opCode the op-codes of the requested method
     * @param args   the op-codes for the arguments of the method
     *
     * @throws IllegalArgumentException if {@code method} or {@code
     *         args} is null
     */
    public MethodRequest(byte[] opCode, byte[] args) {
	this(opCode, args, true);
    }

    /**
     * Private constructor for setting the method, arguments, and
     * whether the request is in compressed op-code format.
     *
     * @param op       if {@code isPacked}, then an op code to execute,
     *                 else, the serialized {@code String} of the
     *                 method name.
     * @param args     if {@code isPacked}, then any byte argument codes,
     *                 else, the serialized form of the {@code Object}
     *                 argument array.
     * @param isPacked whether the method request is in compressed
     *                 op-code format.
     *
     * @throws IllegalArgumentException if {@code method} or {@code
     *         args} is null
     */
    private MethodRequest(byte[] op, byte[] args, boolean isPacked) {
	if (op == null)
	    throw new IllegalArgumentException("Requested method operation cannot be null");
	if (args == null)
	    throw new IllegalArgumentException("Method arguments cannot be a null array");
	this.op = op;
	this.args = args;
	this.methodName = null;
	this.objectArgs = null;
	this.isPacked = isPacked;
    }

    /**
     * Constructs a method request with the provided method name and
     * an empty list of arguments.
     *
     * @param method the name of the method to be invoked
     *
     * @throws IllegalArgumentException if {@code method} is null
     */
    public MethodRequest(String method) {
	this((method == null) ? null : method.getBytes(), 
	     serializeArgs(new Object[] {}), false);
    }

    /**
     * Constructs a method request with the provided method name and
     * an list of arguments.
     *
     * @param method  the name of the method to be invoked
     * @param args    the arguments with which the method should
     *                be invoked.
     *
     * @throws IllegalArgumentException if {@code method} or {@code
     *         args} is null
     */
    public MethodRequest(String method, Object[] args) {
	this((method == null) ? null: method.getBytes(), 
	     (args == null) ? null : serializeArgs(args), false);
    }

    /**
     * Returns the serialized byte array form of the <tt>Object</tt>
     * array argument, or an empty <tt>byte</tt> array if the
     * <tt>args</tt> cannot be serialized.
     *
     * @return the serialized byte array of <tt>args</tt> or an empty
     *         array if it cannot be serialized.
     */
    private static byte[] serializeArgs(Object[] args) {
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	 
	    oos.writeObject(args);
	    oos.close();
	    return baos.toByteArray();
	}
	// fail silently
	catch (IOException ioe) { }
	finally {
	    return new byte[]{ };
	}
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
     */
    private static Object[] deserializeArgs(byte[] args) {
	try {
	    ByteArrayInputStream bais = new ByteArrayInputStream(args);
	    ObjectInputStream ois = new ObjectInputStream(bais);
	    Object[] objectArgs = (Object[])(ois.readObject());
	    return objectArgs;
	}
	// fail silently
	catch (ClassCastException cce) { } 
	catch (ClassNotFoundException cnfe) { }
	catch (InvalidClassException ice) { }
	catch (StreamCorruptedException sce) { }
	catch (OptionalDataException ode) { }
	catch (IOException ioe) { }
	finally {
	    return new Object[] { };
	}
    } 

    /**
     * Returns the op-codes that denote which method is being
     * requested.  If {@link isPacked()} returns false, the return
     * value of this method has no meaning.
     *
     * @return the op-codes for the requested method
     */
    public byte[] getOpCode() {
	return op;
    }

    /**
     * Returns the op-codes that denote the arguments for the
     * requested method If {@link isPacked()} returns false, the
     * return value of this method has no meaning.
     *
     * @return the op-codes for the arguments of the requested method
     */
    public byte[] getArgs() {
	return args;
    }


    /**
     * Returns the name of the requested method.  If {@link
     * #isPacked()} return {@code true}, the results of this method
     * will have no meaning.
     *
     * @return the name of method that should be invoked
     */    
    public String getMethodName() {
	if (methodName == null)
	    methodName = new String(op);
	return methodName;
    }

    /**
     * Returns the {@code Object} arguments with which this method
     * request should be invoked.  If {@link #isPacked()} return
     * {@code true}, the results of this method will be an empty
     * {@code Object} array.
     *
     * @return the arguments with which the method request should be
     *         invoked
     */
    public Object[] getObjectArgs() {
	if (objectArgs == null)
	    objectArgs = deserializeArgs(args);
	return objectArgs;
    }

    /**
     * Returns whether this method request uses compressed op-codes
     * instead of Java objects.
     *
     * @return {@code true} if this method request uses compressed
     *         op-codes instead of java objects.
     */
    public boolean isPacked() {
	return isPacked;
    }

}
