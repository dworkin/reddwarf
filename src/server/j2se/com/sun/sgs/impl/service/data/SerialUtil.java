package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ObjectIOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;

/** Defines serialization utilities.  This class cannot be instantiated. */
final class SerialUtil {

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
     * Converts an object into serialized data.
     *
     * @param	object the object
     * @return	the serialized data
     * @throws	ObjectIOException if a problem occurs serializing the object
     */
    static byte[] serialize(Object object) {
	ObjectOutputStream out = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    out = new ObjectOutputStream(baos);
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
     * Obtains a fingerprint that uniquely identifies the serialized data of
     * the object.
     *
     * @param	object the object
     * @return	the fingerprint
     * @throws	ObjectIOException if a problem occurs serializing the object
     */
    static byte[] fingerprint(Object object) {
	ObjectOutputStream out = null;
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    out = new ObjectOutputStream(baos) {
		protected void writeClassDescriptor(ObjectStreamClass desc)
		    throws IOException
		{
		    writeUTF(desc.getName());
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
 
	
	


