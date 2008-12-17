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
import com.sun.sgs.auth.Identity;
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
import com.sun.sgs.protocol.LoginCompletionFuture;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.LoginRedirectException;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.service.ProtocolDescriptor;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.Node;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the protocol specified in {@code SimpleSgsProtocol} over two
 * communication channels.  The
 * implementation uses a wrapper channel, {@link AsynchronousMessageChannel},
 * that reads and writes complete messages by framing messages with a 2-byte
 * message length, and masking (and re-issuing) partial I/O operations.  Also
 * enforces a fixed buffer size when reading.
 */
class MultiSgsProtocolImpl implements SessionProtocol {
    /** The number of bytes used to represent the message length. */
    private static final int PREFIX_LENGTH = 2;

    /** The logger for this class. */
    private static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(MultiSgsProtocolImpl.class.getName()));

    /** The default reason string returned for login failure. */
    private static final String DEFAULT_LOGIN_FAILED_REASON = "login refused";

    /** The default length of the reconnect key, in bytes.
     * TBD: the reconnection key length should be configurable.
     */
    private static final int DEFAULT_RECONNECT_KEY_LENGTH = 16;

    /** A random number generator for reconnect keys. */
    private static SecureRandom random = new SecureRandom();
    
    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...).
     */
    private final AsynchronousMessageChannel asyncMsgChannel;

    /** The protocol handler. */
    private volatile SessionProtocolHandler protocolHandler;

    /** This protocol's acceptor. */
    private final MultiSgsProtocolAcceptor acceptor;

    /** The protocol listener. */
    private final ProtocolListener listener;

    /** The identity. */
    private volatile Identity identity;

    /** The reconnect key. */
    private byte[] reconnectKey;

    /** The completion handler for reading from the I/O channel. */
    private volatile ReadHandler readHandler = new ConnectedReadHandler();

    /** The completion handler for writing to the I/O channel. */
    private volatile WriteHandler writeHandler = new ConnectedWriteHandler();

    /** A lock for {@code loggedIn} and {@code messageQueue} fields. */
    private Object lock = new Object();

    /** Indicates whether the client's login ack has been sent. */
    private boolean loginHandled = false;

    /** Messages enqueued to be sent after a login ack is sent. */
    private List<ByteBuffer> messageQueue = new ArrayList<ByteBuffer>();

    /** The immutable set of supported delivery requirements. */
    private final Set<Delivery> deliverySet = new HashSet<Delivery>();

    /**
     * Flags on whether to use the primary (reliable) connection or
     * to use the secondary connection.
     */
    private final boolean[] usePrimary = new boolean[Delivery.values().length];
    
    /** Secondary comm channel. */
    private SecondaryChannel secondaryChannel = null;
    
    /**
     * Creates a new instance of this class.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     */
    public MultiSgsProtocolImpl(ProtocolListener listener,
                                MultiSgsProtocolAcceptor acceptor,
                                AsynchronousByteChannel byteChannel,
                                int readBufferSize)
    {
	// The read buffer size lower bound is enforced by the protocol acceptor
	assert readBufferSize >= PREFIX_LENGTH;
	this.listener = listener;
	this.acceptor = acceptor;
        asyncMsgChannel =
	    new AsynchronousMessageChannel(byteChannel, readBufferSize);
	reconnectKey = getNextReconnectKey();
        deliverySet.add(Delivery.RELIABLE);
        for (Delivery delivery : Delivery.values())
            usePrimary[delivery.ordinal()] = true;
	
	/*
	 * TBD: It might be a good idea to implement high- and low-water marks
	 * for the buffers, so they don't go into hysteresis when they get
	 * full. -JM
	 */
	scheduleReadOnReadHandler();
    }

    /**
     * Attach the secondary connection.
     * @param connection
     * @param supportedDelivery
     * @return
     */
    SessionProtocolHandler attach(SecondaryChannel channel,
                                  Delivery[] supportedDelivery)
    {
        if (protocolHandler != null) {
        
            // Set the usePrimary flag to false for any delivery that the
            // secondary connection can support. Skipping reliable, since
            // the primary does that.
            //
            for (Delivery delivery : supportedDelivery) {
                if (delivery != Delivery.RELIABLE) {
                    deliverySet.add(delivery);
                    usePrimary[delivery.ordinal()] = false;
                }
            }
            secondaryChannel = channel;
            return protocolHandler;
        } else
            return null;
    }
    
    /* -- Implement SessionProtocol -- */

    /** {@inheritDoc} */
    public Set<Delivery> supportedDeliveries() {
	return deliverySet;
    }
    
    /** {@inheritDoc} */
    public void sessionMessage(ByteBuffer message) {
	ByteBuffer buf = ByteBuffer.wrap(new byte[1 + message.remaining()]);
	buf.put(SimpleSgsProtocol.SESSION_MESSAGE).
	    put(message).
	    flip();
	writeOrEnqueueIfLoginNotHandled(buf);
    }

    /** {@inheritDoc} */
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
    public void channelLeave(BigInteger channelId) {
	byte[] channelIdBytes = channelId.toByteArray();
	ByteBuffer buf =
	    ByteBuffer.allocate(1 + channelIdBytes.length);
	buf.put(SimpleSgsProtocol.CHANNEL_LEAVE).
	    put(channelIdBytes).
	    flip();
	writeOrEnqueueIfLoginNotHandled(buf);
    }

    /***
     * {@inheritDoc}
     * The {@code delivery} parameter is ignored. This protocol only supports
     * reliable delivery.
     */
    @Override
    public void channelMessage(BigInteger channelId,
                               ByteBuffer message,
                               Delivery delivery)
    {
        byte[] channelIdBytes = channelId.toByteArray();
        ByteBuffer buf =
            ByteBuffer.allocate(3 + channelIdBytes.length +
                                message.remaining());
        buf.put(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putShort((short) channelIdBytes.length).
            put(channelIdBytes).
            put(message).
            flip();
        if (usePrimary[delivery.ordinal()]) {
            writeOrEnqueueIfLoginNotHandled(buf);
        } else {
            secondaryChannel.writeOrEnqueueIfLoginNotHandled(buf);
        }
    }

    /** {@inheritDoc} */
    public void disconnect(DisconnectReason reason) {
	// TBD: The SimpleSgsProtocol does not yet support sending a
	// message to the client in the case of session termination or
	// preemption, so just close the connection for now.
	try {
	    close();
	} catch (IOException e) {
	}
    }
    
    /* -- Private methods for sending protocol messages -- */

    /**
     * Notifies the associated client that the previous login attempt was
     * successful.
     */
    private void loginSuccess() {
	MessageBuffer buf = new MessageBuffer(1 + reconnectKey.length);
	buf.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
	    putBytes(reconnectKey);
	writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
        acceptor.sucessfulLogin(reconnectKey, this);
    }

    /**
     * Notifies the associated client that it should redirect its login
     * to the specified {@code node}.
     *
     * @param	node a node to redirect the login
     */
    public void loginRedirect(Node node) {
        ProtocolDescriptor[] descriptors = node.getClientListeners();
        for (ProtocolDescriptor descriptor : descriptors) {
            if (acceptor.getDescriptor().isCompatibleWith(descriptor)) {
                loginRedirect((MultiSgsProtocolDescriptor)descriptor);
                return;
            }
        }
        loginFailure("redirect failed", null);
        logger.log(Level.SEVERE,
                   "redirect node {0} does not support a compatable transport",
                   node);
    }

    private void loginRedirect(MultiSgsProtocolDescriptor newListener) {
        byte[] primaryData = newListener.primaryDesc.getConnectionData();
        byte[] secondaryData = newListener.secondaryDesc.getConnectionData();
	MessageBuffer buf = new MessageBuffer(1 + primaryData.length +
                                                  secondaryData.length);
        buf.putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
            putBytes(primaryData).
            putBytes(secondaryData);
	writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
	acceptor.monitorDisconnection(this);
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
        writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
	acceptor.monitorDisconnection(this);
    }
    
    /**
     * Notifies the associated client that it has successfully logged out.
     */
    private void logoutSuccess() {
	ByteBuffer buf = ByteBuffer.allocate(1);
	buf.put(SimpleSgsProtocol.LOGOUT_SUCCESS).
	    flip();
	writeToWriteHandler(buf);
	acceptor.monitorDisconnection(this);
    }

    /* -- Implement Channel -- */
    
    /** {@inheritDoc} */
    public boolean isOpen() {
        return asyncMsgChannel.isOpen();
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        try {
            asyncMsgChannel.close();
            if (secondaryChannel != null)
                secondaryChannel.close();
        } finally {
            readHandler = new ClosedReadHandler();
            writeHandler = new ClosedWriteHandler();
            acceptor.disconnect(reconnectKey);
        }
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
			Level.FINER, "resuming reads protocol:{0}", this);
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

    /**
     * Method to disconnect the connection.
     */
    private void disconnect() {
	if (protocolHandler != null) {
	    protocolHandler.disconnect();
	} else {
	    try {
		close();
	    } catch (IOException ignore) {
	    }
	}
    }
    
    /**
     * Returns the next reconnect key.
     *
     * @return the next reconnect key
     */
    private static synchronized byte[] getNextReconnectKey() {
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
                           MultiSgsProtocolImpl.this,
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
		    MultiSgsProtocolImpl.this, pendingWrites.size(),
		    HexDumper.format(message, 0x50));
            }
            try {
                asyncMsgChannel.write(message, this);
            } catch (RuntimeException e) {
                logger.logThrow(Level.SEVERE, e,
				"{0} processing message {1}",
				MultiSgsProtocolImpl.this,
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
			   "completed write session:{0} message:{1}",
			   MultiSgsProtocolImpl.this,
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
				    MultiSgsProtocolImpl.this,
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
		    disconnect();
                    return;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "completed read protocol:{0} message:{1}",
                        MultiSgsProtocolImpl.this,
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
		logger.log(
 		    Level.FINEST,
		    "processing opcode 0x{0}",
		    Integer.toHexString(opcode));
	    }
	    
	    switch (opcode) {
		
	    case SimpleSgsProtocol.LOGIN_REQUEST:

	        byte version = msg.getByte();
	        if (version != SimpleSgsProtocol.VERSION) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                    "got protocol version:{0}, " +
	                    "expected {1}", version, SimpleSgsProtocol.VERSION);
	            }
		    disconnect();
	            break;
	        }

		final String name = msg.getString();
		final String password = msg.getString();

		try {
		    identity = acceptor.authenticate(name, password);
		} catch (Exception e) {
		    logger.logThrow(
			Level.FINEST, e,
			"login authentication failed for name:{0}", name);
		    loginFailure("login failed", e);
		    
		    break;
		}

		LoginCompletionFuture loginCompletionFuture =
		    listener.newLogin(
			identity, MultiSgsProtocolImpl.this);
		acceptor.scheduleNonTransactionalTask(
 		    new LoginCompletionTask(loginCompletionFuture));
		
                // Resume reading immediately
		read();

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
			    MultiSgsProtocolImpl.this);
		    }
		    return;
		}
		    
		acceptor.scheduleNonTransactionalTask(
		    new RequestCompletionTask(
		    	protocolHandler.sessionMessage(clientMessage)));
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
			    MultiSgsProtocolImpl.this);
		    }
		    return;
		}
		
		CompletionFuture channelMessageFuture =
		    protocolHandler.channelMessage(
			channelRefId, channelMessage);
		acceptor.scheduleNonTransactionalTask(
		    new RequestCompletionTask(channelMessageFuture));
		break;


	    case SimpleSgsProtocol.LOGOUT_REQUEST:
		if (protocolHandler == null) {
		    try {
			close();
		    } catch (IOException ignore) {
		    }
		    return;
		}
		acceptor.scheduleNonTransactionalTask(
		    new LogoutCompletionTask(
			protocolHandler.logoutRequest()));

		// Resume reading immediately
                read();

		break;
		
	    default:
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.log(
			Level.SEVERE,
			"unknown opcode 0x{0}",
			Integer.toHexString(opcode));
		}
		if (protocolHandler != null) {
		    protocolHandler.disconnect();
		}
		break;
	    }
	}
    }

    /**
     * Task to obtain the result of a login request and send a protocol
     * message accordingly to the client.
     */
    private class LoginCompletionTask extends AbstractKernelRunnable {

	/** The login completion future. */
	private final LoginCompletionFuture future;

	/**
	 * Constructs an instance with the specified login completion
	 * {@code future}.
	 *
	 * @param future a login completion future to wait on.
	 */
	LoginCompletionTask(LoginCompletionFuture future) {
	    super(LoginCompletionTask.class.getName());
	    this.future = future;
	}

	/** {@inheritDoc} */
	public void run() {
	    try {
		protocolHandler = future.get();
		loginSuccess();
		
	    } catch (InterruptedException e) {
		// reschedule interrupted execution
		acceptor.scheduleNonTransactionalTask(this);
	    } catch (ExecutionException e) {
		// login failed
		Throwable cause = e.getCause();
		if (cause instanceof LoginRedirectException) {
		    // redirect
		    Node node = ((LoginRedirectException) cause).getNode();
		    loginRedirect(node);
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
     * Task to wait for an associated request to complete. Reading is resumed
     * when request completes.
     */
    private class RequestCompletionTask extends AbstractKernelRunnable {

	/** The completion future. */
	private final CompletionFuture future;
	
	/**
	 * Constructs an instance with the given completion {@code future}.
	 *
	 * @param future a completion future to wait on
	 */
	RequestCompletionTask(CompletionFuture future) {
	    super(RequestCompletionTask.class.getName());
	    this.future = future;
	}

	/** {@inheritDoc} */
	public void run() {
	    try {
		future.get();
	    } catch (InterruptedException ignore) {
	    } catch (CancellationException ignore) {
	    } catch (ExecutionException e) {
		if (logger.isLoggable(Level.FINE)) {
		    logger.logThrow(
			Level.FINE, e,
			"Request future:{0} for protocol:{1} throws",
			future, MultiSgsProtocolImpl.this);
		}
	    }
	    readHandler.read();
	}
    }

    /**
     * Task to wait for a logout request to complete.
     */
    private class LogoutCompletionTask extends AbstractKernelRunnable {

	/** The completion future. */
	private final CompletionFuture future;

	/**
	 * Constructs an instance with the specified login completion
	 * {@code future}.
	 *
	 * @param future a login completion future to wait on.
	 */
	LogoutCompletionTask(CompletionFuture future) {
	    super(LoginCompletionTask.class.getName());
	    this.future = future;
	}

	/** {@inheritDoc} */
	public void run() {
	    try {
		future.get();
		
	    } catch (InterruptedException e) {
		// reschedule interrupted execution
		acceptor.scheduleNonTransactionalTask(this);
	    } catch (Exception ignore) {
	    }
	    // always report success
	    logoutSuccess();
	}
    }
}
