/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;
import com.sun.sgs.nio.channels.UnsupportedSocketOptionException;

final class AsyncServerSocketChannelImpl
    extends AsynchronousServerSocketChannel
    implements AsyncChannelHandler
{
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_REUSEADDR);
        socketOptions = Collections.unmodifiableSet(es);
    }

    final AsyncChannelGroupImpl channelGroup;
    final ServerSocketChannel channel;

    final AtomicBoolean acceptPending = new AtomicBoolean(false);

    AsyncServerSocketChannelImpl(
            DefaultAsynchronousChannelProvider provider,
            AsyncChannelGroupImpl group)
    throws IOException
    {
        super(provider);
        this.channelGroup = group;
        channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        group.register(this);
    }

    private void checkClosed() {
        if (! channel.isOpen())
            throw new ClosedAsynchronousChannelException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<AsynchronousSocketChannel, A> accept(
        A attachment,
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        checkClosed();
        if (! acceptPending.compareAndSet(false, true)) {
            throw new AcceptPendingException();
        }
        //AcceptFuture<A> future = new AcceptFuture<A>(attachment, handler);
       // group.updateInterestOps(channel, SelectionKey.OP_ACCEPT, 0);
        //IoFuture<AsynchronousSocketChannel, A> foo =
        //    new AbstractIoFuture<AsynchronousSocketChannel, A>(group, attachment, handler);
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl bind(SocketAddress local,
                                             int backlog)
        throws IOException
    {
        channel.socket().bind(local, backlog);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAcceptPending()
    {
        return acceptPending.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl setOption(SocketOption name,
                                                  Object value)
        throws IOException
    {
        if (! name.type().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException(
                "value must be of type " + name.type());
        }
        if (name instanceof StandardSocketOption) {
            StandardSocketOption stdOpt = (StandardSocketOption) name;
            switch (stdOpt) {
            case SO_REUSEADDR:
                channel.socket().setReuseAddress((Boolean) value);
                return this;

            case SO_RCVBUF:
                channel.socket().setReceiveBufferSize((Integer) value);
                return this;

            default:
                break;
            }
        }
        throw new UnsupportedSocketOptionException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        // TODO

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
    public Object getOption(SocketOption name) throws IOException {
        if (name instanceof StandardSocketOption) {
            StandardSocketOption stdOpt = (StandardSocketOption) name;
            switch (stdOpt) {
            case SO_REUSEADDR:
                return channel.socket().getReuseAddress();

            case SO_RCVBUF:
                return channel.socket().getReceiveBufferSize();

            default:
                break;
            }
        }
        throw new UnsupportedSocketOptionException();
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
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    public void channelSelected(int ops) throws IOException {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    public ServerSocketChannel getSelectableChannel() {
        return channel;
    }
}
