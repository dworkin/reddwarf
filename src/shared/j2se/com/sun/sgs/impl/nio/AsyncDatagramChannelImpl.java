/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.MembershipKey;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

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

    final AsyncOp<DatagramChannel> ops;


    AsyncDatagramChannelImpl(AsyncOp<DatagramChannel> ops)
        throws IOException
    {
        super(ops.group().provider());
        this.ops = ops;
    }

    private void checkClosedAsync() {
        if (! ops.isOpen())
            throw new ClosedAsynchronousChannelException();
    }

    private void checkConnected() {
        if (! ops.channel().isConnected())
            throw new NotYetConnectedException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOpen() {
        return ops.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        ops.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException
    {
        final DatagramSocket socket = ops.channel().socket();
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
        return ops.channel().socket().getLocalSocketAddress();
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
        final DatagramSocket socket = ops.channel().socket();
        switch (stdOpt) {
        case SO_SNDBUF:
            socket.setSendBufferSize(((Integer)value).intValue());
            break;

        case SO_RCVBUF:
            socket.setReceiveBufferSize(((Integer)value).intValue());
            break;

        case SO_REUSEADDR:
            socket.setReuseAddress(((Boolean)value).booleanValue());
            break;

        case SO_BROADCAST:
            socket.setBroadcast(((Boolean)value).booleanValue());
            break;

        case IP_TOS:
            socket.setTrafficClass(((Integer)value).intValue());
            break;

        case IP_MULTICAST_IF: {
            MulticastSocket msocket = (MulticastSocket) socket;
            msocket.setNetworkInterface((NetworkInterface)value);
            break;
        }

        case IP_MULTICAST_TTL: {
            MulticastSocket msocket = (MulticastSocket) socket;
            msocket.setTimeToLive(((Integer)value).intValue());
            break;
        }

        case IP_MULTICAST_LOOP: {
            MulticastSocket msocket = (MulticastSocket) socket;
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
        final DatagramSocket socket = ops.channel().socket();
        switch (stdOpt) {
        case SO_SNDBUF:
            return socket.getSendBufferSize();

        case SO_RCVBUF:
            return socket.getReceiveBufferSize();

        case SO_REUSEADDR:
            return socket.getReuseAddress();

        case SO_BROADCAST:
            return socket.getBroadcast();

        case IP_TOS:
            return socket.getTrafficClass();

        case IP_MULTICAST_IF: {
            MulticastSocket msocket = (MulticastSocket) socket;
            return msocket.getNetworkInterface();
        }

        case IP_MULTICAST_TTL: {
            MulticastSocket msocket = (MulticastSocket) socket;
            return msocket.getTimeToLive();
        }

        case IP_MULTICAST_LOOP: {
            // TODO should we reverse the value of this IP_MULTICAST_LOOP?
            MulticastSocket msocket = (MulticastSocket) socket;
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
        return ops.channel().socket().getRemoteSocketAddress();
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
        if (ops.channel().isConnected())
            throw new AlreadyConnectedException();

        // TODO ensure that only one of these is pending at a time

        return ops.submit(0, attachment, handler, 0, TimeUnit.MILLISECONDS,
            new Callable<Void>() {
                public Void call() throws IOException {
                    ops.channel().connect(remote);
                    return null;
                }});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> disconnect(A attachment,
        CompletionHandler<Void, ? super A> handler)
    {
        checkClosedAsync();

        return ops.submit(0, attachment, handler, 0, TimeUnit.MILLISECONDS,
            new Callable<Void>() {
                public Void call() throws IOException {
                    ops.channel().disconnect();
                    return null;
                }});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return ops.isPending(OP_READ);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return ops.isPending(OP_WRITE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<SocketAddress, A> receive(
            final ByteBuffer dst,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<SocketAddress, ? super A> handler)
    {
        checkClosedAsync();

        return ops.submit(
            OP_READ, attachment, handler, timeout, unit,
            new Callable<SocketAddress>() {
                public SocketAddress call() throws IOException {
                    return ops.channel().receive(dst);
            }});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Integer, A> send(
            final ByteBuffer src,
            final SocketAddress target,
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
                    return ops.channel().send(src, target);
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

        return ops.submit(
            OP_READ, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    return ops.channel().read(dst);
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
}
