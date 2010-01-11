/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.protocol.simple;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.LoginRedirectException;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RequestFailureException;
import com.sun.sgs.protocol.RequestFailureException.FailureReason;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the protocol specified in {@code SimpleSgsProtocol}.  The
 * implementation uses a wrapper channel, {@link AsynchronousMessageChannel},
 * that reads and writes complete messages by framing messages with a 2-byte
 * message length, and masking (and re-issuing) partial I/O operations.  Also
 * enforces a fixed buffer size when reading.
 */
public class SimpleSgsProtocolImpl implements SessionProtocol {

    /** The protocol version for this implementation. */
    private static final byte PROTOCOL4 = 0x04;
    
   /** The number of bytes used to represent the message length. */
    private static final int PREFIX_LENGTH = 2;

    /** The logger for this class. */
    private static final LoggerWrapper staticLogger = new LoggerWrapper(
	Logger.getLogger(SimpleSgsProtocolImpl.class.getName()));

    /** The default reason string returned for login failure. */
    private static final String DEFAULT_LOGIN_FAILED_REASON = "login refused";

    /** The default length of the reconnect key, in bytes.
     * TBD: the reconnection key length should be configurable.
     */
    private static final int DEFAULT_RECONNECT_KEY_LENGTH = 16;

    /** A random number generator for reconnect keys. */
    private static final SecureRandom random = new SecureRandom();

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...).
     */
    private final AsynchronousMessageChannel asyncMsgChannel;

    /** The protocol handler. */
    protected volatile SessionProtocolHandler protocolHandler;

    /** This protocol's acceptor. */
    protected final SimpleSgsProtocolAcceptor acceptor;

    /** The logger for this instance. */
    protected final LoggerWrapper logger;
    
   /** The protocol listener. */
    protected final ProtocolListener listener;

    /** The identity. */
    private volatile Identity identity;

    /** The reconnect key. */
    protected final byte[] reconnectKey;

    /** The completion handler for reading from the I/O channel. */
    private volatile ReadHandler readHandler = new ConnectedReadHandler();

    /** The completion handler for writing to the I/O channel. */
    private volatile WriteHandler writeHandler = new ConnectedWriteHandler();

    /** A lock for {@code loginHandled} and {@code messageQueue} fields. */
    private final Object lock = new Object();

    /** Indicates whether the client's login ack has been sent. */
    private boolean loginHandled = false;

    /** Messages enqueued to be sent after a login ack is sent. */
    private List<ByteBuffer> messageQueue = new ArrayList<ByteBuffer>();

    /** The set of supported delivery requirements. */
    protected final Set<Delivery> deliverySet = new HashSet<Delivery>();

    /**
     * Creates a new instance of this class.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     */
    SimpleSgsProtocolImpl(ProtocolListener listener,
                          SimpleSgsProtocolAcceptor acceptor,
                          AsynchronousByteChannel byteChannel,
                          int readBufferSize)
    {
	this(listener, acceptor, byteChannel, readBufferSize, staticLogger);
	/*
	 * TBD: It might be a good idea to implement high- and low-water marks
	 * for the buffers, so they don't go into hysteresis when they get
	 * full. -JM
	 */
	scheduleRead();
    }
    
    /**
     * Constructs a new instance of this class.  The subclass should invoke
     * {@code scheduleRead} after constructing the instance to commence
     * reading.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     * @param	logger a logger for this instance
     */
    protected  SimpleSgsProtocolImpl(ProtocolListener listener,
				     SimpleSgsProtocolAcceptor acceptor,
				     AsynchronousByteChannel byteChannel,
				     int readBufferSize,
				     LoggerWrapper logger)
    {
	// The read buffer size lower bound is enforced by the protocol acceptor
	assert readBufferSize >= PREFIX_LENGTH;
	this.asyncMsgChannel =
	    new AsynchronousMessageChannel(byteChannel, readBufferSize);
	this.listener = listener;
	this.acceptor = acceptor;
	this.logger = logger;
	this.reconnectKey = getNextReconnectKey();
	deliverySet.add(Delivery.RELIABLE);
    }

    /**
     * Returns the {@code SimpleSgsProtocol} version supported by this
     * implementation.
     *
     * @return the {@code SimpleSgsProtocol} version supported by this
     * implementation
     */
    protected byte getProtocolVersion() {
	return PROTOCOL4;
    }

    /**
     * Returns the associated identity, or {@code null} if the client has
     * not yet authenticated.
     *
     * @return the associated identity, or {@code null}
     */
    protected Identity getIdentity() {
	return identity;
    }
    
    /* -- Implement SessionProtocol -- */

    /** {@inheritDoc} */
    public Set<Delivery> getDeliveries() {
	return Collections.unmodifiableSet(deliverySet);
    }
    
    /** {@inheritDoc} */
    public int getMaxMessageLength() {
        // largest message size is max for channel messages
        return
	    SimpleSgsProtocol.MAX_MESSAGE_LENGTH -
	    1 -           // Opcode
	    2 -           // channel ID size
	    8;            // (max) channel ID bytes
    }
    
    /** {@inheritDoc} */
    public void sessionMessage(ByteBuffer message, Delivery delivery) {
	int messageLength = 1 + message.remaining();
        assert messageLength <= SimpleSgsProtocol.MAX_MESSAGE_LENGTH;
	ByteBuffer buf = ByteBuffer.wrap(new byte[messageLength]);
	buf.put(SimpleSgsProtocol.SESSION_MESSAGE).
	    put(message).
	    flip();
	writeBuffer(buf, delivery);
    }
    
    /** {@inheritDoc} */
    public void channelJoin(
	String name, BigInteger channelId, Delivery delivery) {
	byte[] channelIdBytes = channelId.toByteArray();
	MessageBuffer buf =
	    new MessageBuffer(1 + MessageBuffer.getSize(name) +
			      channelIdBytes.length);
	buf.putByte(SimpleSgsProtocol.CHANNEL_JOIN).
	    putString(name).
	    putBytes(channelIdBytes);
	write(ByteBuffer.wrap(buf.getBuffer()));
    }

    /** {@inheritDoc} */
    public void channelLeave(BigInteger channelId) {
	byte[] channelIdBytes = channelId.toByteArray();
	ByteBuffer buf =
	    ByteBuffer.allocate(1 + channelIdBytes.length);
	buf.put(SimpleSgsProtocol.CHANNEL_LEAVE).
	    put(channelIdBytes).
	    flip();
	write(buf);
    }

    /***
     * {@inheritDoc}
     *
     * <p>This implementation invokes the protected method {@link
     * #writeBuffer writeBuffer} with the channel protocol message (a
     * {@code ByteBuffer}) and the specified delivery requirement.  A
     * subclass can override the {@code writeBuffer} method if it supports
     * other delivery guarantees and can make use of alternate transports
     * for those other delivery requirements.
     */
    public void channelMessage(BigInteger channelId,
                               ByteBuffer message,
                               Delivery delivery)
    {
	byte[] channelIdBytes = channelId.toByteArray();
	int messageLength = 3 + channelIdBytes.length + message.remaining();
        assert messageLength <= SimpleSgsProtocol.MAX_MESSAGE_LENGTH;
	ByteBuffer buf =
	    ByteBuffer.allocate(messageLength);
	buf.put(SimpleSgsProtocol.CHANNEL_MESSAGE).
	    putShort((short) channelIdBytes.length).
	    put(channelIdBytes).
	    put(message).
	    flip();
	writeBuffer(buf, delivery);
    }

    /** {@inheritDoc} */
    public void disconnect(DisconnectReason reason) throws IOException {
	// TBD: The SimpleSgsProtocol does not yet support sending a
	// message to the client in the case of session termination or
	// preemption, so just close the connection for now.
        close();
    }
    
    /* -- Private methods for sending protocol messages -- */

    /**
     * Notifies the associated client that the previous login attempt was
     * successful.
     */
    protected void loginSuccess() {
	MessageBuffer buf = new MessageBuffer(1 + reconnectKey.length);
	buf.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
	    putBytes(reconnectKey);
	writeNow(ByteBuffer.wrap(buf.getBuffer()), true);
    }

    /**
     * Notifies the associated client that it should redirect its login to
     * the specified {@code node} with the specified protocol {@code
     * descriptors}.
     *
     * @param	nodeId the ID of the node to redirect the login
     * @param	descriptors a set of protocol descriptors supported
     *		by {@code node}
     */
    private void loginRedirect(
	long nodeId, Set<ProtocolDescriptor> descriptors)
    {
        for (ProtocolDescriptor descriptor : descriptors) {
            if (acceptor.getDescriptor().supportsProtocol(descriptor)) {
		byte[] redirectionData =
		    ((SimpleSgsProtocolDescriptor) descriptor).
		        getConnectionData();
		MessageBuffer buf =
		    new MessageBuffer(1 + redirectionData.length);
		buf.putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
		    putBytes(redirectionData);
		writeNow(ByteBuffer.wrap(buf.getBuffer()), true);
		monitorDisconnection();
                return;
            }
        }
        loginFailure("redirect failed", null);
        logger.log(Level.SEVERE,
                   "redirect node {0} does not support a compatable protocol",
                   nodeId);
    }

    /**
     * Notifies the associated client that the previous login attempt was
     * unsuccessful for the specified {@code reason}.  The specified {@code
     * throwable}, if non-{@code null} is an exception that occurred while
     * processing the login request.  The message channel should be careful
     * not to reveal to the associated client sensitive data that may be
     * present in the specified {@code throwable}.
     *
     * @param	reason a reason why the login was unsuccessful
     * @param	throwable an exception that occurred while processing the
     *		login request, or {@code null}
     */
    private void loginFailure(String reason, Throwable ignore) {
	// for now, override specified reason.
	reason = DEFAULT_LOGIN_FAILED_REASON;
        MessageBuffer buf =
	    new MessageBuffer(1 + MessageBuffer.getSize(reason));
        buf.putByte(SimpleSgsProtocol.LOGIN_FAILURE).
            putString(reason);
        writeNow(ByteBuffer.wrap(buf.getBuffer()), true);
	monitorDisconnection();
    }
    
    /**
     * Notifies the associated client that it has successfully logged out.
     */
    private void logoutSuccess() {
	ByteBuffer buf = ByteBuffer.allocate(1);
	buf.put(SimpleSgsProtocol.LOGOUT_SUCCESS).
	    flip();
	writeNow(buf, false);
	monitorDisconnection();
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public boolean isOpen() {
        return asyncMsgChannel.isOpen();
    }

    /** {@inheritDoc} */
    public void close() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "closing channel, protocol:{0}", this);
	}
	if (isOpen()) {
	    try {
		asyncMsgChannel.close();
	    } catch (IOException e) {
	    }
	}
	readHandler = new ClosedReadHandler();
        writeHandler = new ClosedWriteHandler();
	if (protocolHandler != null) {
	    SessionProtocolHandler handler = protocolHandler;
	    protocolHandler = null;
	    handler.disconnect(new RequestHandler());
	}
    }

    /* -- Object method overrides -- */
    
    /** {@inheritDoc} */
    @Override
    public String toString() {
	return getClass().getName() + "[" +
	    (identity != null ? identity : "<unknown>") + "]";
    }

    /* -- Methods for reading and writing -- */
    
    /**
     * Schedules an asynchronous task to resume reading.
     */
    protected final void scheduleRead() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "scheduling read, protocol:{0}", this);
	}
	acceptor.scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("ResumeReadOnReadHandler") {
		public void run() {
		    logger.log(
			Level.FINER, "resuming reads protocol:{0}", this);
		    readNow();
		} });
    }

    /**
     * Resumes reading from the underlying connection.
     */
    protected final void readNow() {
	if (isOpen()) {
	    readHandler.read();
	} else {
	    close();
	}
    }

    /**
     * Writes a message to the underlying connection if login has been handled,
     * otherwise enqueues the message to be sent when the login has not yet been
     * handled.
     *
     * @param	buf a buffer containing a complete protocol message
     */
    protected final void write(ByteBuffer buf) {
	synchronized (lock) {
	    if (!loginHandled) {
		messageQueue.add(buf);
	    } else {
		writeNow(buf, false);
	    }
	}
    }
    
    /**
     * Writes a message to the underlying connection.
     *
     * @param	message a buffer containing a complete protocol message
     * @param	flush if {@code true}, then set the {@code loginHandled}
     *		flag to {@code true} and flush the message queue
     */
    protected final void writeNow(ByteBuffer message, boolean flush) {
	try {
	    writeHandler.write(message);
		    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(
		    Level.WARNING, e,
		    "writeNow protocol:{0} throws", this);
	    }
	}

	if (flush) {
	    synchronized (lock) {
		loginHandled = true;
		for (ByteBuffer nextMessage : messageQueue) {
		    try {
			writeHandler.write(nextMessage);
		    } catch (RuntimeException e) {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.logThrow(
				Level.WARNING, e,
				"writeNow protocol:{0} throws", this);
			}
		    }
		}
		messageQueue.clear();
	    }
	}
    }

    /**
     * Writes the specified buffer, satisfying the specified delivery
     * requirement.
     *
     * <p>This implementation writes the buffer reliably, because this
     * protocol only supports reliable delivery.
     *
     * <p>A subclass can override the {@code writeBuffer} method if it
     * supports other delivery guarantees and can make use of alternate
     * transports for those other delivery requirements.
     *
     * @param	buf a byte buffer containing a protocol message
     * @param	delivery a delivery requirement
     */
    protected void writeBuffer(ByteBuffer buf, Delivery delivery) {
	write(buf);
    }
    
    /**
     * Returns the next reconnect key.
     *
     * @return the next reconnect key
     */
    private static byte[] getNextReconnectKey() {
	byte[] key = new byte[DEFAULT_RECONNECT_KEY_LENGTH];
	random.nextBytes(key);
	return key;
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
			   "write protocol:{0} message:{1} first:{2}",
                           SimpleSgsProtocolImpl.this,
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
		    "processQueue protocol:{0} size:{1,number,#} head={2}",
		    SimpleSgsProtocolImpl.this, pendingWrites.size(),
		    HexDumper.format(message, 0x50));
		message.mark();
            }
            try {
                asyncMsgChannel.write(message, this);
            } catch (RuntimeException e) {
                logger.logThrow(Level.SEVERE, e,
				"{0} processing message {1}",
				SimpleSgsProtocolImpl.this,
				HexDumper.format(message, 0x50));
                throw e;
            }
        }

	/** Done writing the first request in the queue. */
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
			   "completed write protocol:{0} message:{1}",
			   SimpleSgsProtocolImpl.this,
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
				    "write protocol:{0} message:{1} throws",
				    SimpleSgsProtocolImpl.this,
				    HexDumper.format(message, 0x50));
                }
		synchronized (writeLock) {
		    pendingWrites.clear();
		}
		close();
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

        public void completed(IoFuture<ByteBuffer, Void> result) {
            throw new AssertionError("should be unreachable");
        }
    }

    /** A completion handler for reading from the session's channel. */
    private class ConnectedReadHandler extends ReadHandler {

	/** The lock for accessing the {@code isReading} field. The locks
	 * {@code lock} and {@code readLock} should only be acquired in
	 * that specified order.
	 */
	private final Object readLock = new Object();

	/** Whether a read is underway. */
        private boolean isReading = false;

	/** Creates an instance of this class. */
        ConnectedReadHandler() { }

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
        public void completed(IoFuture<ByteBuffer, Void> result) {
            synchronized (readLock) {
                isReading = false;
            }
            try {
                ByteBuffer message = result.getNow();
                if (message == null) {
		    close();
                    return;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "completed read protocol:{0} message:{1}",
                        SimpleSgsProtocolImpl.this,
			HexDumper.format(message, 0x50));
                }

                byte[] payload = new byte[message.remaining()];
                message.get(payload);

                // Dispatch
		MessageBuffer msg = new MessageBuffer(payload);
		byte opcode = msg.getByte();
		
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"processing opcode 0x{0}",
			Integer.toHexString(opcode));
		}
		
		handleMessageReceived(opcode, msg);
		

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
                close();
            }
        }
    }

    /**
     * Processes the received message.  This implementation processes
     * opcodes for {@code SimpleSgsProtocol} version {@code 0x04}.  A
     * subclass can override this implementation to process additional
     * opcodes, and then delegate to this implementation to process the
     * version {@code 0x04} opcodes.
     *
     * @param	opcode the message opcode
     * @param	msg a message buffer containing the entire message, but
     *		with the position advanced to the payload (just after the
     *		opcode)
     */
    protected void handleMessageReceived(byte opcode, MessageBuffer msg) {
		
	switch (opcode) {
		
	    case SimpleSgsProtocol.LOGIN_REQUEST:

		byte version = msg.getByte();
	        if (version != getProtocolVersion()) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                    "got protocol version:{0}, " +
			    "expected {1}", version, getProtocolVersion());
	            }
		    close();
	            break;
	        }

		String name = msg.getString();
		String password = msg.getString();

		try {
		    identity = acceptor.authenticate(name, password);
		} catch (Exception e) {
		    logger.logThrow(
			Level.FINEST, e,
			"login authentication failed for name:{0}", name);
		    loginFailure("login failed", e);
		    
		    break;
		}

		listener.newLogin(
 		    identity, SimpleSgsProtocolImpl.this, new LoginHandler());
		
                // Resume reading immediately
		readNow();

		break;

	    case SimpleSgsProtocol.SESSION_MESSAGE:
		ByteBuffer clientMessage =
		    ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
		if (protocolHandler == null) {
		    // ignore message before authentication
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "Dropping early session message:{0} " +
			    "for protocol:{1}",
			    HexDumper.format(clientMessage, 0x50),
			    SimpleSgsProtocolImpl.this);
		    }
		    return;
		}

		// TBD: schedule a task to process this message?
		protocolHandler.sessionMessage(clientMessage,
					       new RequestHandler());
		break;

	    case SimpleSgsProtocol.CHANNEL_MESSAGE:
		BigInteger channelRefId =
		    new BigInteger(1, msg.getBytes(msg.getShort()));
		ByteBuffer channelMessage =
		    ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
		if (protocolHandler == null) {
		    // ignore message before authentication
		    if (logger.isLoggable(Level.FINE)) {
			logger.log(
			    Level.FINE,
			    "Dropping early channel message:{0} " +
			    "for protocol:{1}",
			    HexDumper.format(channelMessage, 0x50),
			    SimpleSgsProtocolImpl.this);
		    }
		    return;
		}
		
		// TBD: schedule a task to process this message?
		protocolHandler.channelMessage(
		    channelRefId, channelMessage, new RequestHandler());
		break;

	    case SimpleSgsProtocol.LOGOUT_REQUEST:
		if (protocolHandler == null) {
		    close();
		    return;
		}
		protocolHandler.logoutRequest(new LogoutHandler());

		// Resume reading immediately
                readNow();

		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"unknown opcode 0x{0}",
			Integer.toHexString(opcode));
		}
		close();
		break;
	}
    }

    /**
     * Monitors the client's disconnection and closes this instance's
     * underlying connection if the client hasn't closed the connection in
     * a timely fashion.
     */
    protected void monitorDisconnection() {
	acceptor.monitorDisconnection(this);	
    }
    
    /**
     * A completion handler that is notified when the associated login
     * request has completed processing. 
     */
    private class LoginHandler
	implements RequestCompletionHandler<SessionProtocolHandler>
    {
	/** {@inheritDoc}
	 *
	 * <p>This implementation invokes the {@code get} method on the
	 * specified {@code future} to obtain the session's protocol
	 * handler.
	 *
	 * <p>If the login request completed successfully (without throwing an
	 * exception), it sends a logout success message to the client.
	 *
	 * <p>Otherwise, if the {@code get} invocation throws an {@code
	 * ExecutionException} and the exception's cause is a {@link
	 * LoginRedirectException}, it sends a login redirect message to
	 * the client with the redirection information obtained from the
	 * exception.  If the {@code ExecutionException}'s cause is a
	 * {@link LoginFailureException}, it sends a login failure message
	 * to the client.
	 *
	 * <p>If the {@code get} method throws an exception other than
	 * {@code ExecutionException}, or the {@code ExecutionException}'s
	 * cause is not either a {@code LoginFailureException} or a {@code
	 * LoginRedirectException}, then a login failed message is sent to
	 * the client.
	 */
	public void completed(Future<SessionProtocolHandler> future) {
	    try {
		protocolHandler = future.get();
		loginSuccess();
		
	    } catch (ExecutionException e) {
		// login failed
		Throwable cause = e.getCause();
		if (cause instanceof LoginRedirectException) {
		    // redirect
		    LoginRedirectException redirectException =
			(LoginRedirectException) cause;
		    
                    loginRedirect(redirectException.getNodeId(),
                                  redirectException.getProtocolDescriptors());
		    
		} else if (cause instanceof LoginFailureException) {
		    loginFailure(cause.getMessage(), cause.getCause());
		} else {
		    loginFailure(e.getMessage(), e.getCause());
		}
	    } catch (Exception e) {
		loginFailure(e.getMessage(), e.getCause());
	    }
	}
    }

    /**
     * A completion handler that is notified when its associated request has
     * completed processing. 
     */
    private class RequestHandler implements RequestCompletionHandler<Void> {
	
	/**
	 * {@inheritDoc}
	 *
	 * <p>This implementation schedules a task to resume reading.
	 */
	public void completed(Future<Void> future) {
	    try {
		future.get();
	    } catch (ExecutionException e) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.logThrow(
			Level.FINE, e, "Obtaining request result throws ");
		}

		Throwable cause = e.getCause();
		if (cause instanceof RequestFailureException) {
		    FailureReason reason =
			((RequestFailureException) cause).getReason();
		    if (reason.equals(FailureReason.DISCONNECT_PENDING)) {
			// Don't read any more from client because session
			// is disconnecting.
			return;
		    }
		    // Assume other failures are transient.
		}

	    } catch (Exception e) {
		// TBD: Unknown exception: disconnect?
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "Obtaining request result throws ");
		}
	    }
	    scheduleRead();
	}
    }
    
    /**
     * A completion handler that is notified when the associated logout
     * request has completed processing. 
     */
    private class LogoutHandler implements RequestCompletionHandler<Void> {

	/** {@inheritDoc}
	 *
	 * <p>This implementation sends a logout success message to the
	 * client .
	 */
	public void completed(Future<Void> future) {
	    try {
		future.get();
	    } catch (Exception e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e, "Obtaining logout result throws ");
		}
	    }
	    logoutSuccess();
	}
    }
}
