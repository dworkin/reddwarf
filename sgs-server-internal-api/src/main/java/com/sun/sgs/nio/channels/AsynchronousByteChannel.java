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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.nio.channels;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;

/**
 * A channel that can read and write bytes asynchronously. Some channels may
 * not allow more than one read to be outstanding at any given time. If a
 * thread invokes a read method before a previous read operation has
 * completed then a {@link ReadPendingException} will be thrown. Similarly,
 * if a write method is invoked before a previous write has completed then
 * {@link WritePendingException} is thrown. Whether or not other kinds of
 * I/O operations may proceed concurrently with a read operation depends
 * upon the type of the channel.
 *
 * @see Channels#newInputStream(AsynchronousByteChannel)
 * @see Channels#newOutputStream(AsynchronousByteChannel)
 */
public interface AsynchronousByteChannel extends Channel {

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     * This method initiates an operation to read a sequence of bytes from
     * this channel into the given buffer. The method returns an
     * {@link IoFuture} representing the pending result of the operation.
     * The result of the operation, obtained by invoking the
     * {@code IoFuture}'s {@link IoFuture#get() get} method, is the number
     * of bytes read, possibly zero, or {@code -1} if all bytes have been
     * read and the channel has reached end-of-stream.
     * <p>
     * This method initiates a read operation to read up to <i>r</i> bytes
     * from the channel, where <i>r</i> is the number of bytes remaining in
     * the buffer, that is, {@code dst.remaining()} at the time that the
     * read is attempted.
     * <p>
     * Suppose that a byte sequence of length <i>n</i> is read, where
     * <code>0 &lt;= <i>n</i> &lt;= <i>r</i></code>. This byte sequence will be
     * transferred into the buffer so that the first byte in the sequence is
     * at index <i>p</i> and the last byte is at index
     * <code><i>p</i> + <i>n</i> - 1</code>, where <i>p</i> is the buffer's
     * position at the moment the read is performed. Upon completion the
     * buffer's position will be equal to
     * <code><i>p</i> + <i>n</i></code>; its limit will not have changed.
     * <p>
     * Buffers are not safe for use by multiple concurrent threads so care
     * should be taken to not to access the buffer until the operaton has
     * completed.
     * <p>
     * This method may be invoked at any time. Some channel types may not
     * allow more than one read to be outstanding at any given time. If a
     * thread initiates a read operation before a previous read operation has
     * completed then a {@link ReadPendingException} will be thrown.
     * <p>
     * The handler parameter is used to specify a {@link CompletionHandler}.
     * When the read operation completes the handler's
     * {@link CompletionHandler#completed(IoFuture) completed} method is
     * executed.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which bytes are to be transferred
     * @param attachment the object to {@link IoFuture#attach attach}
     *        to the returned {@link IoFuture} object; can be {@code null}
     * @param handler the completion handler object; can be {@code null}
     * @return a future representing the result of the operation
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if the channel does not allow more than
     *         one read to be outstanding and a previous read has not
     *         completed
     */
    <A> IoFuture<Integer, A> read(ByteBuffer dst, A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     * An invocation of this method of the form {@code c.read(dst,handler)}
     * behaves in exactly the same manner as the invocation
     * <pre>
     *   c.read(dst, null, handler);
     * </pre>
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which bytes are to be transferred
     * @param handler the completion handler object; can be {@code null}
     * @return a future representing the result of the operation
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if the channel does not allow more than
     *         one read to be outstanding and a previous read has not
     *         completed
     */
    <A> IoFuture<Integer, A> read(ByteBuffer dst,
        CompletionHandler<Integer, ? super A> handler);
    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * This method initiates an operation to write a sequence of bytes to
     * this channel from the given buffer. The method returns an
     * {@link IoFuture} representing the pending result of the operation.
     * The result of the operation, obtained by invoking the
     * {@code IoFuture}'s {@link IoFuture#get() get} method, is the number
     * of bytes written, possibly zero.
     * <p>
     * This method initiates a write operation to write up to <i>r</i> bytes
     * from the channel, where <i>r</i> is the number of bytes remaining in
     * the buffer, that is, {@code src.remaining()} at the time that the
     * write is attempted.
     * <p>
     * Suppose that a byte sequence of length <i>n</i> is written, where
     * <code>0 &lt;= <i>n</i> &lt;= <i>r</i></code>. This byte sequence will be
     * transferred from the buffer starting at index <i>p</i>, where <i>p</i>
     * is the buffer's position at the moment the write is performed; the
     * index of the last byte written will be
     * <code><i>p</i> + <i>n</i> - 1</code>.  Upon completion the
     * buffer's position will be equal to
     * <code><i>p</i> + <i>n</i></code>; its limit will not have changed.
     * <p>
     * Buffers are not safe for use by multiple concurrent threads so care
     * should be taken to not to access the buffer until the operaton has
     * completed.
     * <p>
     * This method may be invoked at any time. Some channel types may not
     * allow more than one write to be outstanding at any given time. If a
     * thread initiates a write operation before a previous write operation has
     * completed then a {@link WritePendingException} will be thrown.
     * <p>
     * The handler parameter is used to specify a {@link CompletionHandler}.
     * When the write operation completes the handler's
     * {@link CompletionHandler#completed(IoFuture) completed} method is
     * executed.
     * 
     * @param <A> the attachment type
     * @param src the buffer from which bytes are to be retrieved
     * @param attachment the object to {@link IoFuture#attach attach}
     *        to the returned {@link IoFuture} object; can be {@code null}
     * @param handler the completion handler object; can be {@code null}
     * @return a future representing the result of the operation
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if the channel does not allow more than
     *         one write to be outstanding and a previous write has not
     *         completed
     */
    <A> IoFuture<Integer, A> write(ByteBuffer src, A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * An invocation of this method of the form {@code c.write(src,handler)}
     * behaves in exactly the same manner as the invocation
     * <pre>
     *   c.write(src, null, handler);
     * </pre>
     * 
     * @param <A> the attachment type
     * @param src the buffer from which bytes are to be retrieved
     * @param handler the completion handler object; can be {@code null}
     * @return a future representing the result of the operation
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if the channel does not allow more than
     *         one write to be outstanding and a previous write has not
     *         completed
     */
    <A> IoFuture<Integer, A> write(ByteBuffer src,
        CompletionHandler<Integer, ? super A> handler);
}
