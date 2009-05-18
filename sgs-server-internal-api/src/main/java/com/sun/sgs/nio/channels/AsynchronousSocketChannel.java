/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * An asynchronous channel for stream-oriented connecting sockets.
 * <p>
 * Asynchronous socket channels are created in one of two ways. A
 * newly-created AsynchronousSocketChannel is created by invoking one of the
 * open methods defined by this class. A newly-created channel is open but
 * not yet connected. A connected AsynchronousSocketChannel is created when
 * a connection is made to the socket of an AsynchronousServerSocketChannel.
 * It is not possible to create an asynchronous socket channel for an
 * arbitrary, pre-existing socket.
 * <p>
 * A newly-created channel is connected by invoking its connect method; once
 * connected, a channel remains connected until it is closed. Whether or not
 * a socket channel is connected may be determined by invoking its
 * getConnectedAddress method. Whether or not a connect operation is in
 * progress may be determined by invoking the isConnectionPending method. An
 * attempt to invoke an I/O operation upon an unconnected channel will cause
 * a NotYetConnectedException to be thrown.
 * <p>
 * Channels of this type are safe for use by multiple concurrent threads.
 * They support concurrent reading and writing, though at most one read
 * operation and one write operation can be outstanding at any time. If a
 * thread initiates a read operation before a previous read operation has
 * completed then a ReadPendingException will be thrown. Similarly, an
 * attempt to initiate a write operation before a previous write has
 * completed will throw a WritePendingException. Whether or not a read or
 * write operation is pending may be determined by invoking the
 * isReadPending and isWritePending methods.
 * <p>
 * Socket options are configured using the setOption method. Asynchronous
 * socket channels support the following options:
 * <blockquote>
 * <table>
 * <tr>
 * <th>Option Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_SNDBUF SO_SNDBUF}</td>
 * <td>The size of the socket send buffer</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_RCVBUF SO_RCVBUF}</td>
 * <td> The size of the socket receive buffer </td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_KEEPALIVE SO_KEEPALIVE}</td>
 * <td>Keep connection alive</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_REUSEADDR SO_REUSEADDR}</td>
 * <td>Re-use address</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#TCP_NODELAY TCP_NODELAY}</td>
 * <td>Disable the Nagle algorithm</td>
 * </tr>
 * </table>
 * </blockquote>
 * and may support additional (implementation
 * specific) options. The list of options supported is obtained by invoking
 * the options method.
 * <h4>Timeouts</h4>
 * The read and write methods defined by this class allow a timeout to be
 * specified when initiating a read or write operation. If the timeout
 * elapses before an operation completes then the operation completes by
 * throwing ExecutionException with cause AbortedByTimeoutException. A
 * timeout may leave the channel, or the underlying connection, in an
 * inconsistent state. Where an implementation cannot guarantee that no
 * bytes have been read from the channel then it puts the channel into an
 * implementation specific error state and a subsequent attempt to initiate
 * a read operation throws an unspecified runtime exception. Similarly if a
 * write operation times and the implementation cannot guarantee that no
 * bytes have been written to the channel then it prohibits further write
 * operations by throwing an unspecified runtime exception.
 * <p>
 * When a timeout elapses then the state of the ByteBuffer, or the sequence
 * of buffers, for the I/O operation is not defined. Buffers should be
 * discarded or at least care must be taken to ensure that the buffers are
 * not accessed while the channel remains open.
 */
