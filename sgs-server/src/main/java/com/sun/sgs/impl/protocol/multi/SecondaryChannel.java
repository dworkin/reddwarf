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

package com.sun.sgs.impl.protocol.multi;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.protocol.simple.AsynchronousMessageChannel;
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
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper channel that reads and writes complete messages by framing
 * messages with a 2-byte message length, and masking (and re-issuing) partial
 * I/O operations.  Also enforces a fixed buffer size when reading.
 */
class SecondaryChannel implements Channel {
    
    /** The number of bytes used to represent the message length. */
    public static final int PREFIX_LENGTH = 2;

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(SecondaryChannel.class.getName()));

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...).
     */
    final AsynchronousMessageChannel asyncMsgChannel;

    /** This message channel's protocol impl. */
    final MultiSgsProtocolAcceptor acceptor;

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

    private final Set<Delivery> supportedDelivery;
    
    /**
     * Creates a new instance of this class with the given byte channel
     * and read buffer size.
     * 
     * @param	byteChannel a byte channel
     * @param	protocolImpl protocol impl
     * @param	readBufferSize the number of bytes in the read buffer
     */
    SecondaryChannel(Set<Delivery> supportedDelivery,
                     MultiSgsProtocolAcceptor acceptor,
                     AsynchronousByteChannel byteChannel,
                     int readBufferSize)
    {
	// The read buffer size lower bound is enforced by the protocol factory
	assert readBufferSize >= PREFIX_LENGTH;
	this.supportedDelivery = supportedDelivery;
        this.acceptor = acceptor;
        asyncMsgChannel =
	    new AsynchronousMessageChannel(byteChannel, readBufferSize);
    
        readHandler = new ConnectedReadHandler();
        
	scheduleReadOnReadHandler();
    }
    
    private void reconnectFailure(String reason, Throwable throwable) {
        int stringSize = MessageBuffer.getSize(reason);
        MessageBuffer buf = new MessageBuffer(1 + stringSize);
        buf.putByte(SimpleSgsProtocol.RECONNECT_FAILURE).
            putString(reason);
        writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
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
    
    void disconnect() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "disconnecting secondary connection");
        }
        try {
            close();
        } catch (IOException ignore) {}
    }
    
    /* -- Methods for reading and writing -- */
    
    /**
     * Schedules an asynchronous task to resume reading.
     */
    private void scheduleReadOnReadHandler() {
	acceptor.scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("ResumeReadOnReadHandler") {
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
    void writeOrEnqueueIfLoginNotHandled(ByteBuffer buf) {
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
                           SecondaryChannel.this,
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
		    SecondaryChannel.this, pendingWrites.size(),
		    HexDumper.format(message, 0x50));
            }
            try {
                asyncMsgChannel.write(message, this);
            } catch (RuntimeException e) {
                logger.logThrow(Level.SEVERE, e,
				"{0} processing message {1}",
				SecondaryChannel.this,
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
			   SecondaryChannel.this,
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
				    SecondaryChannel.this,
				    HexDumper.format(message, 0x50));
                }
		disconnect();
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
        volatile SessionProtocolHandler handler;
    
	/** The lock for accessing the {@code isReading} field. The locks
	 * {@code lock} and {@code readLock} should only be acquired in
	 * that specified order.
	 */
	private final Object readLock = new Object();

	/** Whether a read is underway. */
        private boolean isReading = false;

	/** Creates an instance of this class. */
        ConnectedReadHandler() {}

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
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Read returned null message");
                    }
                    disconnect();
                    return;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                               "completed read channel:{0} message:{1}",
                               SecondaryChannel.this,
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
                disconnect();
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
		
	    case SimpleSgsProtocol.RECONNECT_REQUEST:

	        byte version = msg.getByte();
	        if (version != SimpleSgsProtocol.VERSION) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                           "got protocol version:{0}, expected {1}",
                                   version, SimpleSgsProtocol.VERSION);
	            }
		    disconnect();
	            break;
	        }

                byte[] key = msg.getBytes(msg.limit() - msg.position());
                MultiSgsProtocolImpl protocol = acceptor.attach(key);
                
		if (protocol != null) {
                    handler = protocol.attach(SecondaryChannel.this,
                                              supportedDelivery);
                }
                
                if (handler == null) {
                    reconnectFailure("authentication failed", null);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                   "Authentication failed for key {0}", key);
                    }
                    disconnect();
                    break;
                }
                
		// Resume reading immediately
                read();
                break;

	    case SimpleSgsProtocol.CHANNEL_MESSAGE:
                if (handler != null) {
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
                                            SecondaryChannel.this);
                        }
                    }
                }
                read();
		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(Level.SEVERE,
			       "unknown opcode 0x{0}",
			       Integer.toHexString(opcode));
		}
		disconnect();
		break;
	    }
	}
    }
}
