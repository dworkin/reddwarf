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

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

/**
 * An implementation of {@link AsynchronousSocketChannel}.
 * Most interesting methods are delegated to the {@link AsyncKey}
 * returned by this channel's channel group.
 */
class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
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

    /** The underlying {@code SocketChannel}. */
    final SocketChannel channel;

    /** The {@code AsyncKey} for the underlying channel. */
    final AsyncKey key;

    /**
     * Creates a new instance registered with the given channel group.
     * 
     * @param group the channel group
     * @throws IOException if an I/O error occurs
     */
    AsyncSocketChannelImpl(AsyncGroupImpl group)
        throws IOException
    {
        this(group, group.selectorProvider().openSocketChannel());
    }

    /**
     * Creates a new instance registered with the given channel group, with
     * the given underlying {@link SocketChannel}. Used by an
     * {@link AsyncServerSocketChannelImpl} when a new connection is
     * accepted.
     * 
     * @param group the channel group
     * @param channel the {@link SocketChannel} for this async channel
     * @throws IOException if an I/O error occurs
     */
    AsyncSocketChannelImpl(AsyncGroupImpl group,
                           SocketChannel channel)
        throws IOException
    {
        super(group.provider());
        this.channel = channel;
        key = group.register(channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + ":" + key;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        key.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl bind(SocketAddress local)
        throws IOException
    {
        if ((local != null) && (!(local instanceof InetSocketAddress))) {
            throw new UnsupportedAddressTypeException();
        }

        InetSocketAddress inetLocal = (InetSocketAddress) local;
        if ((inetLocal != null) && inetLocal.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        final Socket socket = channel.socket();
        try {
            socket.bind(inetLocal);
        } catch (SocketException e) {
            if (socket.isBound()) {
                throw Util.initCause(new AlreadyBoundException(), e);
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
    public SocketAddress getLocalAddress() throws IOException {
        return channel.socket().getLocalSocketAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl setOption(SocketOption name, Object value)
        throws IOException
    {
        if (!(name instanceof StandardSocketOption)) {
            throw new IllegalArgumentException("Unsupported option " + name);
        }

        if (value == null || !name.type().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Bad parameter for " + name);
        }

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final Socket socket = channel.socket();
        
        try {
            switch (stdOpt) {
            case SO_SNDBUF:
                socket.setSendBufferSize(((Integer) value).intValue());
                break;

            case SO_RCVBUF:
                socket.setReceiveBufferSize(((Integer) value).intValue());
                break;

            case SO_KEEPALIVE:
                socket.setKeepAlive(((Boolean) value).booleanValue());
                break;

            case SO_REUSEADDR:
                socket.setReuseAddress(((Boolean) value).booleanValue());
                break;

            case TCP_NODELAY:
                socket.setTcpNoDelay(((Boolean) value).booleanValue());
                break;

            default:
                throw new IllegalArgumentException(
                    "Unsupported option " + name);
            }
        } catch (SocketException e) {
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
    public Object getOption(SocketOption name) throws IOException {
        if (!(name instanceof StandardSocketOption)) {
            throw new IllegalArgumentException("Unsupported option " + name);
        }

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final Socket socket = channel.socket();
        try {
            switch (stdOpt) {
            case SO_SNDBUF:
                return socket.getSendBufferSize();

            case SO_RCVBUF:
                return socket.getReceiveBufferSize();

            case SO_KEEPALIVE:
                return socket.getKeepAlive();

            case SO_REUSEADDR:
                return socket.getReuseAddress();

            case TCP_NODELAY:
                return socket.getTcpNoDelay();

            default:
                throw new IllegalArgumentException("Unsupported option " 
                                                   + name);
            }
        } catch (SocketException e) {
            if (socket.isClosed()) {
                throw Util.initCause(new ClosedChannelException(), e);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<SocketOption> options() {
        return socketOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl shutdown(ShutdownType how)
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
                    socket.shutdownOutput();
                    key.selected(OP_WRITE);
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
    public SocketAddress getConnectedAddress() throws IOException {
        return channel.socket().getRemoteSocketAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectionPending() {
        return channel.isConnectionPending();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return key.isOpPending(OP_READ);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return key.isOpPending(OP_WRITE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> connect(
        SocketAddress remote,
        final A attachment,
        final CompletionHandler<Void, ? super A> handler)
    {
        try {
            if (channel.connect(remote)) {
                Future<Void> result = Util.finishedFuture(null);
                key.runCompletion(handler, attachment, result);
                return AttachedFuture.wrap(result, attachment);
            }
        } catch (ClosedChannelException e) {
            throw Util.initCause(new ClosedAsynchronousChannelException(), e);
        } catch (IOException e) {
            Future<Void> result = Util.failedFuture(e);
            key.runCompletion(handler, attachment, result);
            return AttachedFuture.wrap(result, attachment);
        }

        return key.execute(
            OP_CONNECT, attachment, handler, 0, TimeUnit.MILLISECONDS,
            new Callable<Void>() {
                public Void call() throws IOException {
                    try {
                        channel.finishConnect();
                        return null;
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
                        return channel.read(dst);
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
                        return channel.read(dsts, offset, length);
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
                        return channel.write(src);
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
                        return channel.write(srcs, offset, length);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }
}
