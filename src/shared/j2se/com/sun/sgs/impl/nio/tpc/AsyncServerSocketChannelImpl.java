/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.tpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

final class AsyncServerSocketChannelImpl
    extends AsynchronousServerSocketChannel
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
            ThreadedAsyncChannelProvider provider,
            AsyncChannelGroupImpl group)
    throws IOException
    {
        super(provider);
        this.channelGroup = group;
        channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        //group.register(this);
    }

    private void checkClosedAsync() {
        if (! channel.isOpen())
            throw new ClosedAsynchronousChannelException();
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
    public void close() throws IOException
    {
        // TODO
        channel.close();
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
        switch (stdOpt) {
        case SO_RCVBUF:
            channel.socket().setReceiveBufferSize(((Integer)value).intValue());
            break;

        case SO_REUSEADDR:
            channel.socket().setReuseAddress(((Boolean)value).booleanValue());
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
        switch (stdOpt) {
        case SO_RCVBUF:
            return channel.socket().getReceiveBufferSize();

        case SO_REUSEADDR:
            return channel.socket().getReuseAddress();

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
    public boolean isAcceptPending()
    {
        return acceptPending.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<AsynchronousSocketChannel, A> accept(
        A attachment,
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        checkClosedAsync();
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
}
