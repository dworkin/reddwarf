/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Defines serialization utilities.  This class cannot be instantiated. */
final class SerialUtil {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(SerialUtil.class.getName()));

    /** This class cannot be instantiated. */
    private SerialUtil() {
	throw new AssertionError();
    }

    /**
     * Converts serialized data into an object.
     *
     * @param	data the serialized data
     * @return	the object
     * @throws	ObjectIOException if a problem occurs deserializing the object
     */
    static Object deserialize(byte[] data) {
	ObjectInputStream in = null;
	try {
	    in = new ObjectInputStream(new ByteArrayInputStream(data));
	    return in.readObject();
	} catch (ClassNotFoundException e) {
	    throw new ObjectIOException(
		"Class not found while deserializing object: " +
		e.getMessage(),
		e, false);
	} catch (IOException e) {
	    throw new ObjectIOException(
		"Problem deserializing object: " + e.getMessage(), e, false);
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Converts an managed object into serialized data.
     *
     * @param	object the object
     * @return	the serialized data
     * @throws	ObjectIOException if a problem occurs serializing the object
     *		and, in particular, if a <code>ManagedObject</code> is
     *		referenced without an intervening <code>ManagedReference</code>
     */
    static byte[] serialize(ManagedObject object) {
	ObjectOutputStream out = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    out = new CheckReferencesObjectOutputStream(baos, object);
	    out.writeObject(object);
	    out.flush();
	    return baos.toByteArray();
	} catch (ObjectIOException e) {
	    check(object, e);
	    throw e;
	} catch (IOException e) {
	    throw new ObjectIOException(
		"Problem serializing object: " + e.getMessage(), e, false);
	} finally {
	    if (out != null) {
		try {
		    out.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Define an ObjectOutputStream that checks for references to
     * ManagedObjects not made through ManagedReferences.  Note that this
     * stream will not be able to detect direct references to the top level
     * object, except when the reference is the synthetic reference made by an
     * inner class instance.
     */
    private static final class CheckReferencesObjectOutputStream
	extends ObjectOutputStream
    {
	/** The top level managed object being serialized. */
	private final ManagedObject topLevelObject;

	/**
	 * Creates an instance that writes to a stream for a managed object
	 * being serialized.
	 */
	CheckReferencesObjectOutputStream(
	    OutputStream out, ManagedObject topLevelObject)
	    throws IOException
	{
	    super(out);
	    this.topLevelObject = topLevelObject;
	    AccessController.doPrivileged(
		new PrivilegedAction<Void>() {
		    public Void run() {
			enableReplaceObject(true);
			return null;
		    }
		});
	}

	/** Check for references to managed objects. */
	protected Object replaceObject(Object object) throws IOException {
	    if (object != topLevelObject && object instanceof ManagedObject) {
		throw new ObjectIOException(
		    "ManagedObject was not referenced through a " +
		    "ManagedReference: " + object,
		    false);
	    } else if (object != null) {
		Class<?> cl = object.getClass();
		if (cl.isAnonymousClass()) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "Storing an instance of an anonymous class: " +
			    "{0}, {1}",
			    object, cl);
		    }
		} else if (cl.isLocalClass()) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "Storing an instance of a local class: {0}, {1}",
			    object, cl);
		    }
		}
	    }
	    return object;
	}
    }

    /**
     * Obtains a fingerprint that uniquely identifies the serialized data of
     * the object.
     *
     * @param	object the object
     * @return	the fingerprint
     * @throws	ObjectIOException if a problem occurs serializing the object
     */
    static byte[] fingerprint(Object object) {
	/*
	 * TBD: Maybe use a message digest if the fingerprint gets long.
	 * -tjb@sun.com (11/16/2006)
	 */
	ObjectOutputStream out = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    out = new ObjectOutputStream(baos) {
		protected void writeClassDescriptor(ObjectStreamClass desc)
		    throws IOException
		{
		    writeObject(desc.getName());
		}
	    };
	    out.writeObject(object);
	    out.flush();
	    return baos.toByteArray();
	} catch (IOException e) {
	    throw new ObjectIOException(
		"Problem serializing object: " + e.getMessage(), e, false);
	} finally {
	    if (out != null) {
		try {
		    out.close();
		} catch (IOException e) {
		}
	    }
	}
    }

    /**
     * Checks if an object has a particular fingerprint.  Returns false if
     * attempting to compute the fingerprint throws an ObjectIOException.
     *
     * @param	object the object
     * @param	fingerprint the fingerprint
     * @return	whether the object has a matching fingerprint
     */
    static boolean matchingFingerprint(Object object, byte[] fingerprint) {
	try {
	    return Arrays.equals(fingerprint(object), fingerprint);
	} catch (ObjectIOException e) {
	    return false;
	}
    }

    /**
     * Checks an object for direct references to managed objects, throwing an
     * exception that contains debugging information about where the bad
     * reference was found.
     *
     * @param	object the object to check
     * @param	cause the exception that prompted the check, or null
     * @throws	ObjectIOException if a direct reference to a managed object is
     *		found
     */
    static void check(Object object, ObjectIOException cause) {
	try {
	    new Check(object, cause).checkObject(object);
	} catch (ObjectIOException e) {
	    /* Don't include the nested stack trace from the check itself */
	    e.fillInStackTrace();
	    throw e;
	}
    }

    /**
     * Class to check for bad references.  Uses reflection to traverse the
     * object graph, keeping a stack describing the traversal for use in error
     * messages.  Although the check performed does not account for
     * serialization features such as writeReplace, the assumption is that
     * direct references to managed objects are wrong wherever they are found.
     */
    private static class Check {

	/** The top level object being checked. */
	private final Object topObject;

	/** The thrown exception that prompted the check, or null. */
	private final ObjectIOException cause;

	/** The stack that stores information about object references. */
	private final DebugStack stack = new DebugStack();

	/** Stores information about objects already seen. */
	private final IdentityHashMap<Object, Boolean> seen =
	    new IdentityHashMap<Object, Boolean>();

	/** A version of Stack that formats output appropriately. */
	private static class DebugStack extends Stack<String> {
	    /* Silence lint warning */
	    private static final long serialVersionUID = 1;
	    DebugStack() { }
	    public String push(String s) {
		return super.push((isEmpty() ? "\t-root: " : "\t-") + s);
	    }
	    public String toString() {
		StringBuilder buffer = new StringBuilder();
		if (!isEmpty()) {
		    while (true) {
			buffer.append(pop());
			if (isEmpty()) {
			    break;
			}
			buffer.append('\n');
		    }
		}
		return buffer.toString();
	    }
	}

	/**
	 * Creates an instance for the specified top level object and cause.
	 */
	Check(Object topObject, ObjectIOException cause) {
	    this.topObject = topObject;
	    this.cause = cause;
	}

	/** Checks an object for bad references. */
	private void checkObject(Object object) {
	    if (object != null &&
		!(object instanceof ManagedReference) &&
		!seen.containsKey(object))
	    {
		Class<?> cl = object.getClass();
		if (object != topObject && object instanceof ManagedObject) {
		    stack.push("object (class \"" + cl.getName() + "\", " +
			       object + ")");
		    throw new ObjectIOException(
			"ManagedObject was not referenced through a " +
			"ManagedReference:\n" + stack,
			cause, false);
		}
		seen.put(object, Boolean.TRUE);
		if (cl.isArray()) {
		    checkArray(object);
		} else {
		    checkNonArray(object);
		}
	    }
	}

	/** Checks an array for bad reference. */
	private void checkArray(Object array) {
	    Class<?> cl = array.getClass();
	    Class<?> compCl = cl.getComponentType();
	    if (!compCl.isPrimitive()) {
		int len = Array.getLength(array);
		stack.push("array (class \"" + className(cl) +
			   "\", size: " + len + ")");
		for (int i = 0; i < len; i++) {
		    stack.push("element of array (index: " + i + ")");
		    checkObject(Array.get(array, i));
		    stack.pop();
		}
		stack.pop();
	    }
	}

	/** Checks an non-array for bad reference. */
	private void checkNonArray(Object object) {
	    Class<?> cl = object.getClass();
	    stack.push("object (class \"" + cl.getName() + "\", " + object +
		       ")");
	    for ( ; cl != null; cl = cl.getSuperclass()) {
		for (Field f : cl.getDeclaredFields()) {
		    if (!Modifier.isStatic(f.getModifiers()) &&
			!f.getType().isPrimitive())
		    {
			stack.push((f.isSynthetic() ? "synthetic " : "") +
				   "field (class \"" + cl.getName() +
				   "\", name: \"" + f.getName() +
				   "\", type: \"" + className(f.getType()) +
				   "\")");
			f.setAccessible(true);
			try {
			    checkObject(f.get(object));
			} catch (IllegalAccessException e) {
			    throw new AssertionError(e);
			}
			stack.pop();
		    }
		}
	    }
	    stack.pop();
	}

	/** Returns the name of a class, converting array class names. */
	private static String className(Class<?> cl) {
	    StringBuilder sb = new StringBuilder();
	    while (cl.isArray()) {
		sb.append("[]");
		cl = cl.getComponentType();
	    }
	    String className = cl.getName();
	    sb.insert(0, className);
	    return sb.toString();
	}
    }
}
