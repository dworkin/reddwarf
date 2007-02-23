/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app;

import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.service.ClientSessionService;
import java.io.Serializable;
import java.util.Arrays;


/**
 * Represents a client session ID, and includes static and non-static
 * methods for obtaining a {@code ClientSession} instance for a client
 * session ID.
 */
public class ClientSessionId implements Serializable {

    /** The serialVersionUID for this class. */
    private static final long serialVersionUID = 1L;

    /** The representation of this client session ID. */
    private final byte[] sessionId;

    /**
     * Constructs an instance with the specified byte array, {@code
     * sessionId}, which contains a representation of a client session
     * ID.
     *
     * <p> Note: The {@code sessionId} byte array is used directly by
     * this instance, so the supplied array should not be modified
     * after invoking this constructor or there may be unpredictable
     * results in using the constructed instance.
     *
     * @param 	sessionId a byte array containing a client session ID
     */
    public ClientSessionId(byte[] sessionId) {
	if (sessionId == null) {
	    throw new NullPointerException("null sessionId");
	}

	this.sessionId = sessionId;
    }
    
    /**
     * Returns the {@code ClientSession} for the given {@code
     * sessionId}, or {@code null} if there is no existing {@code
     * ClientSession} for the specified {@code sessionId}.
     *
     * @param	sessionId a byte array containing a client session ID
     *
     * @return	a {@code ClientSession}, or {@code null}
     */
    public static ClientSession getClientSession(byte[] sessionId) {
	return
	    ClientSessionServiceImpl.getInstance().getClientSession(sessionId);
    }

    /**
     * Returns the {@code ClientSession} for this instance, or {@code
     * null} if there is no existing {@code ClientSession} for this
     * {@code ClientSessionId}.
     *
     * @return	a {@code ClientSession}, or {@code null}
     */
    public ClientSession getClientSession() {
	return getClientSession(sessionId);
    }
    
    /**
     * Returns the underlying byte array representing this {@code
     * ClientSessionId}.
     *
     * <p>Note: The returned byte array is used by this instance
     * directly and therefore should not be modified, or there may be
     * unpredictable results in using this instance.
     *
     * @return	the underlying byte array representing this {@code
     * 		ClientSessionId}
     */
    public byte[] getBytes() {
	return sessionId;
    }

    /**
     * Returns {@code true} if the specified object, {@code obj}, is
     * equivalent to this instance, and returns {@code false}
     * otherwise.  An object is equivalent to this instance if it is
     * an instance of {@code ClientSessionId} and has the same byte
     * array representation for its client session ID.
     *
     * @param	obj an object to compare
     * @return 	{@code true} if {@code obj} is equivalent to this
     * 		instance, and {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (! (obj instanceof ClientSessionId)) {
	    return false;
	} else {
	    return
		Arrays.equals(sessionId, ((ClientSessionId) obj).sessionId);
	}
    }

    /**
     * Returns the hash code value for this instance.
     *
     * @return	the hash code value for this instance
     */
    @Override
    public int hashCode() {
	return Arrays.hashCode(sessionId);
    }

    /**
     * Returns the string representation for this instance.
     *
     * @return	the string representation for this instance
     */
    @Override 
    public String toString() {
	return HexDumper.toHexString(sessionId);
    }
}
