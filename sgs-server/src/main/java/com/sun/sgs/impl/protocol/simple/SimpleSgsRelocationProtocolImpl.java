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
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractCompletionFuture;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.LoginRedirectException;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.RelocateFailureException;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.protocol.SessionRelocationProtocol;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.math.BigInteger;
import java.nio.ByteBuffer;
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
public class SimpleSgsRelocationProtocolImpl
    extends SimpleSgsProtocolImpl
    implements SessionRelocationProtocol
{
    /** The logger for this class. */
    private static final LoggerWrapper staticLogger = new LoggerWrapper(
	Logger.getLogger(SimpleSgsRelocationProtocolImpl.class.getName()));

    /** The default reason string returned for relocation failure. */
    private static final String DEFAULT_RELOCATE_FAILED_REASON =
	"relocation refused";

    /** A lock for {@suspendCompletionFuture}, and {@code relocationInfo}
     * fields. */
    private final Object lock = new Object();

    /** The completion future if suspending messages is in progress, or
     * null. */
    private SuspendMessagesCompletionFuture suspendCompletionFuture = null;
    
    /** The session's relocation information, if this session is relocating to
     * another node. */
    private RelocationInfo relocationInfo = null;

    /**
     * Creates a new instance of this class.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     */
    SimpleSgsRelocationProtocolImpl(ProtocolListener listener,
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
     * {@link SimpleSgsProtocolImpl#scheduleRead} after constructing the
     * instance to commence reading.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     * @param	logger a logger for this instance
     */
    protected  SimpleSgsRelocationProtocolImpl(ProtocolListener listener,
				     SimpleSgsProtocolAcceptor acceptor,
				     AsynchronousByteChannel byteChannel,
				     int readBufferSize,
				     LoggerWrapper logger)
    {
	super(listener, acceptor, byteChannel, readBufferSize, logger);
    }

    /**
     * Returns the {@code SimpleSgsProtocol} version supported by this
     * implementation.
     *
     * This implementation returns the latest {@code SimpleSgsProtocol}
     * version.
     *
     * @return the {@code SimpleSgsProtocol} version supported by this
     * implementation
     */
    @Override
    protected byte getProtocolVersion() {
	return SimpleSgsProtocol.VERSION;
    }
    
    /* -- Implement SessionProtocol -- */

    /** {@inheritDoc} */
    @Override
    public void sessionMessage(ByteBuffer message, Delivery delivery) {
	checkSuspend();
	super.sessionMessage(message, delivery);
    }
    
    /** {@inheritDoc} */
    @Override
    public void channelJoin(
	String name, BigInteger channelId, Delivery delivery)
    {
	checkSuspend();
	super.channelJoin(name, channelId, delivery);
    }

    /** {@inheritDoc} */
    @Override
    public void channelLeave(BigInteger channelId) {
	checkSuspend();
	super.channelLeave(channelId);
    }

    /** {@inheritDoc} */
    @Override
    public void channelMessage(
	BigInteger channelId, ByteBuffer message, Delivery delivery)
    {
	checkSuspend();
	super.channelMessage(channelId, message, delivery);
    }
    
    /* -- Implement SessionRelocationProtocol -- */

    /** {@inheritDoc} */
    public void suspend(RequestCompletionHandler<Void> completionHandler) {
	synchronized (lock) {
	    if (suspendCompletionFuture != null) {
		throw new IllegalStateException(
		    "already suspending messages");
	    }
	    suspendCompletionFuture =
		new SuspendMessagesCompletionFuture(completionHandler);
	}
	ByteBuffer buf = ByteBuffer.allocate(1);
	buf.put(SimpleSgsProtocol.SUSPEND_MESSAGES).
	    flip();
	writeNow(buf, true);
    }

    /** {@inheritDoc} */
    public void resume() {
	synchronized (lock) {
	    if (suspendCompletionFuture != null) {
		suspendCompletionFuture = null;
	    }
	}
	ByteBuffer buf = ByteBuffer.allocate(1);
	buf.put(SimpleSgsProtocol.RESUME_MESSAGES).
	    flip();
	writeNow(buf, true);
    }

    /** {@inheritDoc} */
    public void relocate(Set<ProtocolDescriptor> descriptors,
			 ByteBuffer relocationKey,
			 RequestCompletionHandler<Void> completionHandler)
    {
	synchronized (lock) {
	    if (relocationInfo != null) {
		throw new IllegalStateException("session already relocating");
	    } else if (suspendCompletionFuture == null ||
		       !suspendCompletionFuture.isDone())
	    {
		throw new IllegalStateException("session is not suspended");
	    }
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "relocating, identity:{0} key:{1}", getIdentity(),
			   HexDumper.toHexString(relocationKey.array()));
	    }
	    relocationInfo = new RelocationInfo(descriptors, relocationKey);
	}
	
	relocationInfo.sendRelocateNotification();
    }
    
    /* -- Private methods for sending protocol messages -- */

    /**
     * Notifies the associated client that the previous relocation attempt
     * was successful.
     */
    private void relocateSuccess() {
	MessageBuffer buf = new MessageBuffer(1 + reconnectKey.length);
	buf.putByte(SimpleSgsProtocol.RELOCATE_SUCCESS).
	    putBytes(reconnectKey);
	writeNow(ByteBuffer.wrap(buf.getBuffer()), true);
    }

    /**
     * Notifies the associated client that the previous relocation attempt
     * was unsuccessful for the specified {@code reason}.  The specified
     * {@code throwable}, if non-{@code null} is an exception that
     * occurred while processing the relocation request.  The message
     * channel should be careful not to reveal to the associated client
     * sensitive data that may be present in the specified {@code
     * throwable}.
     *
     * @param	reason a reason why the relocation was unsuccessful
     * @param	throwable an exception that occurred while processing the
     *		relocation request, or {@code null}
     */
    private void relocateFailure(String reason, Throwable ignore) {
	// the reason argument is overridden for security reasons
	reason = DEFAULT_RELOCATE_FAILED_REASON;
        MessageBuffer buf =
	    new MessageBuffer(1 + MessageBuffer.getSize(reason));
        buf.putByte(SimpleSgsProtocol.RELOCATE_FAILURE).
            putString(reason);
	writeNow((ByteBuffer.wrap(buf.getBuffer())), true);
	monitorDisconnection();
    }

    /**
     * Throws {@link IllegalStateException} if the client session is
     * relocating or has suspended messages.
     *
     * @throws	IllegalStateException if the client session is relocating
     */
    private void checkSuspend() {
	synchronized (lock) {
	    if (relocationInfo != null) {
		throw new IllegalStateException("session relocating");
	    } else if (suspendCompletionFuture != null) {
		throw new IllegalStateException("messages suspended");
	    }
	}
    }


    /** 
     * Handles v5 protocol messages (relocate and suspend), and delegates
     * to the super class to handle the v4 protocol messages.
     *
     * @param	opcode the message opcode
     * @param	msg a message buffer containing the entire message, but
     *		with the position advanced to the payload (just after the
     *		opcode)
     */
    @Override
    protected void handleMessageReceived(byte opcode, MessageBuffer msg) {
	
	switch (opcode) {
		
	    case SimpleSgsProtocol.RELOCATE_REQUEST:

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

		byte[] keyBytes = msg.getBytes(msg.limit() - msg.position());
		BigInteger relocationKey = new BigInteger(1, keyBytes);
		
		listener.relocatedSession(
 		    relocationKey, SimpleSgsRelocationProtocolImpl.this,
		    new RelocateHandler());
		
                // Resume reading immediately
		readNow();

		break;
		
	    case SimpleSgsProtocol.SUSPEND_MESSAGES_COMPLETE:
		synchronized (lock) {
		    if (suspendCompletionFuture != null) {
			suspendCompletionFuture.done();
		    }  else {
			if (logger.isLoggable(Level.WARNING)) {
			    logger.log(
				Level.WARNING, "{0} received unexpected " +
				"SUSPEND_MESSAGES_COMPLETE");
			}
		    }
		}
		break;
		
	    default:
		super.handleMessageReceived(opcode, msg);
		break;
	    }
	}
    
    /**
     * A completion handler that is notified when the associated relocate
     * request has completed processing. 
     */
    private class RelocateHandler
	implements RequestCompletionHandler<SessionProtocolHandler>
    {
	/** {@inheritDoc}
	 *
	 * <p>This implementation invokes the {@code get} method on the
	 * specified {@code future} to obtain the session's protocol
	 * handler.
	 *
	 * <p>If the relocate request completed successfully (without
	 * throwing an exception), it sends a relocate success message to
	 * the client.
	 *
	 * <p>Otherwise, if the {@code get} invocation throws an {@code
	 * ExecutionException} and the exception's cause is a {@link
	 * LoginRedirectException} or {@link RelocateFailureException}, it
	 * sends a relocate failure message to the client.
	 *
	 * <p>If the {@code get} method throws an exception other than
	 * {@code ExecutionException}, or the {@code ExecutionException}'s
	 * cause is not either a {@code RelocateFailureException} or a {@code
	 * LoginRedirectException}, then a relocate failed message is sent
	 * to the client.
	 */
	public void completed(Future<SessionProtocolHandler> future) {
	    try {
		protocolHandler = future.get();
		relocateSuccess();
		
	    } catch (ExecutionException e) {
		// relocate failed
		Throwable cause = e.getCause();
		if (cause instanceof LoginRedirectException ||
		    cause instanceof RelocateFailureException) {
		    relocateFailure(cause.getMessage(), cause.getCause());
		} else {
		    relocateFailure(e.getMessage(), e.getCause());
		}
	    } catch (Exception e) {
		relocateFailure(e.getMessage(), e.getCause());
	    }
	}
    }
    
    /**
     * Relocation information.
     */
    private class RelocationInfo {

	private final Set<ProtocolDescriptor> descriptors;
	private final ByteBuffer relocationKey;

	RelocationInfo(Set<ProtocolDescriptor> descriptors,
		       ByteBuffer relocationKey)
	{
	    this.descriptors = descriptors;
	    this.relocationKey = relocationKey;
	}

	/**
	 * Sends a relocate notification to the underlying session, and
	 * monitors the underlying connection for timely disconnection.
	 */
	void sendRelocateNotification() {
	    for (ProtocolDescriptor descriptor : descriptors) {
		if (acceptor.getDescriptor().supportsProtocol(descriptor)) {
		    byte[] redirectionData =
			((SimpleSgsProtocolDescriptor) descriptor).
			getConnectionData();
		    ByteBuffer buf =
			ByteBuffer.allocate(1 + redirectionData.length +
					    relocationKey.remaining());
		    buf.put(SimpleSgsProtocol.RELOCATE_NOTIFICATION).
			put(redirectionData).
			put(relocationKey).
			flip();
		    writeNow(buf, true);
		    monitorDisconnection();
		    return;
		}
	    }
	}
    }

    /**
     * A completion future for suspending messages.
     */
    private static class SuspendMessagesCompletionFuture
	extends AbstractCompletionFuture<Void>
    {
	SuspendMessagesCompletionFuture(RequestCompletionHandler<Void>
					completionFuture)
	{
	    super(completionFuture);
	}

	/** {@inheritDoc} */
	protected Void getValue() { return null; }
	
	/** {@inheritDoc} */
	public void done() {
	    super.done();
	}
    }
}
