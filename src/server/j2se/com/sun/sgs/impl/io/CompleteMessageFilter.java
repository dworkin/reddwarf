package com.sun.sgs.impl.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;

import com.sun.sgs.impl.util.LoggerWrapper;
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

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
            CompleteMessageFilter.class.getName()));

    /** The default recv processing buffer size. */
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /** The largest we expect the recv processing buffer size to get. */
    private static final int MAX_BUFFER_SIZE = 512 * 1024;

    /** The largest message we reasonably expect to recv. */
    private static final int MAX_MSG_SIZE = 128 * 1024;

    /** The data being processed, or a partial message awaiting more data. */
    private final ByteBuffer msgBuf;

    /**
     * Default constructor.
     */
    CompleteMessageFilter() {
        msgBuf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE, false);
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
     * length of the message is read as the first int, and that many bytes
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
     * @param buf the data to filter and optionally deliver to the
     *        {@code ConnectionListener} for the given connection
     */
    void filterReceive(Connection conn, ByteBuffer buf) {

        logger.log(Level.FINEST,
            "processing {0,number,#} bytes",
            buf.remaining());

        // Append the new data to the buffer
        msgBuf.expand(buf.remaining());
        msgBuf.put(buf);
        msgBuf.flip();

        processReceiveBuffer(conn);
    }

    private void processReceiveBuffer(Connection conn) {
        ConnectionListener listener =
            ((SocketConnection) conn).getConnectionListener();

        if (msgBuf.remaining() > MAX_BUFFER_SIZE) {
            logger.log(Level.WARNING,
                "Recv filter buffer is larger than expected: {0}",
                msgBuf.remaining());
        }

        // Process complete messages, if any
        while (msgBuf.hasRemaining()) {
            if (! msgBuf.prefixedDataAvailable(4, MAX_MSG_SIZE))
                break;

            int msgLen = msgBuf.getInt();

            if (msgLen > MAX_MSG_SIZE) {
                logger.log(Level.WARNING,
                    "Recv message is larger than expected: {0}",
                    msgLen);
            }

            byte[] completeMessage = new byte[msgLen];
            msgBuf.get(completeMessage);

            logger.log(Level.FINER,
                "dispatching complete message of size {0,number,#}",
                msgLen);

            listener.bytesReceived(conn, completeMessage);
        }

        msgBuf.compact();

        logger.log(Level.FINEST,
            "partial message {0,number,#} bytes",
            msgBuf.position());
    }

    /**
     * Invoked after a caller has requested data be sent on the associated
     * {@code Connection}. The filter may modify the data in any way, and may
     * send it on the underlying connection whole, in pieces, or not at all.
     * <p>
     * This implementation prepends the length of the given byte array as
     * a 4-byte {@code int} in network byte-order, and sends it out on
     * the underlying MINA {@code IoSession}.
     *
     * @param conn the {@link Connection} on which to send the data
     * @param message the data to filter and optionally send on the
     *        connection represented by the given connection
     */
    void filterSend(Connection conn, byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(message.length + 4);
        buffer.putInt(message.length);
        buffer.put(message);
        buffer.flip();
        ((SocketConnection) conn).doSend(buffer);
    }
}
