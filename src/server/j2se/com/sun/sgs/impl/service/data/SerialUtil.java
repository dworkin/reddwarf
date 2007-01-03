package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
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
		/*
		 * Check explicitly for instances of inner classes whose
		 * enclosing instance is a managed object, so that the
		 * exception can refer to the inner class instance, rather than
		 * the enclosing one, which isn't really where the problem
		 * lies.
		 */
		if (cl.isMemberClass() &&
		    !Modifier.isStatic(cl.getModifiers()))
		{
		    Class<?> enclosingClass = cl.getEnclosingClass();
		    if (enclosingClass != null &&
			ManagedObject.class.isAssignableFrom(enclosingClass))
		    {
			throw new ObjectIOException(
			    "Cannot store an instance of an inner class " +
			    "whose enclosing class implements " +
			    "ManagedObject: " + object + ", " + cl,
			    false);
		    }
		} else if (cl.isAnonymousClass()) {
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
     * Checks if an object has a particular fingerprint.
     *
     * @param	object the object
     * @param	fingerprint the fingerprint
     * @return	whether the object has a matching fingerprint
     * @throws	ObjectIOException if a problem occurs serializing the object
     */
    static boolean matchingFingerprint(Object object, byte[] fingerprint) {
	return Arrays.equals(fingerprint(object), fingerprint);
    }
}
