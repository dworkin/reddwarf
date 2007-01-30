package com.sun.sgs.impl.io;

import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

// TODO move this functionality into protocol decode; we should
// do framing in the protocol, not the transport. -JM

/**
 * This filter guarantees that only complete messages are delivered to this
 * connection's {@link ConnectionListener}. That is, each call to
 * {@code sendBytes} by the sender results in exactly one call to
 * {@code bytesReceived} on the receiver's {@code ConnectionListener}.
 * <p>
 * It prepends the message length on sending, and reads the length of each
 * message on receiving. It will fire the {@code bytesReceived} callback on
 * the {@code ConnectionListener} once for each complete message. If partial
 * messages are received, this filter will hold the partial message until
 * the rest of the message is received, even if the message spans multiple
 * calls to {@code filterReceive}.
 * <p>
 * The {@code filterReceive} portion of this filter is not thread-safe since
 * it retains state information about partial messages. For this reason,
 * each {@code Connection} should have its own instance, and
 * {@code filterReceive} should be called by only one thread.
 * {@code filterSend}, however, is thread-safe.
 */
class CompleteMessageFilter {

    /** The offset of the array that is currently being processed. */
    private int index;

    /**
     * A partial message still waiting for its remaining data.
     * The length of the array is the final expected length of the message.
     */
    private byte[] partialMessage;

    /** The current length of the partial message. */
    private int partialLength;

    /**
     * Default constructor.
     */
    CompleteMessageFilter() {
        // empty
    }

    /**
     * Invoked as messages are received by the {@code Connection} before
     * dispatching to its {@code ConnectionListener}. The filter is free to
     * modify the incoming data in any way, and to invoke
     * {@link ConnectionListener#bytesReceived} once, multiple times, = or
     * not at all.
     * <p>
     * This implementation will call {@code bytesReceived} on the associated
     * {@code ConnectionListener} once for each complete message. The size
     * of a message must be encoded as the first four bytes. This method can
     * be in one of a number of states each time it is called:
     * <ul>
     * <li>It can be called with a new, incoming packet. In this case, the
     * length of the message is read as the first int, and that many byte
     * are read from the array and dispatched to the listener. This
     * continues until all the bytes are read from the array. If the array
     * ends in the middle of a message, the remaining bytes are held in a
     * partial message until the next call.</li>
     * <li>If there is a message in progress, then the smaller of the rest
     * of the message, or the length of the array is added to the message.
     * If the message is complete, it is dispatched to the listener,
     * otherwise it continues to be held for the next call.</li>
     * </ul>
     * 
     * @param conn the {@link Connection} which received the data
     * @param message the data to filter and optionally deliver to the
     *        {@code ConnectionListener} for the given connection
     */
    void filterReceive(Connection conn, byte[] message) {
        index = 0;

        ConnectionListener listener = ((SocketConnection) conn).getConnectionListener();

        // first check to see if we have an incomplete message from the
        // last time.
        if (partialMessage != null) {
            int capacity = partialMessage.length - partialLength;
            int remaining = Math.min(message.length, capacity);

            System.arraycopy(
                    message, 0, partialMessage, partialLength, remaining);
            index += remaining;
            partialLength += remaining;

            // the partial message is complete, send it off to the listener
            // and reset the sizing information.
            if (partialLength == partialMessage.length) {
                listener.bytesReceived(conn, partialMessage);
                partialMessage = null;
                partialLength = 0;
            }
        }

        // now the next piece of array data is the size of the next message
        int totalLength = readInt(message);

        // continue to notify the ConnectionListener of complete messages
        // as they appear in the array. This can be called 0 to n times.
        while (totalLength > 0 && totalLength <= (message.length - index)) {
            byte[] nextMessage = new byte[totalLength];
            System.arraycopy(
                    message, index, nextMessage, 0, nextMessage.length);
            listener.bytesReceived(conn, nextMessage);

            index += totalLength;
            totalLength = readInt(message);
        }

        // At this point, totalLength is set to the size of the next message
        // but there was some left over data in the array that will become
        // the partial message buffer of the next call to filterReceive.
        if ((message.length - index) > 0) {
            partialMessage = new byte[totalLength];
            partialLength = message.length - index;
            System.arraycopy(
                    message, index, partialMessage, 0, partialLength);
        }
    }

    /**
     * Invoked after a caller has requested data be sent on the associated
     * {@code Connection}. The filter may modify the data in any way, and may
     * send it on the underlying connection whole, in pieces, or not at all.
     * <p>
     * This filter sends the message without modification on
     * the {@code Connection}.
     *
     * @param conn the {@link Connection} on which to send the data
     * @param message the data to filter and optionally send on the
     *        connection represented by the given connection
     */
    void filterSend(Connection conn, byte[] message) {
        ((SocketConnection) conn).doSend(message);
    }

    /**
     * Reads the next four bytes of the given array starting at the current
     * index and assembles them into a network byte-ordered int. It will
     * increment the index by four if the read was successful. It will
     * return zero if not enough bytes remain in the array.
     *
     * @param array the array from which to read bytes
     *
     * @return the next four bytes as an int, or zero if there aren't enough
     *         bytes remaining in the array
     */
    private int readInt(byte[] array) {
        if (array.length < (index + 4)) {
            return 0;
        }
        int num =
              ((array[index]     & 0xFF) << 24) |
              ((array[index + 1] & 0xFF) << 16) |
              ((array[index + 2] & 0xFF) <<  8) |
               (array[index + 3] & 0xFF);
        
        index += 4;
        
        return num;
    }
}
