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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.NamePasswordCredentials;
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
import com.sun.sgs.service.Node;
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
import javax.security.auth.login.LoginException;

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

    /** Message for indicating login/authentication failure. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";
    
    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...).
     */
    private final AsynchronousMessageChannel asyncMsgChannel;

    /** This message channel's protocol impl. */
    final SimpleSgsProtocolImpl protocolImpl;

    /** The completion handler for reading from the I/O channel. */
    private volatile ReadHandler readHandler;

    /** The completion handler for writing to the I/O channel. */
    private volatile WriteHandler writeHandler = new ConnectedWriteHandler();

    /** A lock for {@code loggedIn} and {@code messageQueue} fields. */
    private Object lock = new Object();

    /** The identity for this channel. */
    private volatile Identity authenticatedIdentity = null;
    
    /** The protocol message handler. Set after a sucessful login. */
    private volatile SessionMessageHandler sessionHandler;
    
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

        readHandler = new ConnectedReadHandler();
        
        /*
	 * TBD: It might be a good idea to implement high- and low-water marks
	 * for the buffers, so they don't go into hysteresis when they get
	 * full. -JM
	 */
	scheduleReadOnReadHandler();
    }
    
    private void loginRedirect(ProtocolDescriptor newListener) {
        TransportDescriptor transportDesc = newListener.getTransport();
	int hostStringSize = MessageBuffer.getSize(transportDesc.getHostName());
	MessageBuffer buf = new MessageBuffer(1 + hostStringSize + 4);
        buf.putByte(SimpleSgsProtocol.LOGIN_REDIRECT).
            putString(transportDesc.getHostName()).
            putInt(transportDesc.getListeningPort());
	writeToWriteHandler(ByteBuffer.wrap(buf.getBuffer()));
	flushMessageQueue();
    }

    /* -- Implement SessionMessageChannel -- */

    /** {@inheritDoc} */
    @Override
    public Identity identity() {
        assert authenticatedIdentity != null;
        return authenticatedIdentity;
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
    public void loginFailure(String reason) {
        int stringSize = MessageBuffer.getSize(reason);
        MessageBuffer buf = new MessageBuffer(1 + stringSize);
        buf.putByte(SimpleSgsProtocol.LOGIN_FAILURE).
            putString(reason);
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
//        try {
//            close();
//        } catch (IOException ignore) { }
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
        @Override
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
		
	    case SimpleSgsProtocol.LOGIN_REQUEST:

                if (sessionHandler != null) {
                    logger.log(Level.WARNING,
                               "received LOGIN_REQUEST after login - ignoring:{0}",
                               this);
                    
                    read();
                    break;
                }
	        byte version = msg.getByte();
	        if (version != SimpleSgsProtocol.VERSION) {
	            if (logger.isLoggable(Level.SEVERE)) {
	                logger.log(Level.SEVERE,
	                           "got protocol version:{0}, expected {1}",
                                   version, SimpleSgsProtocol.VERSION);
	            }
                    // If version is bad, can't send login failure message
                    // back to client (not talking same language) so just close.
                    try {
                        close();
                    } catch (IOException ignore) {}
	            break;
	        }

		final String name = msg.getString();
		final String password = msg.getString();
                protocolImpl.scheduleNonTransactionalTask(
                    new AbstractKernelRunnable() {
        //          new AbstractKernelRunnable("HandleLoginRequest") {
                        public void run() {
                            handleLoginRequest(name, password);
                } });
                // Resume reading immediately - TODO - really?
		read();

		break;
		
	    case SimpleSgsProtocol.SESSION_MESSAGE:
                if (sessionHandler != null) {
                    ByteBuffer clientMessage =
                        ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
                    CompletionFuture sessionMessageFuture =
                                sessionHandler.sessionMessage(clientMessage);

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
                } else
                    logger.log(Level.WARNING,
                               "session message received before login completed: {0}",
                               this);
		read();

		break;

	    case SimpleSgsProtocol.CHANNEL_MESSAGE:
                if (sessionHandler != null) {
                    BigInteger channelRefId =
                        new BigInteger(1, msg.getBytes(msg.getShort()));
                    ByteBuffer channelMessage =
                        ByteBuffer.wrap(msg.getBytes(msg.limit() - msg.position()));
                    CompletionFuture channelMessageFuture =
                            sessionHandler.channelMessage(channelRefId,
                                                          channelMessage);

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
                } else
                    logger.log(Level.WARNING,
                               "channel message received before login completed:{0}",
                               this);
                read();
		
		break;

	    case SimpleSgsProtocol.LOGOUT_REQUEST:
		if (sessionHandler != null) {
                    CompletionFuture logoutFuture =
                            sessionHandler.logoutRequest();
                    try {
                        logoutFuture.get();
                    } catch (InterruptedException ignore) {
                    } catch (ExecutionException e) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.logThrow(Level.FINE, e,
                                            "Processing LOGOUT_REQUEST message: " +
                                            "for protocol:{0} throws",
                                            SimpleSgsProtocolMessageChannel.this);
                        }
                    }
//                    logoutSuccess();
                } else {
                    logger.log(Level.WARNING,
                               "logout message received before login completed:{0}",
                               this);
                    // Resume reading immediately
                    read();
                }
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
    
    /**
     * Disconnect from the client. If a session handler is present call that
     * (which will eventually get back here), otherwise just close the
     * channel.
     */
    private void disconnect() {
        if (sessionHandler != null)
            sessionHandler.disconnect();    // ignore future
        else
            try {
                close();
            } catch (IOException e) {
                logger.logThrow(Level.FINEST,
                                e, "close failed during disconnect");
            }
    }
    
    /**
     * Handles a login request for the specified {@code name} and
     * {@code password}, scheduling the appropriate response to be
     * sent to the client (either logout success, login failure, or
     * login redirect).
     */
    private void handleLoginRequest(String name, String password) {
        logger.log(Level.FINEST,
                   "handling login request for name:{0}", name);
        try {
            /*
             * Authenticate identity.
             */
            authenticatedIdentity = //authenticate(name, password);
                protocolImpl.identityManager.authenticateIdentity(
                        new NamePasswordCredentials(name,
                                                    password.toCharArray()));
        } catch (Exception e) {
            logger.logThrow(Level.FINEST, e,
                            "login authentication failed for name:{0}", name);
            // don't send exception message, may have too much info
            sendLoginFailureAndClose("login authentication failed");
            return;
        }

        Node node;
        try {
            /*
             * Get node assignment.
             */
            protocolImpl.nodeMapService.assignNode(SimpleSgsProtocolMessageChannel.class,
                                                   authenticatedIdentity);
            GetNodeTask getNodeTask = new GetNodeTask(authenticatedIdentity);		
            protocolImpl.runTransactionalTask(getNodeTask,
                                              authenticatedIdentity);
            node = getNodeTask.getNode();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "identity:{0} assigned to node:{1}",
                           name, node);
            }

        } catch (Exception e) {
            logger.logThrow(Level.WARNING, e,
                            "getting node assignment for identity:{0} throws",
                            name);
            sendLoginFailureAndClose(e.getMessage());
            return;
        }

        long assignedNodeId = node.getId();
        if (assignedNodeId == protocolImpl.localNodeId) {
            /*
             * Login to this node.
             */
            try {
                sessionHandler = //protocolImpl.localLogin(this);
                        (SessionMessageHandler)protocolImpl.connectionHandler.
                                newConnection(this, protocolImpl.protocolDesc);

            } catch (Exception e) {
                sendLoginFailureAndClose(e.getMessage());
            }

        } else {
            /*
             * Redirect login to assigned (non-local) node.
             */
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "redirecting login for identity:{0} " +
                           "from nodeId:{1} to node:{2}",
                           name, protocolImpl.localNodeId, node);
            }
            ProtocolDescriptor[] descriptors = node.getClientListeners();
            for (ProtocolDescriptor descriptor : descriptors) {
                if (descriptor.isCompatibleWith(protocolImpl.protocolDesc)) {
                    final ProtocolDescriptor newListener = descriptor;
                    // TBD: identity may be null. Fix to pass a non-null
                    // identity when scheduling the task.

                    protocolImpl.scheduleNonTransactionalTask(
                        new AbstractKernelRunnable() {
        //              new AbstractKernelRunnable("SendLoginRedirectMessage") {
                            public void run() {
                                loginRedirect(newListener);
                                try {
                                    close();
                                } catch (IOException ignore) {}
                            } });
                    return;
                }
            }
            logger.log(Level.SEVERE,
                       "redirect node {0} does not support a compatable protocol" +
                       node);
            sendLoginFailureAndClose("Failed to find redirect node");
        }
    }

