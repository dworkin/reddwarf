package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A filter that guarantees that only complete messages are delivered to the
 * client's {@link IOHandler}. That is, each call to
 * {@link IOHandle#sendBytes} results in exactly one call to
 * {@link IOHandler#bytesReceived}.
 * <p>
 * It prepends the message length on
 * sending, and reads the length of each message on receiving. It will fire
 * the {@link IOHandler#bytesReceived} callback once for each complete
 * message. If partial messages are received, this filter will hold the
 * partial message until the rest of the message is received, even if the
 * message spans multiple calls to {@link #filterReceive}.
 * <p>
 * The {@link #filterReceive} portion of this {@link IOFilter} is
 * not thread-safe since it retains state information about partial
 * messages. For this reason, each {@link IOHandle} should have its own
 * instance, and {@link #filterReceive} should be called by only one thread.
 * {@link #filterSend}, however, is thread-safe.
 *
 * @author Sten Anderson
 * @since  1.0
 */
public class CompleteMessageFilter extends AbstractFilter {

    /**
     * A reference to an "in progress" message. The length of the array will
     * be the final expected length of the message.
     */
    private byte[] partialMessage;

    /** The current length of the partial message. */
    private int currentLength;

    /**
     * Called each time the framework receives a bytes message. It will call
     * {@code IOHandler.bytesReceived} on the associated {@code IOHandle}
     * once for each complete message. The size of a message should be
     * encoded as the first four bytes. This method can be in one of a
     * number of states each time it is called:
     * <ul>
     * <li>It can be called with a new, incoming packet. In this case, the
     * length of the message is read as the first int, and that many byte
     * are read from the array and dispatched to the listener. This continues
     * until all the bytes are read from the array. If the array ends in the
     * middle of a message, the remaining bytes are held in a partial message
     * until the next call.</li>
     * <li>If there is a message in progress, then the
     * smaller of the rest of the message, or the length of the array is
     * added to the message. If the message is complete, it is dispatched to
     * the listener, otherwise it continues to be held for the next call.</li>
     * </ul>
     * 
     * @param handle the {@code IOHandle} receiving the data
     * @param message the data received
     */
    public void filterReceive(IOHandle handle, byte[] message) {
        index = 0;

        // first check to see if we have an incomplete message from the
        // last time.
        if (partialMessage != null) {

            int numBytes = Math.min(message.length, partialMessage.length
                    - currentLength);

            System.arraycopy(message, 0, partialMessage, currentLength,
                    numBytes);
            index += numBytes;
            currentLength += numBytes;

            // the partial message is complete, send it off to the listener
            // and reset the sizing information.
            if (currentLength == partialMessage.length) {
                ((SocketHandle) handle).getIOHandler().bytesReceived(
                        partialMessage, handle);
                partialMessage = null;
                currentLength = 0;
            }

        }

        // now the next piece of array data is the size of the next message
        int totalLength = readInt(message);

        // continue to notify the IOHandler of complete messages
        // as they appear in the array. This can be called 0 to n times.
        while (totalLength > 0 && totalLength <= (message.length - index)) {
            byte[] nextMessage = new byte[totalLength];
            System.arraycopy(message, index, nextMessage, 0,
                    nextMessage.length);
            IOHandler handler = ((SocketHandle) handle).getIOHandler();
            if (handler != null) {
                handler.bytesReceived(nextMessage, handle);
            }

            index += totalLength;
            totalLength = readInt(message);
        }

        // At this point, totalLength is set to the size of the next message
        // but there was some left over data in the array that will become
        // the partial message buffer of the next call to filterReceive.
        if ((message.length - index) > 0) {
            partialMessage = new byte[totalLength];
            currentLength = message.length - index;
            System.arraycopy(message, index, partialMessage, 0,
                    currentLength);
        }
    }

    /**
     * Sends the message on without modification on the given
     * {@link IOHandle}.
     * 
     * @param handle the {@code IOHandle} on which to send the data
     * @param message the data to send
     */
    public void filterSend(IOHandle handle, byte[] message) {
        ((SocketHandle) handle).doSend(message);
    }

}