public abstract class AsynchronousSocketChannel extends AsynchronousChannel
    implements AsynchronousByteChannel, NetworkChannel
{
    /**
     * Initializes a new instance of this class.
     *
     * @param provider the asynchronous channel provider for this channel
     */
    protected AsynchronousSocketChannel(AsynchronousChannelProvider provider) {
        super(provider);
    }

    /**
     * Opens an asynchronous socket channel.
     * <p>
     * The new channel is created by invoking the
     * openAsynchronousSocketChannel method on the
     * AsynchronousChannelProvider object that created the given group.
     * If the group parameter is null then the resulting channel is created
     * by the system-wide default provider, and bound to the default group.
     *
     * @param group the group to which the newly constructed channel should
     *              be bound, or null for the default group
     * @return a new asynchronous socket channel
     * @throws ShutdownChannelGroupException if the specified group is shutdown
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousSocketChannel open(AsynchronousChannelGroup group)
        throws IOException
    {
        return AsynchronousChannelProvider.provider().
                   openAsynchronousSocketChannel(group);
    }

    /**
     * Opens an asynchronous socket channel.
     * <p>
     * This method returns an asynchronous socket channel that is bound
     * to the default group.This method is equivalent to evaluating the
     * expression:
     * <pre>
     *       open((AsynchronousChannelGroup)null);
     * </pre>
     * @return a new asynchronous socket channel
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousSocketChannel open() throws IOException {
        return open((AsynchronousChannelGroup) null);
    }

    /**
     * {@inheritDoc}
     */
    public abstract AsynchronousSocketChannel bind(SocketAddress local)
                                            throws IOException;

    /**
     * {@inheritDoc}
     */
    public abstract AsynchronousSocketChannel setOption(SocketOption name,
        Object value) throws IOException;

    /**
     * Shutdown a connection for reading and/or writing without closing the
     * channel.
     * <p>
     * The how parameter specifies if the input, output, or both sides of
     * the connection is shutdown. If the input side of the connection is
     * shutdown then further read operations on the channel will return -1,
     * the end-of-stream indication. If the input side of the connection is
     * already shutdown then invoking this method to shutdown the input side
     * of the connection has no effect. If the output side of the connection
     * is shutdown then further write operations on the channel will
     * complete immediately by throwning ExecutionException with cause
     * ClosedChannelException. If the output side of the connection is
     * already shutdown then invoking this method to shutdown the output
     * side of the connection has no effect.
     * 
     * @param how specifies if the input, output, or both sides of the
     *        connection is shutdown
     * @return this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws ClosedChannelException if this channel is closed
     * @throws IOException if some other I/O error occurs
     */
    public abstract AsynchronousSocketChannel shutdown(ShutdownType how)
        throws IOException;

    /**
     * Returns the remote address to which this channel's socket is
     * connected, or null if the channel's socket is not connected.
     * 
     * @return the remote address; null if the channel is not open or the
     *         channel's socket is not connected
     * @throws IOException if an I/O error occurs
     */
    public abstract SocketAddress getConnectedAddress() throws IOException;

    /**
     * Tells whether or not a connect is pending for this channel.
     * <p>
     * The result of this method is a snapshot of the channel state. It may
     * be invalid when the caller goes to examine the result and should not
     * be used for purposes of coordination.
     * 
     * @return true if, and only if, a connect is pending for this
     *         channel but has not yet completed
     */
    public abstract boolean isConnectionPending();

    /**
     * Tells whether or not a read is pending for this channel.
     * <p>
     * The result of this method is a snapshot of the channel state. It may
     * be invalid when the caller goes to examine the result and should not
     * be used for purposes of coordination.
     * 
     * @return true if, and only if, a read is pending for this this channel
     *         but has not yet completed
     * @see ReadPendingException
     */
    public abstract boolean isReadPending();

    /**
     * Tells whether or not a write is pending for this channel.
     * <p>
     * The result of this method is a snapshot of the channel state. It may
     * be invalid when the caller goes to examine the result and should not
     * be used for purposes of coordination.
     * 
     * @return true if, and only if, a write is pending for this this
     *         channel but has not yet completed
     * @see WritePendingException
     */
    public abstract boolean isWritePending();

    /**
     * Connects this channel.
     * <p>
     * This method initiates an operation to connect this channel, returning
     * a IoFuture representing the pending result of the operation. If the
     * connection is successfully established then the IoFuture's get method
     * will return null, otherwise it throws ExecutionException with the
     * approach cause.
     * <p>
     * This method performs exactly the same security checks as the Socket
     * class. That is, if a security manager has been installed then this
     * method verifies that its checkConnect method permits connecting to
     * the address and port number of the given remote endpoint.
     * 
     * @param <A> the attachment type
     * @param remote the remote address to which this channel is to be
     *        connected
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws AlreadyConnectedException if this channel is already
     *         connected
     * @throws ConnectionPendingException if a connection operation is
     *         already in progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the given remote endpoint
     * @see #getConnectedAddress()
     * @see #isConnectionPending()
     */
    public abstract <A> IoFuture<Void, A> connect(SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler);

    /**
     * Connects this channel.
     * <p>
     * This method initiates an operation to connect this channel, returning
     * a IoFuture representing the pending result of the operation. If the
     * connection is successfully established then the IoFuture's get method
     * will return null.
     * <p>
     * This method is equivalent to invoking
     * connect(SocketAddress,A,CompletionHandler) with the attachment
     * parameter set to null.
     * 
     * @param <A> the attachment type
     * @param remote the remote address to which this channel is to be
     *        connected
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws AlreadyConnectedException if this channel is already
     *         connected
     * @throws ConnectionPendingException if a connection operation is
     *         already in progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the given remote endpoint
     * @see #getConnectedAddress()
     * @see #isConnectionPending()
     */
    public final <A> IoFuture<Void, A> connect(SocketAddress remote, 
        CompletionHandler<Void, ? super A> handler)
    {
        return connect(remote, null, handler);
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     * This method initiates the reading of a sequence of bytes from this
     * channel into the given buffer, returning an IoFuture representing the
     * pending result of the operation. The IoFuture's get method returns
     * the number of bytes read, possibly zero, or -1 if all bytes have been
     * read and channel has reached end-of-stream.
     * <p>
     * If a timeout is specified and the timeout elapses before the
     * operation completes then it completes with ExecutionException and
     * cause AbortedByTimeoutException. In that case it is guranteed that no
     * bytes have been read from the channel into the given buffer.
     * <p>
     * Otherwise this method works in the same manner as the
     * AsynchronousByteChannel.read(ByteBuffer,A,CompletionHandler) method.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which bytes are to be transferred
     * @param timeout the timeout, or 0L for no timeout
     * @param unit the time unit of the timeout argument
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws IllegalArgumentException if the timeout parameter is negative
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous read operation on
     *         the channel completed due to a timeout
     */
    public abstract <A> IoFuture<Integer, A> read(ByteBuffer dst,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     * This method initiates the reading of a sequence of bytes from this
     * channel into the given buffer, returning an IoFuture representing the
     * pending result of the operation. The IoFuture's get method will
     * return the number of bytes read, possibly zero, or -1 if all bytes
     * have been read and channel has reached end-of-stream.
     * <p>
     * This method is equivalent to invoking
     * read(ByteBuffer,long,TimeUnit,A,CompletionHandler) with a timeout of
     * 0L.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which bytes are to be transferred
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous read operation on
     *         the channel completed due to a timeout
     */
    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     * This method initiates the reading of a sequence of bytes from this
     * channel into the given buffer, returning an IoFuture representing the
     * pending result of the operation. The IoFuture's get method will
     * return the number of bytes read, possibly zero, or -1 if all bytes
     * have been read and channel has reached end-of-stream.
     * <p>
     * This method is equivalent to invoking
     * read(ByteBuffer,long,TimeUnit,A,CompletionHandler) with a timeout of
     * 0L, and an attachment of null.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which bytes are to be transferred
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous read operation on
     *         the channel completed due to a timeout
     */
    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    /**
     * Reads a sequence of bytes from this channel into a subsequence of the
     * given buffers.
     * <p>
     * This method initiates the reading of a sequence of bytes from this
     * channel into a subsequence of the given buffers, returning an
     * IoFuture representing the pending result of the operation. The
     * IoFuture's get method returns the number of bytes read, possibly
     * zero, or -1 if all bytes have been read and channel has reached
     * end-of-stream.
     * <p>
     * This method initiates a read of up to r bytes from this channel,
     * where r is the total number of bytes remaining in the specified
     * subsequence of the given buffer array, that is,
     * <pre>
     *      dsts[offset].remaining()
     *          + dsts[offset+1].remaining()
     *          + ... + dsts[offset+length-1].remaining()
     * </pre>
     * at the moment that the read is attempted.
     * <p>
     * Suppose that a byte sequence of length n is read, where 0 <= n <= r.
     * Up to the first dsts[offset].remaining() bytes of this sequence are
     * transferred into buffer dsts[offset], up to the next
     * dsts[offset+1].remaining() bytes are transferred into buffer
     * dsts[offset+1], and so forth, until the entire byte sequence is
     * transferred into the given buffers. As many bytes as possible are
     * transferred into each buffer, hence the final position of each
     * updated buffer, except the last updated buffer, is guaranteed to be
     * equal to that buffer's limit.
     * <p>
     * If a timeout is specified and the timeout elapses before the
     * operation completes then it completes with ExecutionException and
     * cause AbortedByTimeoutException. In that case it is guranteed that no
     * bytes have been read from the channel into the given buffers.
     * 
     * @param <A> the attachment type
     * @param dsts the buffers into which bytes are to be transferred
     * @param offset the offset within the buffer array of the first buffer
     *        into which bytes are to be transferred; must be non-negative
     *        and no larger than dsts.length
     * @param length the maximum number of buffers to be accessed; must be
     *        non-negative and no larger than dsts.length - offset
     * @param timeout the timeout, or 0L for no timeout
     * @param unit the time unit of the timeout argument
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws IllegalArgumentException if the timeout parameter is
     *         negative, or the pre-conditions for the offset and length
     *         parameter aren't met
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous read operation on
     *         the channel completed due to a timeout
     */
    public abstract <A> IoFuture<Long, A> read(ByteBuffer[] dsts,
        int offset,
        int length,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Long, ? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * This method initiates the writing of a sequence of bytes to this
     * channel from the given buffer, returning an IoFuture representing the
     * pending result of the operation. The IoFuture's get method will
     * return the number of bytes written, possibly zero.
     * <p>
     * If a timeout is specified and the timeout elapses before the
     * operation completes then it completes with ExecutionException and
     * cause AbortedByTimeoutException. In that case it is guranteed that no
     * bytes have written to the channel from the given buffer.
     * <p>
     * Otherwise this method works in the same manner as the
     * AsynchronousByteChannel.write(ByteBuffer,A,CompletionHandler) method.
     * 
     * @param <A> the attachment type
     * @param src the buffer from which bytes are to be retrieved
     * @param timeout the timeout, or 0L for no timeout
     * @param unit the time unit of the timeout argument
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws IllegalArgumentException if the timeout parameter is negative
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous write operation on
     *         the channel completed due to a timeout
     */
    public abstract <A> IoFuture<Integer, A> write(ByteBuffer src,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * This method initiates the writing of a sequence of bytes to this
     * channel rom the given buffer, returning an IoFuture representing the
     * pending result of the operation.
     * <p>
     * This method is equivalent to invoking
     * write(ByteBuffer,long,TimeUnit,A,CompletionHandler) with a timeout of
     * 0L.
     * 
     * @param <A> the attachment type
     * @param src the buffer from which bytes are to be retrieved
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous write operation on
     *         the channel completed due to a timeout
     */
    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * This method initiates the writing of a sequence of bytes to this
     * channel from the given buffer, returning an IoFuture representing the
     * pending result of the operation.
     * <p>
     * This method is equivalent to invoking
     * write(ByteBuffer,long,TimeUnit,A,CompletionHandler) with a timeout of
     * 0L, and an attachment of null.
     * 
     * @param <A> the attachment type
     * @param src the buffer from which bytes are to be retrieved
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous write operation on
     *         the channel completed due to a timeout
     */
    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    /**
     * Writes a sequence of bytes to this channel from a subsequence of the
     * given buffers.
     * <p>
     * This method initiates the writing of a sequence of bytes to this
     * channel from a subsequence of the given buffers, returning an
     * IoFuture representing the pending result of the operation. The
     * IoFuture's get method will return the number of bytes written,
     * possibly zero.
     * <p>
     * This method initiates a write of up to r bytes to this channel, where
     * r is the total number of bytes remaining in the specified subsequence
     * of the given buffer array, that is,
     * <pre>
     *      srcs[offset].remaining()
     *          + srcs[offset+1].remaining()
     *          + ... + srcs[offset+length-1].remaining()
     * </pre>
     * at the moment that the write is attempted.
     * <p>
     * Suppose that a byte sequence of length n is written, where 0 <= n <=
     * r. Up to the first srcs[offset].remaining() bytes of this sequence
     * are written from buffer srcs[offset], up to the next
     * srcs[offset+1].remaining() bytes are written from buffer
     * srcs[offset+1], and so forth, until the entire byte sequence is
     * written. As many bytes as possible are written from each buffer,
     * hence the final position of each updated buffer, except the last
     * updated buffer, is guaranteed to be equal to that buffer's limit.
     * <p>
     * If a timeout is specified and the timeout elapses before the
     * operation completes then it completes with ExecutionException and
     * cause AbortedByTimeoutException. In that case it is guranteed that no
     * bytes have written to the channel from the given buffers.
     * 
     * @param <A> the attachment type
     * @param srcs the buffers from which bytes are to be retrieved
     * @param offset the offset within the buffer array of the first buffer
     *        from which bytes are to be retrieved; must be non-negative and
     *        no larger than srcs.length.
     * @param length the maximum number of buffers to be accessed; must be
     *        non-negative and no larger than srcs.length - offset
     * @param timeout the timeout, or 0L for no timeout
     * @param unit the time unit of the timeout argument
     * @param attachment the object to attach to the returned IoFuture
     *        object; can be null
     * @param handler the handler for consuming the result; can be null
     * @return an IoFuture object representing the pending result
     * @throws IllegalArgumentException if the timeout parameter is negative
     *         or the pre-conditions for the offset or length parameter
     *         aren't met
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not yet connected
     * @throws IllegalChannelStateException if a previous write operation on
     *         the channel completed due to a timeout
     */
    public abstract <A> IoFuture<Long, A> write(ByteBuffer[] srcs,
        int offset,
        int length,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Long, ? super A> handler);
}