//    /**
//     * Authenticates the specified username and password, throwing
//     * LoginException if authentication fails.
//     */
//    private Identity authenticate(String username, String password)
//	throws LoginException
//    {
//	return protocolImpl.identityManager.authenticateIdentity(
//	    new NamePasswordCredentials(username, password.toCharArray()));
//    }
    
    /**
     * Sends a login failure message to the client and closes the client
     * channel. Should only be called on login failure, before a session
     * handler is set.
     *
     * @param   reason the reason the login failed, or {@code null}
     */
    private void sendLoginFailureAndClose(final String reason) {
        assert sessionHandler == null;
        // TBD: identity may be null. Fix to pass a non-null identity
        // when scheduling the task.
        protocolImpl.scheduleNonTransactionalTask(
            new AbstractKernelRunnable() {
//            new AbstractKernelRunnable("SendLoginFailureMessage") {
                public void run() {
//                        loginFailure(LOGIN_REFUSED_REASON, throwable);
                        loginFailure(reason);
                    try {
                        close();
                    } catch (IOException ignore) {}
                } });
    }
    
    /**
     * This is a transactional task to obtain the node assignment for
     * a given identity.
     */
    private class GetNodeTask extends AbstractKernelRunnable {

	private final Identity authenticatedIdentity;
	private volatile Node node = null;

	GetNodeTask(Identity authenticatedIdentity) {
	    this.authenticatedIdentity = authenticatedIdentity;
	}

	public void run() throws Exception {
	    node = protocolImpl.nodeMapService.getNode(authenticatedIdentity);
	}

	Node getNode() {
	    return node;
	}
    }
}
