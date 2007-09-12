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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;
import com.sun.sgs.nio.channels.ShutdownType;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;
import com.sun.sgs.nio.channels.WritePendingException;

final class AsyncSocketChannelImpl
    extends AsynchronousSocketChannel
    implements AsyncChannelInternals
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

    final AbstractAsyncChannelGroup channelGroup;
    final SocketChannel channel;

    private final AsyncIoTaskFactory connectTask;
    private final AsyncIoTaskFactory readTask;
    private final AsyncIoTaskFactory writeTask;

    // For unconnected channels
    AsyncSocketChannelImpl(AbstractAsyncChannelGroup group)
        throws IOException
    {
        this(group,
             group.getSelectorProvider().openSocketChannel());
    }

    // For channels accepted via AsynchronousServerSocketChannel
    AsyncSocketChannelImpl(AbstractAsyncChannelGroup group,
                           SocketChannel channel)
        throws IOException
    {
        super(group.provider());
        channelGroup = group;
        this.channel = channel;
        connectTask = new AsyncIoTaskFactory(group) {
            @Override protected void alreadyPendingPolicy() {
                throw new ConnectionPendingException();
            }};
        readTask = new AsyncIoTaskFactory(group) {
            @Override protected void alreadyPendingPolicy() {
                throw new ReadPendingException();
            }};
        writeTask = new AsyncIoTaskFactory(group) {
            @Override protected void alreadyPendingPolicy() {
                throw new WritePendingException();
            }};

        try {
            channelGroup.addChannel(this);
        } catch (ShutdownChannelGroupException e) {
            channel.close();
        }
    }

    public SelectableChannel getSelectableChannel() {
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

    private void awaitSelectableOp(long timeout, int ops) throws IOException {
        ((AsyncProviderImpl) provider())
                .awaitSelectableOp(channel, timeout, ops);
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
            channelGroup.channelClosed(this);
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
        return connectTask.isPending();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return readTask.isPending();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return writeTask.isPending();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> connect(
        final SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler)
    {
        checkClosedAsync();
        if (channel.isConnected())
            throw new AlreadyConnectedException();

        return connectTask.submit(attachment, handler, new Callable<Void>() {
            public Void call() throws IOException {
                channel.connect(remote);
                return null;
            }});
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
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");

        final int timeoutMillis = (int) unit.toMillis(timeout);

/*
        return readTask.submit(attachment, handler, new Callable<Integer>() {
            public Integer call() throws IOException {
                if (channel.socket().getSoTimeout() != timeoutMillis)
                    channel.socket().setSoTimeout(timeoutMillis);
                try {
                    return channel.read(dst);
                } catch (SocketTimeoutException e) {
                    throw new AbortedByTimeoutException();
                }
            }});
*/

        return readTask.submit(attachment, handler, new Callable<Integer>() {
            public Integer call() throws IOException {
                awaitSelectableOp(timeoutMillis, SelectionKey.OP_READ);
                return channel.read(dst);
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
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");
        if ((offset < 0) || (offset >= dsts.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (dsts.length - offset)))
            throw new IllegalArgumentException("length out of range");

        final int timeoutMillis = (int) unit.toMillis(timeout);

/*
        return readTask.submit(attachment, handler, new Callable<Long>() {
            public Long call() throws IOException {
                if (channel.socket().getSoTimeout() != timeoutMillis)
                    channel.socket().setSoTimeout(timeoutMillis);
                try {
                    return channel.read(dsts, offset, length);
                } catch (SocketTimeoutException e) {
                    throw new AbortedByTimeoutException();
                }
            }});
*/

        return readTask.submit(attachment, handler, new Callable<Long>() {
            public Long call() throws IOException {
                awaitSelectableOp(timeoutMillis, SelectionKey.OP_READ);
                return channel.read(dsts, offset, length);
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
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");

        final long timeoutMillis = unit.toMillis(timeout);

        return writeTask.submit(attachment, handler, new Callable<Integer>() {
            public Integer call() throws IOException {
                awaitSelectableOp(timeoutMillis, SelectionKey.OP_WRITE);
                return channel.write(src);
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
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");
        if ((offset < 0) || (offset >= srcs.length))
            throw new IllegalArgumentException("offset out of range");
        if ((length < 0) || (length > (srcs.length - offset)))
            throw new IllegalArgumentException("length out of range");

        final long timeoutMillis = unit.toMillis(timeout);

        return writeTask.submit(attachment, handler, new Callable<Long>() {
            public Long call() throws IOException {
                awaitSelectableOp(timeoutMillis, SelectionKey.OP_WRITE);
                return channel.write(srcs, offset, length);
            }});
    }
}
