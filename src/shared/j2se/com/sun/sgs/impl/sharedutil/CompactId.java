/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.sharedutil;

import java.util.Arrays;

/**
 * A utility class for constructing IDs with a self-describing
 * external format.  A {@code CompactId} is stored in its canonical form,
 * with all leading zero bytes stripped.  The external format is
 * designed to be compact if the canonical ID has a small number of
 * bytes.
 *
 * <p>The first byte of the ID's external form contains a length field
 * of variable size.  If the first two bits of the length byte are not
 * #b11, then the size of the ID is indicated as follows:
 *
 * <ul>
 * <li>#b00: 14 bit ID (2 bytes total)</li>
 * <li>#b01: 30 bit ID (4 bytes total)</li>
 * <li>#b10: 62 bit ID (8 bytes total)</li>
 * </ul>
 *
 * <p>If the first byte has the following format:
 * <ul><li>1100<i>nnnn</i></li></ul> then, the ID is contained in
 * the next {@code 8 +  nnnn} bytes.
 *
 * <p>The maximum length of an ID is 23 bytes (if the first byte of
 * the external form has the value {@code 11001111}).
 */
public final class CompactId {

    /** The maximum supported ID size, in bytes. */
    public static final int MAX_SIZE = 8 + 0x0f;

    /** The canonical form for this ID. */
    private final byte[] canonicalId;
    
    /** The external form for this ID. */
    private final byte[] externalForm;

    /**
     * Constructs an instance with the specified {@code id}.  The
     * {@code id} is stored in its canonical form, with all leading
     * zero bytes stripped.
     *
     * @param	id a byte array containing an ID
     *
     * @throws	IllegalArgumentException if {@code id} is empty or
     *		if the {@code id} length exceeds the maximum length
     */
    public CompactId(byte[] id) {
	if (id == null) {
	    throw new NullPointerException("null id");
	} else if (id.length == 0) {
	    throw new IllegalArgumentException("zero length id");
	} else if (id.length > MAX_SIZE) {
	    throw new IllegalArgumentException(
		"id length exceeds maximum size of " + MAX_SIZE);
	}
	canonicalId = stripLeadingZeroBytes(id);
	externalForm = toExternalForm(canonicalId);
    }

    /**
     * Constructs an instance with the specified {@code id} with
     * external form {@code ext}.  This constructor is only used by
     * the {@code fromExternalForm} method to construct an instance
     * with a known, valid ID and external form.
     */
    private CompactId(byte[] id, byte[] ext) {
	canonicalId = id;
	externalForm = ext;
    }

    /**
     * Returns the underlying byte array containing the canonical ID
     * for this instance.  The canonical ID contains no leading zero
     * bytes.
     *
     * @return	the byte array containing the ID for this instance
     */
    public byte[] getId() {
	return canonicalId;
    }

    /**
     * Returns the underlying byte array containing the external form
     * for this instance.
     *
     * @return	the byte array containing the external form for this instance
     */
    public byte[] getExternalForm() {
	return externalForm;
    }
    
    /**
     * Returns the length, in bytes, of the external form for this instance.
     *
     * @return the length, in bytes, of the external form for this instance
     */
    public int getExternalFormByteCount() {
	return externalForm.length;
    }

    /**
     * Constructs a {@code CompactId} from the specified {@code externalForm}.
     *
     * @param	externalForm a byte array containing the external form
     *		of a {@code CompactId}
     *
     * @return	a {@code CompactId} constructed from the specified
     *		{@code externalForm} 
     *
     * @throws 	IllegalArgumentException if {@code externalForm} is empty,
     * 		if {@code externalForm} has a different number of bytes
     * 		than is specified by its length field, if {@code
     * 		externalForm}'s length exceeds the maximum length, or if
     *		the ID format is otherwise malformed or unsupported
     */
    public static CompactId fromExternalForm(byte[] externalForm) {
	if (externalForm == null) {
	    throw new NullPointerException("null external form");
	} else if (externalForm.length < 2) {
	    throw new IllegalArgumentException(
		"invalid external form; must have 2 or more bytes");
	} else if (externalForm.length > MAX_SIZE + 1) {
	    throw new IllegalArgumentException(
		"invalid external form; > 23 bytes unsupported");
	}

	int size = getExternalFormByteCount(externalForm[0]);
	if (size != externalForm.length) {
	    throw new IllegalArgumentException(
		"invalid external form; should have " + size + " bytes");
	}

	byte[] id;
	if (size <= 8) {
	    int firstByte = externalForm[0] & 0x3f;
	    int first = 0;
	    
	    // find first non-zero byte in external form
	    if (firstByte == 0) {
		for (first = 1;
		     first < externalForm.length && externalForm[first] == 0;
		     first++)
		    ;
	    }
 	
	    if (first == externalForm.length) {
		// all bytes are zero, so first byte is last byte
		first = externalForm.length-1;
	    }
	    id = new byte[externalForm.length - first];
	    System.arraycopy(externalForm, first, id, 0, id.length);
	    if (first == 0) {
		id[0] = (byte) firstByte;
	    }
	} else {
	    id = new byte[externalForm.length-1];
	    System.arraycopy(externalForm, 1, id, 0, id.length);
	}
	return new CompactId(id, externalForm);
    }
    
