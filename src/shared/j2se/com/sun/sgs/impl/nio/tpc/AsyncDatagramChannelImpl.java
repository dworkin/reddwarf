/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.tpc;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.MembershipKey;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

class AsyncDatagramChannelImpl
    extends AsynchronousDatagramChannel
{
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_SNDBUF,
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_REUSEADDR,
            StandardSocketOption.SO_BROADCAST,
            StandardSocketOption.IP_TOS,
            StandardSocketOption.IP_MULTICAST_IF,
            StandardSocketOption.IP_MULTICAST_TTL,
            StandardSocketOption.IP_MULTICAST_LOOP);
        socketOptions = Collections.unmodifiableSet(es);
    }

    final AsyncChannelGroupImpl channelGroup;
    final DatagramChannel channel;
    private final Object closeLock = new Object();

    protected AsyncDatagramChannelImpl(
            ThreadedAsyncChannelProvider provider,
            AsyncChannelGroupImpl group)
        throws IOException
    {
        super(provider);
        this.channelGroup = group;
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        // TODO group.register(this);
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
    public AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException
    {
        final DatagramSocket socket = channel.socket();
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
    public AsyncDatagramChannelImpl setOption(SocketOption name, Object value)
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

        case SO_REUSEADDR:
            channel.socket().setReuseAddress(((Boolean)value).booleanValue());
            break;

        case SO_BROADCAST:
            channel.socket().setBroadcast(((Boolean)value).booleanValue());
            break;

        case IP_TOS:
            channel.socket().setTrafficClass(((Integer)value).intValue());
            break;

        case IP_MULTICAST_IF: {
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            msocket.setNetworkInterface((NetworkInterface)value);
            break;
        }

        case IP_MULTICAST_TTL: {
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            msocket.setTimeToLive(((Integer)value).intValue());
            break;
        }

        case IP_MULTICAST_LOOP: {
            // TODO should we reverse the value of this IP_MULTICAST_LOOP?
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            msocket.setLoopbackMode(((Boolean)value).booleanValue());
            break;
        }

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

        case SO_REUSEADDR:
            return channel.socket().getReuseAddress();

        case SO_BROADCAST:
            return channel.socket().getBroadcast();

        case IP_TOS:
            return channel.socket().getTrafficClass();

        case IP_MULTICAST_IF: {
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            return msocket.getNetworkInterface();
        }

        case IP_MULTICAST_TTL: {
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            return msocket.getTimeToLive();
        }

        case IP_MULTICAST_LOOP: {
            // TODO should we reverse the value of this IP_MULTICAST_LOOP?
            MulticastSocket msocket = (MulticastSocket)channel.socket();
            return msocket.getLoopbackMode();
        }

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
    public MembershipKey join(InetAddress group, NetworkInterface interf)
        throws IOException
    {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public MembershipKey join(InetAddress group, NetworkInterface interf,
        InetAddress source) throws IOException
    {
        // TODO
        throw new UnsupportedOperationException();
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
    public <A> IoFuture<Void, A> disconnect(A attachment,
        CompletionHandler<Void, ? super A> handler)
    {
        // TODO
        return null;
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
    public <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        long timeout, TimeUnit unit, A attachment,
        CompletionHandler<SocketAddress, ? super A> handler)
    {
        // TODO
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target, long timeout, TimeUnit unit, A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        // TODO
        return null;
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
    public <A> IoFuture<Integer, A> write(ByteBuffer src, long timeout,
        TimeUnit unit, A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        // TODO
        return null;
    }
}
