/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;

class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
{
    final AsyncChannelGroupImpl channelGroup;

    AsyncSocketChannelImpl(DefaultAsynchronousChannelProvider provider,
        AsyncChannelGroupImpl group)
    {
        super(provider);
        this.channelGroup = group;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl bind(SocketAddress local)
        throws IOException
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> connect(SocketAddress remote, A attachment,
        CompletionHandler<Void, ? super A> handler)
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getConnectedAddress() throws IOException
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectionPending()
    {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending()
    {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending()
    {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> read(ByteBuffer dst, long timeout,
        TimeUnit unit, A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Long, A> read(ByteBuffer[] dsts, int offset,
        int length, long timeout, TimeUnit unit, A attachment,
        CompletionHandler<Long, ? super A> handler)
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl setOption(SocketOption name, Object value)
        throws IOException
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl shutdown(ShutdownType how)
        throws IOException
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> write(ByteBuffer src, long timeout,
        TimeUnit unit, A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Long, A> write(ByteBuffer[] srcs, int offset,
        int length, long timeout, TimeUnit unit, A attachment,
        CompletionHandler<Long, ? super A> handler)
    {
        // TODO
        return null;
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
    public boolean isOpen() {
        // TODO
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public SocketAddress getLocalAddress() throws IOException {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Object getOption(SocketOption name) throws IOException {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Set<SocketOption> options() {
        // TODO
        return null;
    }

}