    /**
     * Returns the byte count of a {@code CompactId}'s external form with
     * the given {@code lengthByte}.  The returned byte count includes
     * the given byte in the count.
     *
     * @param	lengthByte the first byte of the external form which
     *		contains byte count information
     * @return	the byte count of a {@code CompactId}'s external form
     * 		indicated by {@code lengthByte}
     * @throws	IllegalArgumentException if the given {@code lengthByte}
     *		contains an unsupported format
     */
    public static int getExternalFormByteCount(byte lengthByte) {

	switch (lengthByte & 0xc0) {
	    
	case 0x00:
	    return 2;
	    
	case 0x40:
	    return 4;
	    
	case 0x80:
	    return 8;

	default /* 0xc0 */:
	    if ((lengthByte & 0x30) == 0) {
		return 9 + (lengthByte & 0x0f);
	    } else {
		throw new IllegalArgumentException(
		    "unsupported id format; lengthByte: " +
		    String.format("%02x", lengthByte));
	    }
	}
    }

    /**
     * Puts the external form of this {@code CompactId} in the specified
     * message buffer.
     *
     * @param	buf a message buffer
     * @throws	IllegalArgumentException if the message buffer size is
     *		insufficient
     */
    public void putCompactId(MessageBuffer buf) {
	if (buf.capacity() - buf.position() < externalForm.length) {
	    throw new IllegalArgumentException("buffer size insufficient");
	}
	buf.putBytes(externalForm);
    }

    /**
     * Returns a {@code CompactId} constructed from the ID's external
     * format contained in the specified message buffer.
     *
     * @param 	buf a message buffer containing the external format of
     *		a {@code CompactId}
     * @return	a {@code CompactId} constructed from the external format
     *		in the given message buffer
     * @throws 	IllegalArgumentException if the external format
     * 		contained in the message buffer is malformed or unsupported
     */
    public static CompactId getCompactId(MessageBuffer buf) {
	int bufSize = buf.limit() - buf.position();
	if (bufSize == 0) {
	    throw new IllegalArgumentException("empty buffer");
	}
	byte lengthByte = buf.getByte();
	int size = getExternalFormByteCount(lengthByte);
	if (bufSize < size) {
	    throw new IllegalArgumentException("buffer size insufficient");
	}
	byte[] externalForm = new byte[size];
	externalForm[0] = lengthByte;
	for (int i = 1; i < size; i++) {
	    externalForm[i] = buf.getByte();
	}
	return CompactId.fromExternalForm(externalForm);
    }

    /* -- java.lang.Object overrides -- */

    /**
     * Returns {@code true} if the specified object, {@code obj}, is
     * equivalent to this instance, and returns {@code false}
     * otherwise.  An object is equivalent to this instance if it is
     * an instance of {@code CompactId} and has the same representation
     * for its ID.
     *
     * @param	obj an object to compare
     * @return 	{@code true} if {@code obj} is equivalent to this
     * 		instance, and {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (! (obj instanceof CompactId)) {
	    return false;
	} else {
	    CompactId thatCompactId = (CompactId) obj;
	    return
		Arrays.equals(canonicalId, thatCompactId.canonicalId) &&
		Arrays.equals(externalForm, thatCompactId.externalForm);
	}
    }

    /**
     * Returns the hash code value for this instance.
     *
     * @return	the hash code value for this instance
     */
    @Override
    public int hashCode() {
	return Arrays.hashCode(canonicalId);
    }

    /**
     * Returns the string representation for this instance.
     *
     * @return	the string representation for this instance
     */
    @Override 
    public String toString() {
	return HexDumper.toHexString(canonicalId);
    }

    /* -- other methods -- */

    /**
     * Returns a new byte array containing the specified {@code id}
     * with leading zero bytes stripped.
     */
    private static byte[] stripLeadingZeroBytes(byte[] id) {
	// find first nonzero byte
	int first = 0;
	for (; first < id.length && id[first] == 0; first++)
	    ;
	if (first == id.length) {
	    // all bytes are zero, so first byte is last byte
	    first = id.length-1;
	}
	byte[] canonicalId = new byte[id.length-first];
	System.arraycopy(id, first, canonicalId, 0, canonicalId.length);
	return canonicalId;
    }
    
    /**
     * Returns the external form for the given {@code id}.
     *
     * Note: The specified {@code id} must be in its canonical form
     * (with no leading zero bytes, unless it is a single zero byte).
     */
    private static byte[] toExternalForm(byte[] id) {
	assert id != null;
	assert id.length != 0;
	assert id.length <= MAX_SIZE;
	
	// find bit count
	int first = 0;
	int b = id[first];
	int zeroBits = 0;
	for ( ; (zeroBits < 8) && ((b & 0x80) == 0); b <<= 1, zeroBits++)
	    ;
	int bitCount = ((id.length - first - 1) << 3) + (8 - zeroBits);
	
	// determine external form's byte count and the mask for most
	// significant two bits of first byte.
	int mask = 0x00;
	int size;
	if (bitCount <= 14) {
	    size = 2;
	} else if (bitCount <= 30) {
	    size = 4;
	    mask = 0x40;
	} else if (bitCount <= 62) {
	    size = 8;
	    mask = 0x80;
	} else {
	    size = id.length + 1;
	    mask = 0xc0 + id.length - 8;
	}

	// copy id into destination byte array and apply mask
	byte[] external = new byte[size];
	for (int i = id.length-1, e = size-1; i >= first; i--, e--) {
	    external[e] = id[i];
	}
	external[0] |= mask;
	
	return external;
    }
}

