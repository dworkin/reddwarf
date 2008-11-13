/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.protocol.simple;

import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.protocol.CompletionFuture;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.session.SessionMessageChannel;
import com.sun.sgs.protocol.session.SessionMessageHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.transport.TransportDescriptor;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper channel that reads and writes complete messages by framing
 * messages with a 2-byte message length, and masking (and re-issuing) partial
 * I/O operations.  Also enforces a fixed buffer size when reading.
 */
public class SimpleSgsProtocolMessageChannel implements SessionMessageChannel {
    
    /** The number of bytes used to represent the message length. */
    public static final int PREFIX_LENGTH = 2;

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(SimpleSgsProtocolMessageChannel.class.getName()));

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...).
     */
    final AsynchronousMessageChannel asyncMsgChannel;

    /** This message channel's protocol impl. */
    final SimpleSgsProtocolImpl protocolImpl;

    /** The completion handler for reading from the I/O channel. */
    private volatile ReadHandler readHandler;

    /** The completion handler for writing to the I/O channel. */
    private volatile WriteHandler writeHandler = new ConnectedWriteHandler();

    /** A lock for {@code loggedIn} and {@code messageQueue} fields. */
    private Object lock = new Object();

    /** Indicates whether the client's login ack has been sent. */
    private boolean loginHandled = false;

    /** Messages enqueued to be sent after a login ack is sent. */
    private List<ByteBuffer> messageQueue = new ArrayList<ByteBuffer>();

    /**
     * Creates a new instance of this class with the given byte channel
     * and read buffer size.
     * 
     * @param	byteChannel a byte channel
     * @param	protocolImpl protocol impl
     * @param	readBufferSize the number of bytes in the read buffer
     */
    SimpleSgsProtocolMessageChannel(AsynchronousByteChannel byteChannel,
                                           SimpleSgsProtocolImpl protocolImpl,
                                           int readBufferSize)
    {
	// The read buffer size lower bound is enforced by the protocol factory
	assert readBufferSize >= PREFIX_LENGTH;
	this.asyncMsgChannel =
	    new AsynchronousMessageChannel(byteChannel, readBufferSize);
	this.protocolImpl = protocolImpl;
    }

    void setHandler(SessionMessageHandler handler) {
        readHandler = new ConnectedReadHandler(handler);
        
        /*
	 * TBD: It might be a good idea to implement high- and low-water marks
	 * for the buffers, so they don't go into hysteresis when they get
	 * full. -JM
	 */
	scheduleReadOnReadHandler();
    }
    
    /* -- Implement ProtocolMessageChannel -- */

    /** {@inheritDoc} */
    @Override
    public void loginRedirect(ProtocolDescriptor newListener) {
        TransportDescriptor transportDesc = newListener.getTransport();
	int hostStringSize = MessageBuffer.getSize(transportDesc.getHostName());
	MessageBuffer buf = new MessageBuffer(1 + hostStringSize + 4);
        buf.putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
            putString(transportDesc.getHostName()).
            putInt(transportDesc.getListeningPort());
	writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
    }

    /** {@inheritDoc} */
    @Override
    public void loginSuccess(BigInteger sessionId) {
	// FIXME: currently we choose the reconnect key to be
	// the session ID, to facilitate the test of the Channel Service.
	// If the reconnect key is generated some other way, the test
	// will have to be updated to get the session key some other way.
	byte[] reconnectKey = sessionId.toByteArray();
	MessageBuffer buf = new MessageBuffer(1 + reconnectKey.length);
	buf.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
	    putBytes(reconnectKey);
	writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
    }

    /** {@inheritDoc} */
    @Override
    public void loginFailure(String reason, Throwable throwable) {
        int stringSize = MessageBuffer.getSize(reason);
        MessageBuffer buf = new MessageBuffer(1 + stringSize);
        buf.putByte(SimpleSgsProtocol.LOGIN_FAILURE).
            putString("login refused");
        writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
    }

    /** {@inheritDoc} */
    @Override
    public void sessionMessage(ByteBuffer message) {
	ByteBuffer buf = ByteBuffer.wrap(new byte[1 + message.remaining()]);
	buf.put(SimpleSgsProtocol.SESSION_MESSAGE).
	    put(message).
	    flip();
	writeOrEnqueueIfLoginNotHandled(buf);
    }

    /** {@inheritDoc} */
    @Override
    public void channelJoin(String name, BigInteger channelId) {
	byte[] channelIdBytes = channelId.toByteArray();
	MessageBuffer buf =
	    new MessageBuffer(1 + MessageBuffer.getSize(name) +
			      channelIdBytes.length);
	buf.putByte(SimpleSgsProtocol.CHANNEL_JOIN).
	    putString(name).
	    putBytes(channelIdBytes);
	writeOrEnqueueIfLoginNotHandled(ByteBuffer.wrap(buf.getBuffer()));
    }

    /** {@inheritDoc} */
    @Override
    public void channelLeave(BigInteger channelId) {
	byte[] channelIdBytes = channelId.toByteArray();
	ByteBuffer buf =
	    ByteBuffer.allocate(1 + channelIdBytes.length);
	buf.put(SimpleSgsProtocol.CHANNEL_LEAVE).
	    put(channelIdBytes).
	    flip();
	writeOrEnqueueIfLoginNotHandled(buf);
    }

    /** {@inheritDoc} */
    @Override
    public void channelMessage(BigInteger channelId, ByteBuffer message) {
	byte[] channelIdBytes = channelId.toByteArray();
	ByteBuffer buf =
	    ByteBuffer.allocate(3 + channelIdBytes.length +
				message.remaining());
	buf.put(SimpleSgsProtocol.CHANNEL_MESSAGE).
	    putShort((short) channelIdBytes.length).
	    put(channelIdBytes).
	    put(message).
	    flip();
	writeOrEnqueueIfLoginNotHandled(buf);
    }

    /** {@inheritDoc} */
    @Override
    public void logoutSuccess() {
	ByteBuffer buf = ByteBuffer.allocate(1);
	buf.put(SimpleSgsProtocol.LOGOUT_SUCCESS).
	    flip();
	writeToWriteHandler(buf);
    }
    
    /* -- Implement Channel -- */

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        asyncMsgChannel.close();
	readHandler = new ClosedReadHandler();
        writeHandler = new ClosedWriteHandler();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return asyncMsgChannel.isOpen();
    }
    
    /* -- Methods for reading and writing -- */
    
    /**
     * Schedules an asynchronous task to resume reading.
     */
    private void scheduleReadOnReadHandler() {
	protocolImpl.scheduleNonTransactionalTask(
            new AbstractKernelRunnable() {
//	    new AbstractKernelRunnable("ResumeReadOnReadHandler") {
		public void run() {
		    logger.log(
			Level.FINER, "resuming reads channel:{0}", this);
		    if (isOpen()) {
			readHandler.read();
		    }
		} });
    }

    /**
     * Writes a message to the write handler if login has not handled yet,
     * otherwise enqueues the message to be sent when the login has been
     * handled.
     *
     * @param	buf a buffer containing a complete protocol message
     */
    private void writeOrEnqueueIfLoginNotHandled(ByteBuffer buf) {
	synchronized (lock) {
	    if (!loginHandled) {
		messageQueue.add(buf);
	    } else {
		writeToWriteHandler(buf);
	    }
	}
    }
    
    /**
     * Writes a message to the write handler.
     *
     * @param	buf a buffer containing a complete protocol message
     */
    private void writeToWriteHandler(ByteBuffer message) {
	try {
	    writeHandler.write(message);
		    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "sendProtocolMessage session:{0} throws", this);
	    }
	}
    }

    /**
     * Writes all enqueued messages to the write handler.
     */
    private void flushMessageQueue() {
	synchronized (lock) {
	    loginHandled = true;
	    for (ByteBuffer nextMessage : messageQueue) {
		writeToWriteHandler(nextMessage);
	    }
	    messageQueue.clear();
	}
    }
    /* -- I/O completion handlers -- */

    /** A completion handler for writing to a connection. */
    private abstract class WriteHandler
        implements CompletionHandler<Void, Void>
    {
	/** Writes the specified message. */
        abstract void write(ByteBuffer message);
    }

    /** A completion handler for writing that always fails. */
    private class ClosedWriteHandler extends WriteHandler {

	ClosedWriteHandler() { }

        @Override
        void write(ByteBuffer message) {
            throw new ClosedAsynchronousChannelException();
        }
        
        @Override
        public void completed(IoFuture<Void, Void> result) {
            throw new AssertionError("should be unreachable");
        }    
    }

    /** A completion handler for writing to the session's channel. */
    private class ConnectedWriteHandler extends WriteHandler {

	/** The lock for accessing the fields {@code pendingWrites} and
	 * {@code isWriting}. The locks {@code lock} and {@code writeLock}
	 * should only be acquired in that specified order.
	 */
	private final Object writeLock = new Object();
	
	/** An unbounded queue of messages waiting to be written. */
        private final LinkedList<ByteBuffer> pendingWrites =
            new LinkedList<ByteBuffer>();

	/** Whether a write is underway. */
        private boolean isWriting = false;

	/** Creates an instance of this class. */
        ConnectedWriteHandler() { }

	/**
	 * Adds the message to the queue, and starts processing the queue if
	 * needed.
	 */
        @Override
        void write(ByteBuffer message) {
            if (message.remaining() > SimpleSgsProtocol.MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException(
                    "message too long: " + message.remaining() + " > " +
                        SimpleSgsProtocol.MAX_PAYLOAD_LENGTH);
            }
            boolean first;
            synchronized (writeLock) {
                first = pendingWrites.isEmpty();
                pendingWrites.add(message);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
			   "write channel:{0} message:{1} first:{2}",
                           SimpleSgsProtocolMessageChannel.this,
			   HexDumper.format(message, 0x50), first);
            }
            if (first) {
                processQueue();
            }
        }

	/** Start processing the first element of the queue, if present. */
        private void processQueue() {
            ByteBuffer message;
            synchronized (writeLock) {
                if (isWriting) {
                    return;
		}
                message = pendingWrites.peek();
                if (message == null) {
		    return;
		}
		isWriting = true;
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(
		    Level.FINEST,
		    "processQueue channel:{0} size:{1,number,#} head={2}",
		    SimpleSgsProtocolMessageChannel.this, pendingWrites.size(),
		    HexDumper.format(message, 0x50));
            }
            try {
                asyncMsgChannel.write(message, this);
            } catch (RuntimeException e) {
                logger.logThrow(Level.SEVERE, e,
				"{0} processing message {1}",
				SimpleSgsProtocolMessageChannel.this,
				HexDumper.format(message, 0x50));
                throw e;
            }
        }

	/** Done writing the first request in the queue. */
        @Override
        public void completed(IoFuture<Void, Void> result) {
	    ByteBuffer message;
            synchronized (writeLock) {
                message = pendingWrites.remove();
                isWriting = false;
            }
            if (logger.isLoggable(Level.FINEST)) {
		ByteBuffer resetMessage = message.duplicate();
		resetMessage.reset();
                logger.log(Level.FINEST,
			   "completed write session:{0} message:{1}",
			   SimpleSgsProtocolMessageChannel.this,
			   HexDumper.format(resetMessage, 0x50));
            }
            try {
                result.getNow();
                /* Keep writing */
                processQueue();
            } catch (ExecutionException e) {
                /*
		 * TBD: If we're expecting the session to close, don't
                 * complain.
		 */
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, e,
				    "write session:{0} message:{1} throws",
				    SimpleSgsProtocolMessageChannel.this,
				    HexDumper.format(message, 0x50));
                }
