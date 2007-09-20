/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

final class AsyncServerSocketChannelImpl
    extends AsynchronousServerSocketChannel
    implements AsyncChannelImpl
{
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_REUSEADDR);
        socketOptions = Collections.unmodifiableSet(es);
    }

    final AbstractAsyncChannelGroup group;
    final ServerSocketChannel channel;

    volatile AsyncOp<?> acceptTask = null;

    private static final AtomicReferenceFieldUpdater<AsyncServerSocketChannelImpl, AsyncOp>
        acceptTaskUpdater = AtomicReferenceFieldUpdater.newUpdater(
            AsyncServerSocketChannelImpl.class, AsyncOp.class, "acceptTask");

    AsyncServerSocketChannelImpl(AbstractAsyncChannelGroup group)
        throws IOException
    {
        super(group.provider());
        this.group = group;
        this.channel = group.selectorProvider().openServerSocketChannel();
        group.registerChannel(this);
    }

    public ServerSocketChannel channel() {
        return channel;
    }

    public void selected(int ops) {
        if (ops != OP_ACCEPT)
            throw new IllegalStateException("Unexpected ops " + ops);

        AsyncOp<?> task = acceptTaskUpdater.getAndSet(this, null);
        if (task != null)
            group.execute(task);
    }

    public void setException(int op, Throwable t) {
        if (op != OP_ACCEPT)
            throw new IllegalStateException("Unexpected op " + op);

        AsyncOp<?> task = acceptTaskUpdater.getAndSet(this, null);
        if (task != null)
            group.setException(task, t);
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
        try {
            channel.close();
        } finally {
            setException(OP_ACCEPT, new AsynchronousCloseException());
            group.unregisterChannel(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl bind(SocketAddress local,
                                             int backlog)
        throws IOException
    {
        final ServerSocket socket = channel.socket();
        if (socket.isClosed())
            throw new ClosedChannelException();
        if (socket.isBound())
            throw new AlreadyBoundException();
        if ((local != null) && (!(local instanceof InetSocketAddress)))
            throw new UnsupportedAddressTypeException();

        socket.bind(local, backlog);
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
    public AsyncServerSocketChannelImpl setOption(SocketOption name, Object value)
        throws IOException
    {
        if (! (name instanceof StandardSocketOption))
            throw new IllegalArgumentException("Unsupported option " + name);

        if (value == null || !name.type().isAssignableFrom(value.getClass()))
            throw new IllegalArgumentException("Bad parameter for " + name);

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final ServerSocket socket = channel.socket();
        switch (stdOpt) {
        case SO_RCVBUF:
            socket.setReceiveBufferSize(((Integer)value).intValue());
            break;

        case SO_REUSEADDR:
            socket.setReuseAddress(((Boolean)value).booleanValue());
            break;

        default:
            throw new IllegalArgumentException("Unsupported option " + name);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Object getOption(SocketOption name) throws IOException {
        if (! (name instanceof StandardSocketOption))
            throw new IllegalArgumentException("Unsupported option " + name);

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final ServerSocket socket = channel.socket();
        switch (stdOpt) {
        case SO_RCVBUF:
            return socket.getReceiveBufferSize();

        case SO_REUSEADDR:
            return socket.getReuseAddress();

        default:
            break;
        }
        throw new IllegalArgumentException("Unsupported option " + name);
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
    public boolean isAcceptPending() {
        return acceptTask != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<AsynchronousSocketChannel, A> accept(
            A attachment,
            CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        AsyncOp<AsynchronousSocketChannel> task =
            AsyncOp.create(attachment, handler,
            new Callable<AsynchronousSocketChannel>() {
                public AsynchronousSocketChannel call() throws IOException {
                    return new AsyncSocketChannelImpl(group, channel.accept());
                }});

        if (! acceptTaskUpdater.compareAndSet(this, null, task))
            throw new AcceptPendingException();

        group.awaitReady(this, OP_ACCEPT);
        return AttachedFuture.wrap(task, attachment);
    }
}
