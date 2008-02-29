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

package com.sun.sgs.impl.service.session;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A wrapper channel that reads and writes complete messages by framing
 * messages with a 2-byte message length, and masking (and re-issuing) partial
 * I/O operations.
 */
public class AsynchronousMessageChannel implements Channel {

    /** The logger for this class. */
    static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(AsynchronousMessageChannel.class.getName()));

    /** The number of bytes used to represent the message length. */
    private static final int PREFIX_LENGTH = 2;

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...)
     */
    final AsynchronousByteChannel channel;

    /** Whether there is a read underway. */
    final AtomicBoolean readPending = new AtomicBoolean();

    /** Whether there is a write underway. */
    final AtomicBoolean writePending = new AtomicBoolean();

    /**
     * Creates a new instance of this class with the given channel.
     * 
     * @param channel a channel
     */
    public AsynchronousMessageChannel(AsynchronousByteChannel channel) {
	this.channel = channel;
    }

    /* -- Methods for reading and writing -- */

    /**
     * Initiates reading a complete message from this channel into the given
     * buffer.  Returns a future which will contain the specified attachment
     * and a read-only view of {@code dst} buffer containing the complete
     * message.  Calls {@code handler} when the read operation has completed,
     * if {@code handler} is not {@code null}.
     * 
     * @param	<A> the attachment type
     * @param	dst the buffer into which bytes are to be transferred
     * @param	attachment the object to {@link IoFuture#attach attach} to the
     *		returned {@link IoFuture} object; can be {@code null}
     * @param	handler the completion handler object; can be {@code null}
     * @return	a future representing the result of the operation
     * @throws	ReadPendingException if a read is in progress
     */
    public <A> IoFuture<ByteBuffer, A> read(
	ByteBuffer dst, A attachment, CompletionHandler<ByteBuffer, A> handler)
    {
        if (!readPending.compareAndSet(false, true)) {
            throw new ReadPendingException();
	}
        return new Reader<A>(attachment, handler).start(dst);
    }

    /**
     * Initiates reading a complete message from this channel into the given
     * buffer, and returns a future which will contain a {@code null}
     * attachment and a read-only view into the {@code dst} buffer containing
     * the complete message.
     * 
     * @param	<A> the attachment type
     * @param	dst the buffer into which bytes are to be transferred
     * @param	handler the completion handler object; can be {@code null}
     * @return	a future representing the result of the operation.
     * @throws	ReadPendingException if a read is in progress
     */
    public final <A> IoFuture<ByteBuffer, A> read(
	ByteBuffer dst, CompletionHandler<ByteBuffer, A> handler)
    {
        return read(dst, null, handler);
    }

    /**
     * Initiates writing a complete message from the given buffer to the
     * underlying channel, and returns a future which will contain the
     * specified attachment and a read-only view into the {@code src} buffer
     * containing the complete message.
     * 
     * @param	<A> the attachment type
     * @param	src the buffer from which bytes are to be retrieved
     * @param	attachment the object to {@link IoFuture#attach attach} to the
     *		returned {@link IoFuture} object; can be {@code null}
     * @param	handler the completion handler object; can be {@code null}
     * @return	a future representing the result of the operation
     * @throws	WritePendingException if a write is in progress
     */
    public <A> IoFuture<Void, A> write(
	ByteBuffer src, A attachment, CompletionHandler<Void, A> handler)
    {
        if (!writePending.compareAndSet(false, true)) {
            throw new WritePendingException();
	}
        return new Writer<A>(attachment, handler).start(src);
    }

    /**
     * Initiates writing a complete message from the given buffer to the
     * underlying channel, and returns a future which will contains a {@code
     * null} attachment and a read-only view into the {@code src} buffer
     * containing the complete message.
     * 
     * @param	<A> the attachment type
     * @param	src the buffer from which bytes are to be retrieved
     * @param	handler the completion handler object; can be {@code null}
     * @return	a future representing the result of the operation
     * @throws	WritePendingException if a write is in progress
     */
    public final <A> IoFuture<Void, A> write(
	ByteBuffer src, CompletionHandler<Void, A> handler)
    {
        return write(src, null, handler);
    }

    /* -- Implement Channel -- */

    /** {@inheritDoc} */
    public void close() throws IOException {
        channel.close();
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return channel.isOpen();
    }

    /* -- Other methods and classes -- */

    /**
     * Returns the length of the complete message based on the data read into
     * the buffer between position 0 and the current position, or {@code -1} if
     * the length cannot be determined.
     * 
     * @param	buf the buffer
     * @return	the length, or {@code -1}
     */
    int completeMessageLength(ByteBuffer buf) {
	if (buf.position() >= PREFIX_LENGTH) {
	    return (buf.getShort(0) & 0xffff) + PREFIX_LENGTH;
	}
	/* Check that there is room for the prefix in the buffer */
	if (buf.limit() < PREFIX_LENGTH) {
	    throw new BufferOverflowException();
	}
	return -1;
    }

    private final class Reader<A>
	extends DelegatingCompletionHandler<ByteBuffer, A, Integer, ByteBuffer>
    {
        private int messageLen = -1;

        Reader(A attachment, CompletionHandler<ByteBuffer, A> handler) {
            super(attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            readPending.set(false);
            super.done();
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer> implStart(ByteBuffer dst) {
            return processBuffer(dst);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer> implCompleted(
	    IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException 
        {
            ByteBuffer dst = result.attach(null);
            int bytesRead = result.getNow();

            if (bytesRead < 0) {
                set(null);
                return null;
            }

            return processBuffer(dst);
        }

        private IoFuture<Integer, ByteBuffer> processBuffer(ByteBuffer dst) {
            ByteBuffer readBuf = dst.asReadOnlyBuffer();

            if (messageLen < 0) {
                messageLen = completeMessageLength(dst);

                if (messageLen >= 0) {
                    // Ensure that the buffer will hold the complete message
                    if (dst.limit() < messageLen) {
                        throw new BufferOverflowException();
                    }
                } else {
                    // Or at least ensure that the buffer isn't full
                    if (! dst.hasRemaining()) {
                        throw new BufferOverflowException();
		    }
                }
            }

            if (messageLen >= 0 && dst.position() >= messageLen) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER,
			       "{0} read complete {1}:{2}",
			       this, messageLen, dst.position());
                }
                readBuf.limit(messageLen).flip();
                set(readBuf); // Invokes the completion handler
                return null;
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
			   "{0} read incomplete {1}:{2}",
			   this, messageLen, dst.position());
            }
            return channel.read(dst, dst, this);
        }
    }

    private final class Writer<A>
	extends DelegatingCompletionHandler<Void, A, Integer, ByteBuffer> {

        Writer(A attachment, CompletionHandler<Void, A> handler) {
            super(attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            writePending.set(false);
            super.done();
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer> implStart(ByteBuffer src) {
	    /* XXX: Move prepending size to here.  -tjb@sun.com (02/27/2008) */
            return channel.write(src, src, this);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer> implCompleted(
	    IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException
        {
            ByteBuffer src = result.attach(null);
            result.getNow();
            if (src.hasRemaining()) {
                // Write some more
                return channel.write(src, src, this);
            } else {
                // Finished
                set(null);
                return null;
            }
        }
    }
}
