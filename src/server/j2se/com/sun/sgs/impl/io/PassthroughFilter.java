package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A filter that doesn't do anything except simply pass the messages through.
 * This is the default filter.  Since message length is encoded as part of the
 * wire protocol, this filter must extract the length information before
 * forwarding the bytes onto the event handler.
 * <p>
 * The {@link #filterReceive} portion of {@code PassthroughFilter} is not
 * thread-safe since it retains state information about partial messages.
 * For this reason, each {@link IOHandle} should have its own instance, and
 * {@link #filterReceive} should be called by only one thread.
 * {@link #filterSend}, however, is thread-safe.  
 * 
 * @author Sten Anderson
 * @since  1.0
 */
public class PassthroughFilter extends AbstractFilter {

    /** The total expected length of an "in progress" partial message */
    private int partialMessageLength;

    /** The current length of the partial message. */
    private int currentLength;

    /**
     * Default constructor.
     */
    public PassthroughFilter() {
        // empty
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply strips the length header from incoming
     * packets.
     */
    public void filterReceive(IOHandle handle, byte[] message) {
        index = 0;

        // This Mina ByteBuffer will hold the contents of the message bytes as
        // the lengths of the messages are extracted.  In the best case scenrio
        // exactly one message is contained in the incoming byte array, so
        // we initialize the buffer to the array size minus the length 
        // (as an int).
        ByteBuffer buffer = ByteBuffer.allocate(message.length - 4);
        buffer.setAutoExpand(true);

        // first check to see if we have an incomplete message from the
        // last time.  If so, we want to read the rest of the message
        // before reading another length.
        if (partialMessageLength > 0) {

            int numBytes = Math.min(message.length, partialMessageLength
                    - currentLength);

            buffer.put(message, 0, numBytes);

            index += numBytes;
            currentLength += numBytes;

            // the partial message is complete, reset the sizing information.
            if (currentLength == partialMessageLength) {
                partialMessageLength = 0;
                currentLength = 0;
            }

        }

        // now the next piece of array data is the size of the next message 
        int totalLength = readInt(message);

        // Continue to strip out message lengths in the array.  This covers
        // the case where multiple messages are sent in one byte array.
        while (totalLength > 0 && totalLength <= (message.length - index)) {
            buffer.put(message, index, totalLength);

            index += totalLength;
            totalLength = readInt(message);
        }

        // At this point, totalLength is set to the size of the next message
        // but there was some left over data in the array. We need to keep
        // around the total size of the "in progress" message, and the current
        // size for the next call to filterReceive.
        if ((message.length - index) > 0) {
            partialMessageLength = totalLength;
            currentLength = message.length - index;
            buffer.put(message, index, currentLength);
        }

        buffer.flip();
        byte[] byteMessage = new byte[buffer.remaining()];
        buffer.get(byteMessage);

        IOHandler handler = ((SocketHandle) handle).getIOHandler();
        if (handler != null) {
            handler.bytesReceived(byteMessage, handle);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation simply sends out each byte message without
     * modification.
     */
    public void filterSend(IOHandle handle, byte[] message) {
        ((SocketHandle) handle).doSend(message);
    }

}
