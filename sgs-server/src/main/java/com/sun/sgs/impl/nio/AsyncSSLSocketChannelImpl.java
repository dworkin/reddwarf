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
 */

package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.impl.net.ssl.SSLChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

/**
 * An extension of {@link AsyncSocketChannelImpl}
 * with SSL decryption/encryption in the read and
 * write methods.
 */
class AsyncSSLSocketChannelImpl
    extends AsyncSocketChannelImpl
{
    /** The valid socket options for this channel. */
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_SNDBUF,
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_KEEPALIVE,
            StandardSocketOption.SO_REUSEADDR,
            StandardSocketOption.TCP_NODELAY);
        socketOptions = Collections.unmodifiableSet(es);
    }

    // The SSL/TLS secure channel
    final SSLChannel sslChannel;

    /**
     * Creates a new instance registered with the given channel group.
     * 
     * @param group the channel group
     * @throws IOException if an I/O error occurs
     */
    AsyncSSLSocketChannelImpl(AsyncGroupImpl group)
        throws IOException
    {
        this(group, group.selectorProvider().openSocketChannel());
    }

    /**
     * Creates a new instance registered with the given channel group, with
     * the given underlying {@link SocketChannel}. Used by an
     * {@link AsyncSSLServerSocketChannelImpl} when a new connection is
     * accepted.
     * 
     * @param group the channel group
     * @param channel the {@link SocketChannel} for this async channel
     * @throws IOException if an I/O error occurs
     */
    AsyncSSLSocketChannelImpl(AsyncGroupImpl group,
                           SocketChannel channel)
        throws IOException
    {
        super(group);
        sslChannel = new SSLChannel(channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSSLSocketChannelImpl shutdown(ShutdownType how)
        throws IOException
    {
        final Socket socket = channel.socket();
        try {
            if (how == ShutdownType.READ  || how == ShutdownType.BOTH) {
                if (!socket.isInputShutdown()) {
                    socket.shutdownInput();
                    key.selected(OP_READ);
                }
            }
            if (how == ShutdownType.WRITE || how == ShutdownType.BOTH) {
                if (!socket.isOutputShutdown()) {
                    if (sslChannel.shutdown()) {
                        socket.shutdownOutput();
                        key.selected(OP_WRITE);
                    }
                }
            }
        } catch (SocketException e) {
            if (!socket.isConnected()) {
                throw Util.initCause(new NotYetConnectedException(), e);
            }
            if (socket.isClosed()) {
                throw Util.initCause(new ClosedChannelException(), e);
            }
            throw e;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> read(
            final ByteBuffer dst,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<Integer, ? super A> handler)
    {
        return key.execute(OP_READ, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    try {
                        return sslChannel.read(dst);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Long, A> read(
            final ByteBuffer[] dsts,
            final int offset,
            final int length, 
            long timeout, 
            TimeUnit unit, 
            A attachment,
            CompletionHandler<Long, ? super A> handler)
    {
        if ((offset < 0) || (offset >= dsts.length)) {
            throw new IllegalArgumentException("offset out of range");
        }
        if ((length < 0) || (length > (dsts.length - offset))) {
            throw new IllegalArgumentException("length out of range");
        }

        return key.execute(OP_READ, attachment, handler, timeout, unit,
            new Callable<Long>() {
                public Long call() throws IOException {
                    try {
                        return sslChannel.read(dsts, offset, length);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> write(
            final ByteBuffer src,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<Integer, ? super A> handler)
    {
        return key.execute(OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    try {
                        return sslChannel.write(src);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Long, A> write(
            final ByteBuffer[] srcs, 
            final int offset,
            final int length,
            long timeout,
            TimeUnit unit,
            A attachment,
           CompletionHandler<Long, ? super A> handler)
    {
        if ((offset < 0) || (offset >= srcs.length)) {
            throw new IllegalArgumentException("offset out of range");
        }
        if ((length < 0) || (length > (srcs.length - offset))) {
            throw new IllegalArgumentException("length out of range");
        }

        return key.execute(OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Long>() {
                public Long call() throws IOException {
                    try {
                        return sslChannel.write(srcs, offset, length);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }
}
