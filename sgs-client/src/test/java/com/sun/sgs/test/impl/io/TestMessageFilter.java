/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.test.impl.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.apache.mina.common.ByteBuffer;

import com.sun.sgs.impl.io.FilterTestHarness;

/**
 * This suite of tests is intended to test the functionality of the 
 * {code CompleteMessageFilter}.
 */
public class TestMessageFilter
        extends TestCase
        implements FilterTestHarness.Callback
{
    final ByteBuffer sizeBuf = ByteBuffer.allocate(2, false);
    final List<byte[]> receivedMessages = new ArrayList<byte[]>();
    final List<byte[]> sentMessages = new ArrayList<byte[]>();

    FilterTestHarness harness = null;

    /**
     * Sets up a {@link FilterTestHarness} for this test, and clears
     * the list of received and sent messages.
     */
    @Override
    public void setUp() {
        System.err.println("Testcase: " + this.getName());
        sizeBuf.sweep();
        receivedMessages.clear();
        sentMessages.clear();
        harness = new FilterTestHarness(this);
    }

    /**
     * Performs cleanup after each test case.
     */
    @Override
    public void tearDown() {
        sizeBuf.clear();
        receivedMessages.clear();
        sentMessages.clear();
        harness = null;
    }

    /** {@inheritDoc} */
    public void filteredMessageReceived(ByteBuffer buf) {
        addToList(receivedMessages, buf);
    }

    /** {@inheritDoc} */
    public void sendUnfiltered(ByteBuffer buf) {
        addToList(sentMessages, buf);
    }

    private void addToList(List<byte[]> list, ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        list.add(bytes);
    }

    private byte[] getByteSequence(int size) {
        byte[] seq = new byte[size];
        for (int i = 0; i < size; ++i) {
            seq[i] = (byte) (i & 0xFF);
        }
        return seq;
    }

    /** Tests that a simple receive works. */
    public void testSimpleReceive() {
        short len = 1000;
        byte[] expected = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(expected);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        harness.recv(buf);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));
    }

    /** Tests that two simple receives work. */
    public void testMultipleSimpleReceives() {
        short len = 1000;
        byte[] expected = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(expected);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        harness.recv(buf);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));

        harness.recv(buf.rewind());

        assertEquals(0, sentMessages.size());
        assertEquals(2, receivedMessages.size());

        actual = receivedMessages.get(1);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));
    }

    /**
     * Tests that a big message gets reassembled by the filter.
     */
    public void testBigReceive() {
        // Send a 7KB message in 1KB chunks
        short len = 7 * 1024;
        byte[] expected = new byte[len];
        sizeBuf.putShort(len).flip();
        harness.recv(sizeBuf.asReadOnlyBuffer());

        ByteBuffer buf =
            ByteBuffer.wrap(getByteSequence(1024)).asReadOnlyBuffer();

        // Do the recvs
        for (int i = 0; i < 7; ++i) {
            assertEquals(0, sentMessages.size());
            assertEquals(0, receivedMessages.size());

            buf.rewind();
            buf.get(expected, i * 1024, 1024);
            buf.rewind();
            harness.recv(buf);
        }

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));
    }

    /**
     * Tests that zero-byte receives have no effect on complete
     * message delivery.
     */
    public void testPartialReceiveNoBytes() {
        short len = 1000;

        sizeBuf.putShort(len).flip();
        harness.recv(sizeBuf.asReadOnlyBuffer());

        ByteBuffer emptyBuf = sizeBuf.slice().limit(0).asReadOnlyBuffer();
        harness.recv(emptyBuf);

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        byte[] expected = getByteSequence(len);
        ByteBuffer buf =
            ByteBuffer.wrap(expected).asReadOnlyBuffer();

        harness.recv(buf);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));

        harness.recv(emptyBuf);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        harness.recv(sizeBuf.rewind().asReadOnlyBuffer());
        harness.recv(buf.rewind());

        assertEquals(0, sentMessages.size());
        assertEquals(2, receivedMessages.size());

        actual = receivedMessages.get(1);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));
    }

    /**
     * Tests that complete messages are delivered even when the message
     * arrives one byte at a time from the network.  Then tests that a
     * second message can still be received all-at-once.
     */
    public void testPartialOneByteReceives() {
        short len = 32;
        byte[] expected = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(expected);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        for (; buf.hasRemaining(); buf.skip(1)) {
            assertEquals(0, sentMessages.size());
            assertEquals(0, receivedMessages.size());
            ByteBuffer singleByte = buf.slice().limit(1);
            harness.recv(singleByte);
        }

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));

        harness.recv(buf.rewind());

        assertEquals(0, sentMessages.size());
        assertEquals(2, receivedMessages.size());

        actual = receivedMessages.get(1);

        assertTrue("Incorrect recv!", Arrays.equals(actual, expected));
    }

    /** Tests handling of a valid message with payload length zero. */
    public void testReceiveValidZeroLength() {
        sizeBuf.putShort((short) 0).flip();

        harness.recv(sizeBuf.asReadOnlyBuffer());

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        assertEquals(0, receivedMessages.get(0).length);
    }

    /** Tests handling of exceptions during dispatch. */
    public void testReceiveHandlingException() {
        short len = 1000;
        byte[] expected = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(expected);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        RuntimeException expectedEx =
            new RuntimeException("Dummy exception for testing filter recv");

        harness.setExceptionOnNextCompleteMessage(expectedEx);

        // This recv will fail to process the message
        harness.recv(buf);
        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        // Send a second message, expecting the first to have been dropped.
        buf.rewind();
        harness.recv(buf);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());
    }

    /** Tests handling of exceptions during dispatch. */
    public void testReceiveHandlingExceptionPartial() {
        short len1 = 400;
        short len2 = 600;
        short len = (short) (len1 + len2);
        byte[] expected = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(expected);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        ByteBuffer part1 = buf.slice().limit(len1 + 2);

        ByteBuffer part2 = ByteBuffer.allocate(len2 + len2 + 2, false);
        buf.rewind();
        buf.skip(len1 + 2);
        part2.put(buf);
        buf.rewind();
        buf.skip(len1 + 2);
        part2.putShort(len2);
        part2.put(buf);
        part2.flip();

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        harness.recv(part1);

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        RuntimeException expectedEx =
            new RuntimeException("Dummy exception for testing filter recv");

        harness.setExceptionOnNextCompleteMessage(expectedEx);

        // This recv should process the second message.
        harness.recv(part2);

        assertEquals(0, sentMessages.size());
        assertEquals(1, receivedMessages.size());

        byte[] actual = receivedMessages.get(0);
        assertEquals(len2, actual.length);
    }

    /** Tests that the send filter correctly prepends the message length. */
    public void testSend() {
        short len = 1000;
        byte[] sendData = getByteSequence(len);
        ByteBuffer buf = ByteBuffer.allocate(len + 2 , false);
        buf.putShort(len);
        buf.put(sendData);
        buf = buf.asReadOnlyBuffer();
        buf.flip();

        byte[] expected = new byte[buf.remaining()];
        buf.get(expected);

        assertEquals(0, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        harness.send(sendData);

        assertEquals(1, sentMessages.size());
        assertEquals(0, receivedMessages.size());

        byte[] actual = sentMessages.get(0);

        assertTrue("Incorrect send!", Arrays.equals(actual, expected));
    }
}