//		handler.disconnect(null);
            }
        }
    }

    /** A completion handler for reading from a connection. */
    private abstract class ReadHandler
        implements CompletionHandler<ByteBuffer, Void>
    {
	/** Initiates the read request. */
        abstract void read();
    }

    /** A completion handler for reading that always fails. */
    private class ClosedReadHandler extends ReadHandler {

	ClosedReadHandler() { }

        @Override
        void read() {
            throw new ClosedAsynchronousChannelException();
        }

        @Override
        public void completed(IoFuture<ByteBuffer, Void> result) {
            throw new AssertionError("should be unreachable");
        }
    }

    /** A completion handler for reading from the session's channel. */
    private class ConnectedReadHandler extends ReadHandler {

        /** The protocol message handler. */
        final SessionMessageHandler handler;
    
	/** The lock for accessing the {@code isReading} field. The locks
	 * {@code lock} and {@code readLock} should only be acquired in
	 * that specified order.
	 */
	private final Object readLock = new Object();

	/** Whether a read is underway. */
        private boolean isReading = false;

	/** Creates an instance of this class. */
        ConnectedReadHandler(SessionMessageHandler handler) {
            this.handler = handler;
        }

	/** Reads a message from the connection. */
        @Override
        void read() {
            synchronized (readLock) {
                if (isReading) {
                    throw new ReadPendingException();
		}
                isReading = true;
            }
            asyncMsgChannel.read(this);
        }

	/** Handles the completed read operation. */
        @Override
        public void completed(IoFuture<ByteBuffer, Void> result) {
            synchronized (readLock) {
                isReading = false;
            }
            try {
                ByteBuffer message = result.getNow();
                if (message == null) {
                    handler.disconnect();   // ignore future
                    return;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                               "completed read channel:{0} message:{1}",
                               SimpleSgsProtocolMessageChannel.this,
                               HexDumper.format(message, 0x50));
                }

                byte[] payload = new byte[message.remaining()];
                message.get(payload);

                // Dispatch
                bytesReceived(payload);

            } catch (Exception e) {

                /*
		 * TBD: If we're expecting the channel to close, don't
                 * complain.
		 */
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(
                        Level.FINE, e,
                        "Read completion exception {0}", asyncMsgChannel);
                }
                handler.disconnect();   // ignore future
            }
        }

	/** Processes the received message. */
        private void bytesReceived(byte[] buffer) {

	    MessageBuffer msg = new MessageBuffer(buffer);
	    byte opcode = msg.getByte();

	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
                           "processing opcode 0x{0}",
                           Integer.toHexString(opcode));
	    }
	    
	    switch (opcode) {
		
	    case SimpleSgsProtocol.LOGIN_REQUEST:

	        byte version = msg.getByte();
	        if (version != SimpleSgsProtocol.VERSION) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                           "got protocol version:{0}, expected {1}",
                                   version, SimpleSgsProtocol.VERSION);
	            }
		    handler.disconnect();   // ignore future
	            break;
	        }

		final String name = msg.getString();
		final String password = msg.getString();
		handler.loginRequest(name, password);   // ignore future
                // Resume reading immediately
		read();

		break;
		
	    case SimpleSgsProtocol.SESSION_MESSAGE:
		ByteBuffer clientMessage =
		    ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
                CompletionFuture sessionMessageFuture =
                                handler.sessionMessage(clientMessage);

		// Wait for session message to be processed before
		// resuming reading.
		try {
		    sessionMessageFuture.get();
		} catch (InterruptedException ignore) {
		} catch (ExecutionException e) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(Level.FINE, e,
                                        "Processing session message:{0} " +
                                        "for protocol:{1} throws",
                                        HexDumper.format(clientMessage, 0x50),
                                        SimpleSgsProtocolMessageChannel.this);
		    }
		}
		read();

		break;

	    case SimpleSgsProtocol.CHANNEL_MESSAGE:
		BigInteger channelRefId =
		    new BigInteger(1, msg.getBytes(msg.getShort()));
		ByteBuffer channelMessage =
		    ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
                CompletionFuture channelMessageFuture =
                        handler.channelMessage(channelRefId, channelMessage);

		// Wait for channel message to be processed before
		// resuming reading.
		try {
		    channelMessageFuture.get();
		} catch (InterruptedException ignore) {
		} catch (ExecutionException e) {
		    if (logger.isLoggable(Level.FINE)) {
			logger.logThrow(Level.FINE, e,
                                        "Processing channel message:{0} " +
                                        "for protocol:{1} throws",
                                        HexDumper.format(channelMessage, 0x50),
                                        SimpleSgsProtocolMessageChannel.this);
		    }
                }
                read();
		
		break;

	    case SimpleSgsProtocol.LOGOUT_REQUEST:
		handler.logoutRequest();    // ignore future
		// Resume reading immediately
                read();

		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(Level.SEVERE,
			       "unknown opcode 0x{0}",
			       Integer.toHexString(opcode));
		}
		handler.disconnect();   // ignore future
		break;
	    }
	}
    }
}
