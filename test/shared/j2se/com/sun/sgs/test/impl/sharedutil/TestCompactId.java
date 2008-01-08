/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.sharedutil.CompactId;
import java.util.Arrays;
import junit.framework.TestCase;

public class TestCompactId extends TestCase {

    /** Creates an instance. */
    public TestCompactId(String name) {
	super(name);
    }

    /** Prints the test case and sets the service field to a new instance. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
    }

    /* -- Tests -- */


    public void testConstructorNullId() {
	try {
	    new CompactId(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorEmptyId() {
	try {
	    new CompactId(new byte[0]);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testZeroIds() {
	for (int i = 1; i <=8; i++) {
	    checkId(new byte[i], 0x00, 2);
	}
    }

    public void testOneByteIds() {
	byte mask = 0x01;	    
	for (int shifts = 0; shifts < 8; shifts++, mask <<= 1) {
	    byte[] idBytes = new byte[1];
	    idBytes[0] = mask;
	    checkId(idBytes, 0x00, 2);
	}
    }
    
    public void testTwoByteIds() {
	byte mask = 0x01;	    
	for (int shifts = 0; shifts < 6; shifts++, mask <<= 1) {
	    byte[] idBytes = new byte[2];
	    idBytes[0] = mask;
	    checkId(idBytes,  0x00, 2);
	}
    }
    
    public void testFourByteIds() {
	byte mask = 0x01;
	for (int shifts = 0; shifts < 6; shifts++, mask <<= 1) {
	    byte[] idBytes = new byte[4];
	    idBytes[0] = mask;
	    checkId(idBytes, 0x40, 4);
	}
    }

    public void testEightByteIds() {
	for (int i = 1; i >= 0; i--) {
	    byte mask = 0x01;
	    for (int shifts = 0; shifts < 6; shifts++, mask <<= 1) {
		byte[] idBytes = new byte[8];
		idBytes[i] = mask;
		checkId(idBytes, 0x80, 8);
	    }
	}
    }

    public void testLargeIds() {
	for (int i = 8; i <=23; i++) {
	    byte[] idBytes = new byte[i];
	    idBytes[0] = (byte) 0xff;
	    checkId(idBytes, 0xc0, i+1);
	}
    }

    public void testTooLargeId() {
	byte[] idBytes = new byte[24];
	idBytes[0] = (byte) 0xff;
	try {
	    new CompactId(idBytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testGetExternalFormByteCount() {
	checkExternalFormByteCount(0x00, 2);
	checkExternalFormByteCount(0x40, 4);
	checkExternalFormByteCount(0x80, 8);
	for (int i = 0; i <= 15; i++) {
	    checkExternalFormByteCount(0xc0 + i, 9 + i);
	}
    }

    public void testGetExternalFormByteCountBadFormat() {
	for (byte b = (byte) 0xd0; b <= (byte) 0xf0; b += 0x10) {
	    try {
		CompactId.getExternalFormByteCount(b);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    public void testFromExternalFormNullExternalForm() {
	try {
	    CompactId.fromExternalForm(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    public void testFromExternalFormBadSize() {
	int[] badSizes =
	    new int[]{0, 1, 3, 5, 6, 7, 8, 8, 10, 11};
	int[] masks =
	    new int[]{ 0x00, 0x00, 0x00, 0x40, 0x40, 0x80, 0x00, 0xc0 , 0xc0, 0xc1};

	for (int i = 0; i < badSizes.length; i++) {
	
	    byte[] ext = new byte[badSizes[i]];
	    if (ext.length > 0) {
		ext[0] = (byte) masks[i];
	    }
	    
	    try {
		CompactId.fromExternalForm(ext);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    public void testFromExternalFormBadFormat() {

	for (byte b = (byte) 0xd0; b <= (byte) 0xf0; b += 0x10) {
	    byte[] ext = new byte[2];
	    ext[0] = b;
	    try {
		CompactId.fromExternalForm(ext);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    public void testPutCompactIdNullBuffer() {
	try {
	    new CompactId(new byte[]{ 0x33 }).putCompactId(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testPutCompactIdBufferTooSmall() {
	CompactId id = new CompactId(
		new byte[]{ (byte) 0xca, (byte) 0xfe,
			    (byte) 0xba, (byte) 0xbe });
	for (int i = 1; i < 8; i++) {
	    try {
		id.putCompactId(new MessageBuffer(i));
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
	try {
	    id.putCompactId(new MessageBuffer(8));
	} catch (Exception e) {
	    e.printStackTrace();
	    fail("Unexpected exception" + e);
	}
    }

    public void testGetCompactIdNullBuffer() {
	try {
	    CompactId.getCompactId(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    public void testGetCompactIdEmptyBuffer() {
	MessageBuffer buf = new MessageBuffer(1);
	buf.putByte(0);
	try {
	    CompactId.getCompactId(buf);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testGetCompactIdBufferTooSmall() {
	int[] badSizes =
	    new int[]{ 1,    3,    5,    6,    7,    8,    9,     10};
	int[] masks =
	    new int[]{ 0x00, 0x40, 0x80, 0x80, 0x80, 0xc0, 0xc1, 0xc2};

	for (int i = 0; i < badSizes.length; i++) {
	
	    byte[] ext = new byte[badSizes[i]];
	    if (ext.length > 0) {
		ext[0] = (byte) masks[i];
	    }
	    MessageBuffer buf = new MessageBuffer(ext.length);
	    buf.putBytes(ext);
	    buf.rewind();
	    try {
		CompactId.getCompactId(buf);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }
    
    private void checkExternalFormByteCount(int length, int expectedCount) {
	int byteCount = CompactId.getExternalFormByteCount((byte) length);
	
	if (byteCount != expectedCount) {
	    fail("Expected byte count of " + expectedCount +
		 ", got " + byteCount);
	}
    }
    
    private void checkId(byte[] idBytes, int mask, int size) {
	CompactId id1 = new CompactId(idBytes);
	byte[] canonicalId = canonical(idBytes);
	if (! Arrays.equals(canonicalId, id1.getId())) {
	    fail("Expected canonical ID " +
		 HexDumper.toHexString(canonicalId) +
		 ", got " + HexDumper.toHexString(id1.getId()));
	}
	byte[] ext = id1.getExternalForm();
	System.err.println("id1: " + id1 + ", externalForm: " +
			   HexDumper.toHexString(ext));
	if (ext.length != size) {
	    fail("Expected ID length of " + size + ", got " + ext.length);
	}
	int length = ext[0] & 0xc0;
	if (length != mask) {
	    fail("Expected length field of " +
		 String.format("%x02x", (byte) mask) +
		 ", got " + String.format("%02x", (byte) length));
	}
	CompactId id2 = CompactId.fromExternalForm(ext);
	System.err.println("id2: " + id2 + ", externalForm: " +
			   HexDumper.toHexString(id2.getExternalForm()));
	if (! Arrays.equals(canonicalId, id2.getId())) {
	    fail("Expected canonical ID " +
		 HexDumper.toHexString(canonicalId) +
		 ", got " + HexDumper.toHexString(id2.getId()));
	}
	if (! id1.equals(id2)) {
	    fail("Expected equal ids; idBytes: " +
		 HexDumper.toHexString(idBytes));
	}

	// Test putCompactId and getCompactId
	MessageBuffer buf = new MessageBuffer(24);
	id1.putCompactId(buf);
	buf.rewind();
	CompactId id3 = CompactId.getCompactId(buf);
	if (! id1.equals(id3)) {
	    fail("Expected equal ids from putCompactId/getCompactId; id1: " + id1 +
		 ", id3: " + id3);
	}
	
    }

    private static byte[] canonical(byte[] id) {
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
}
