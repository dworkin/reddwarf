/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

final class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
    implements AsyncChannelImpl
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

    final AbstractAsyncChannelGroup group;
    final SocketChannel channel;

    final Object stateLock = new Object();

    volatile FutureTask<?> connectTask;
    volatile FutureTask<?> readTask;
    volatile FutureTask<?> writeTask;

    AsyncSocketChannelImpl(AbstractAsyncChannelGroup group)
        throws IOException
    {
        this(group, group.selectorProvider().openSocketChannel());
    }

    AsyncSocketChannelImpl(AbstractAsyncChannelGroup group, SocketChannel channel)
        throws IOException
    {
        super(group.provider());
        this.group = group;
        this.channel = channel;
        try {
            group.registerChannel(this);
        } catch (ShutdownChannelGroupException e) {
            channel.close();
        }
    }

    public SocketChannel channel() {
        return channel;
    }

    private void checkClosedAsync() {
        if (! channel.isOpen())
            throw new ClosedAsynchronousChannelException();
    }

    private void checkConnected() {
        if (! channel.isConnected())
            throw new NotYetConnectedException();
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
        channel.close();
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
        final Socket socket = channel.socket();
        switch (stdOpt) {
        case SO_SNDBUF:
            socket.setSendBufferSize(((Integer)value).intValue());
            break;

        case SO_RCVBUF:
            socket.setReceiveBufferSize(((Integer)value).intValue());
            break;

        case SO_KEEPALIVE:
            socket.setKeepAlive(((Boolean)value).booleanValue());
            break;

        case SO_REUSEADDR:
            socket.setReuseAddress(((Boolean)value).booleanValue());
            break;

        case TCP_NODELAY:
            socket.setTcpNoDelay(((Boolean)value).booleanValue());
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
        final Socket socket = channel.socket();
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
        if (! channel.isOpen())
            throw new ClosedChannelException();
        checkConnected();

        final Socket socket = channel.socket();
        if (how == ShutdownType.READ  || how == ShutdownType.BOTH) {
            if (! socket.isInputShutdown())
                socket.shutdownInput();
        }
        if (how == ShutdownType.WRITE || how == ShutdownType.BOTH) {
            if (! socket.isOutputShutdown())
                socket.shutdownOutput();            
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
        return connectTask != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return readTask != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return writeTask != null;
    }

    static <R, A> void
    runCompletion(CompletionHandler<R, A> handler,
                  A attachment,
                  Future<R> future)
    {
        if (handler == null)
            return;
        handler.completed(AttachedFuture.wrap(future, attachment));
    }

    void selected(int ops) {
        if ((ops & OP_CONNECT) != 0 && connectTask != null) {
            connectTask.run();
        }
        if ((ops & OP_READ) != 0 && readTask != null) {
            readTask.run();
        }
        if ((ops & OP_WRITE) != 0 && writeTask != null) {
            writeTask.run();
        }
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

        FutureTask<Void> task = new FutureTask<Void>(
            new Callable<Void>() {
                public Void call() throws IOException {
                    channel.connect(remote);
                    return null;
               }})
            {
                @Override
                protected void done()
                {
                    connectTask = null;
                    runCompletion(handler, attachment, this);
                }
            };

        synchronized (stateLock) {
            checkClosedAsync();
            if (channel.isConnected())
                throw new AlreadyConnectedException();
            if (isConnectionPending())
                throw new ConnectionPendingException();

             connectTask = task;
        }

        group.submit(this, OP_CONNECT);

        return AttachedFuture.wrap(task, attachment);
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
        checkClosedAsync();
        checkConnected();

        return ops.submit(OP_READ, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    return ops.channel().read(dst);
                }});
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
        checkClosedAsync();
        checkConnected();
        if ((offset < 0) || (offset >= dsts.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (dsts.length - offset)))
            throw new IllegalArgumentException("length out of range");

        return ops.submit(
            OP_READ, attachment, handler, timeout, unit,
            new Callable<Long>() {
                public Long call() throws IOException {
                    return ops.channel().read(dsts, offset, length);
                }});
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
        checkClosedAsync();
        checkConnected();

        return ops.submit(
            OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    return ops.channel().write(src);
                }});
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
        checkClosedAsync();
        checkConnected();
        if ((offset < 0) || (offset >= srcs.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (srcs.length - offset)))
            throw new IllegalArgumentException("length out of range");

        return ops.submit(
            OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Long>() {
                public Long call() throws IOException {
                    return ops.channel().write(srcs, offset, length);
                }});
    }
}
