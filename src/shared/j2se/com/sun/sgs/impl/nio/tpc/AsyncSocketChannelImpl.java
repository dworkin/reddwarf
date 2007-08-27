/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.tpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
{
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

    final AsyncChannelGroupImpl channelGroup;
    final SocketChannel channel;
    private final Object closeLock = new Object();

    private final AtomicBoolean connectPending = new AtomicBoolean();
    private Future<Void>    connectFuture = null;
    private final AtomicBoolean readPending = new AtomicBoolean();
    private Future<Integer> readFuture    = null;
    private final AtomicBoolean writePending = new AtomicBoolean();
    private Future<Integer> writeFuture   = null;

    
    AsyncSocketChannelImpl(ThreadedAsyncChannelProvider provider,
        AsyncChannelGroupImpl group) throws IOException
    {
        super(provider);
        this.channelGroup = group;
        channel = SocketChannel.open();
        group.register(this);
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
        synchronized (closeLock) {
            if (! channel.isOpen())
                return;
            channel.close();
            // TODO kill in-progress read, write, and/or connect ourselves
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl bind(SocketAddress local)
        throws IOException
    {
        final Socket socket = channel.socket();
        if (socket.isClosed())
            throw new ClosedChannelException();
        if (socket.isBound())
            throw new AlreadyBoundException();
        if ((local != null) && (!(local instanceof InetSocketAddress)))
            throw new UnsupportedAddressTypeException();

        socket.bind(local);
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
        if (! (name instanceof StandardSocketOption))
            throw new IllegalArgumentException("Unsupported option " + name);

        if (value == null || !name.type().isAssignableFrom(value.getClass()))
            throw new IllegalArgumentException("Bad parameter for " + name);

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        switch (stdOpt) {
        case SO_SNDBUF:
            channel.socket().setSendBufferSize(((Integer)value).intValue());
            break;

        case SO_RCVBUF:
            channel.socket().setReceiveBufferSize(((Integer)value).intValue());
            break;

        case SO_KEEPALIVE:
            channel.socket().setKeepAlive(((Boolean)value).booleanValue());
            break;

        case SO_REUSEADDR:
            channel.socket().setReuseAddress(((Boolean)value).booleanValue());
            break;

        case TCP_NODELAY:
            channel.socket().setTcpNoDelay(((Boolean)value).booleanValue());
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
        case SO_SNDBUF:
            return channel.socket().getSendBufferSize();

        case SO_RCVBUF:
            return channel.socket().getReceiveBufferSize();

        case SO_KEEPALIVE:
            return channel.socket().getKeepAlive();

        case SO_REUSEADDR:
            return channel.socket().getReuseAddress();

        case TCP_NODELAY:
            return channel.socket().getTcpNoDelay();

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
    public AsyncSocketChannelImpl shutdown(ShutdownType how)
        throws IOException
    {
        // TODO
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
    public boolean isConnectionPending()
    {
        return connectPending.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending()
    {
        // TODO
        return readPending.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending()
    {
        // TODO
        return writePending.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> connect(
        final SocketAddress remote,
        final A attachment,
        final CompletionHandler<Void, ? super A> handler)
    {
        checkClosedAsync();
        if (! connectPending.compareAndSet(false, true))
            throw new ConnectionPendingException();

        Future<Void> f = channelGroup.getExecutor().submit(
            new Callable<Void>() {
                public Void call() throws IOException {
                    channel.connect(remote);
                    // TODO handler.completed(IoFutureBase(f))
                    return null;
                }
            });
        return null; // TODO IoFutureBase(f)
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> read(ByteBuffer dst, long timeout,
        TimeUnit unit, A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        checkClosedAsync();
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
        checkClosedAsync();
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
        checkClosedAsync();
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
        checkClosedAsync();
        // TODO
        return null;
    }
}
