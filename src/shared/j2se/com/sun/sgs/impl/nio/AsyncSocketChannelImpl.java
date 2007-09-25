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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;
import com.sun.sgs.nio.channels.WritePendingException;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

final class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
    implements AsyncChannelImpl
{
    static final Logger log =
        Logger.getLogger(AsyncSocketChannelImpl.class.getName());

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

    final AtomicReference<AsyncOp<?>> connectTask =
        new AtomicReference<AsyncOp<?>>();
    final AtomicReference<AsyncOp<?>> readTask =
        new AtomicReference<AsyncOp<?>>();
    final AtomicReference<AsyncOp<?>> writeTask =
        new AtomicReference<AsyncOp<?>>();

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
        group.registerChannel(this);
    }

    public SocketChannel channel() {
        return channel;
    }

    public void selected(int ops) {
        log.log(Level.FINER, "selected {0}", ops);

        AsyncOp<?> ctask = null;
        AsyncOp<?> rtask = null;
        AsyncOp<?> wtask = null;

        if ((ops & OP_CONNECT) != 0) {
            ctask = connectTask.getAndSet(null);
            log.log(Level.FINEST, "ctask {0}", ctask);
        }

        if ((ops & OP_READ) != 0) {
            rtask = readTask.getAndSet(null);
            log.log(Level.FINEST, "rtask {0}", rtask);
        }

        if ((ops & OP_WRITE) != 0) {
            wtask = writeTask.getAndSet(null);
            log.log(Level.FINEST, "wtask {0}", wtask);
        }

        if (ctask != null)
            group.execute(ctask);
        if (rtask != null)
            group.execute(rtask);
        if (wtask != null)
            group.execute(wtask);

        log.log(Level.FINER, "selected {0} done", ops);
    }

    public void setException(int ops, Throwable t) {
        log.log(Level.FINER, "setException {0}", ops);

        AsyncOp<?> ctask = null;
        AsyncOp<?> rtask = null;
        AsyncOp<?> wtask = null;

        if ((ops & OP_CONNECT) != 0)
            ctask = connectTask.getAndSet(null);

        if ((ops & OP_READ) != 0)
            rtask = readTask.getAndSet(null);

        if ((ops & OP_WRITE) != 0)
            wtask = writeTask.getAndSet(null);

        if (ctask != null)
            group.setException(ctask, t);
        if (rtask != null)
            group.setException(rtask, t);
        if (wtask != null)
            group.setException(wtask, t);

        log.log(Level.FINER, "setException {0} done", ops);
    }

    private void checkConnected() {
        if (! channel.isOpen())
            throw new ClosedAsynchronousChannelException();
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
        group.unregisterChannel(this);
        try {
            channel.close();
        } finally {
            setException(OP_CONNECT | OP_READ | OP_WRITE,
                new AsynchronousCloseException());
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
        return channel.isConnectionPending();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return readTask.get() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return writeTask.get() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> connect(
        SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler)
    {
        if (! channel.isOpen())
            throw new ClosedAsynchronousChannelException();
        if (channel.isConnected())
            throw new AlreadyConnectedException();

        AsyncOp<Void> task = AsyncOp.create(attachment, handler,
            new Callable<Void>() {
                public Void call() throws IOException {
                    channel.finishConnect();
                    return null;
                }});

        if (! connectTask.compareAndSet(null, task))
            throw new ConnectionPendingException();

        try {
            if (channel.connect(remote)) {
                selected(OP_CONNECT);
            } else {
                group.awaitReady(this, OP_CONNECT);
            }
        } catch (IOException e) {
            group.setException(task, e);
        }
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
        checkConnected();

        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");

        AsyncOp<Integer> task = AsyncOp.create(attachment, handler,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    return channel.read(dst);
                }});

        if (! readTask.compareAndSet(null, task))
            throw new ReadPendingException();

        group.awaitReady(this, OP_READ, timeout, unit);
        return AttachedFuture.wrap(task, attachment);
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
        checkConnected();

        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
        if ((offset < 0) || (offset >= dsts.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (dsts.length - offset)))
            throw new IllegalArgumentException("length out of range");

        AsyncOp<Long> task = AsyncOp.create(attachment, handler,
            new Callable<Long>() {
                public Long call() throws IOException {
                    return channel.read(dsts, offset, length);
                }});

        if (! readTask.compareAndSet(null, task))
            throw new ReadPendingException();

        group.awaitReady(this, OP_READ, timeout, unit);
        return AttachedFuture.wrap(task, attachment);
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
        checkConnected();

        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");

        AsyncOp<Integer> task = AsyncOp.create(attachment, handler,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    return channel.write(src);
                }});

        if (! writeTask.compareAndSet(null, task))
            throw new WritePendingException();

        group.awaitReady(this, OP_WRITE, timeout, unit);
        return AttachedFuture.wrap(task, attachment);
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
        checkConnected();

        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
        if ((offset < 0) || (offset >= srcs.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (srcs.length - offset)))
            throw new IllegalArgumentException("length out of range");

        AsyncOp<Long> task = AsyncOp.create(attachment, handler,
            new Callable<Long>() {
                public Long call() throws IOException {
                    return channel.write(srcs, offset, length);
                }});

        if (! writeTask.compareAndSet(null, task))
            throw new WritePendingException();

        group.awaitReady(this, OP_WRITE, timeout, unit);
        return AttachedFuture.wrap(task, attachment);
    }
}
