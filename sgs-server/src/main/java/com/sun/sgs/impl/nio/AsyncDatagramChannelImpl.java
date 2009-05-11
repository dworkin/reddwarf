/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.MembershipKey;
import com.sun.sgs.nio.channels.MulticastChannel;
import com.sun.sgs.nio.channels.ProtocolFamily;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardProtocolFamily;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * An implementation of {@link AsynchronousDatagramChannel}.
 * Most interesting methods are delegated to the {@link AsyncKey}
 * returned by this channel's channel group.
 * <p>
 * Also implements the creation and management of multicast
 * {@link MembershipKey}s for this channel.
 */
class AsyncDatagramChannelImpl
    extends AsynchronousDatagramChannel
{
    /** The valid socket options for this channel. */
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

    /** The valid protocol families for this channel. */
    private static final Set<ProtocolFamily> protocolFamilies;
    static {
        Set<? extends ProtocolFamily> pfs = EnumSet.of(
            StandardProtocolFamily.INET,
            StandardProtocolFamily.INET6);
        protocolFamilies = Collections.unmodifiableSet(pfs);
    }

    /**
     * The default protocol family if none is specified:
     * {@link StandardProtocolFamily#INET}
     */
    static final ProtocolFamily
        DEFAULT_PROTOCOL_FAMILY = StandardProtocolFamily.INET;

    /** The underlying {@code DatagramChannel}. */
    final DatagramChannel channel;

    /** The {@code AsyncKey} for the underlying channel. */
    final AsyncKey key;

    /** The {@code ProtocolFamily} for this channel. */
    final ProtocolFamily protocolFamily;

    /** The set of multicast membership keys for this channel. */
    final ConcurrentHashMap<MembershipKeyImpl, MembershipKeyImpl> mcastKeys =
        new ConcurrentHashMap<MembershipKeyImpl, MembershipKeyImpl>();

    /** Indicates whether a connect operation is pending on this channel. */
    final AtomicBoolean connectionPending = new AtomicBoolean();

    /**
     * Creates a new instance registered with the given channel group.
     * If this channel is used for multicast, the protocol family should
     * be specified to match that of the multicast group.  If {@code null}
     * is specified as the protocol family, the default family is used.
     * 
     * @param group the channel group
     * @param pf the protocol family, or {@code null} for the default
     *        protocol family
     * @throws IOException if an I/O error occurs
     */
    AsyncDatagramChannelImpl(ProtocolFamily pf, AsyncGroupImpl group)
        throws IOException
    {
        super(group.provider());

        if ((pf != null) && (!protocolFamilies.contains(pf))) {
            throw new UnsupportedOperationException(
                "unsupported protocol family");
        }

        protocolFamily = (pf != null) ? pf : DEFAULT_PROTOCOL_FAMILY;

        channel = group.selectorProvider().openDatagramChannel();
        key = group.register(channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + ":" + key;
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
        key.close();
        mcastKeys.clear();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method performs exactly the same security checks as the bind
     * method of the DatagramSocket class. That is, if a security manager
     * has been installed then this method verifies that its checkListen
     * method permits waiting for a connection request on the specified
     * local port number.
     * 
     * @throws SecurityException if a security manager exists and its
     *         {@link SecurityManager#checkListen checkListen} method
     *         doesn't allow the operation.
     */
    @Override
    public AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException
    {
        if ((local != null) && (!(local instanceof InetSocketAddress))) {
            throw new UnsupportedAddressTypeException();
        }

        InetSocketAddress inetLocal = (InetSocketAddress) local;
        if ((inetLocal != null) && inetLocal.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        final DatagramSocket socket = channel.socket();
        try {
            socket.bind(inetLocal);
        } catch (SocketException e) {
            if (socket.isBound()) {
                throw Util.initCause(new AlreadyBoundException(), e);
            }
            if (socket.isClosed()) {
                throw Util.initCause(new ClosedChannelException(), e);
            }
            throw e;
        }

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
        if (!(name instanceof StandardSocketOption)) {
            throw new IllegalArgumentException("Unsupported option " + name);
        }

        if (value == null || !name.type().isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Bad parameter for " + name);
        }

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final DatagramSocket socket = channel.socket();
        
        try {
            switch (stdOpt) {
            case SO_SNDBUF:
                socket.setSendBufferSize(((Integer) value).intValue());
                break;

            case SO_RCVBUF:
                socket.setReceiveBufferSize(((Integer) value).intValue());
                break;

            case SO_REUSEADDR:
                socket.setReuseAddress(((Boolean) value).booleanValue());
                break;

            case SO_BROADCAST:
                socket.setBroadcast(((Boolean) value).booleanValue());
                break;

            case IP_TOS:
                socket.setTrafficClass(((Integer) value).intValue());
                break;

            case IP_MULTICAST_IF: 
                ((MulticastSocket) socket).
                    setNetworkInterface((NetworkInterface) value);
                break;

            case IP_MULTICAST_TTL: 
                ((MulticastSocket) socket).
                    setTimeToLive(((Integer) value).intValue());
                break;

            case IP_MULTICAST_LOOP: 
                // TODO should we reverse the sense of setLoopbackMode? -JM
                ((MulticastSocket) socket).
                    setLoopbackMode(((Boolean) value).booleanValue());
                break;
            

            default:
                throw new IllegalArgumentException("Unsupported option " +
                    name);
            }
        } catch (SocketException e) {
            if (socket.isClosed()) {
                throw Util.initCause(new ClosedChannelException(), e);
            }
            throw e;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Object getOption(SocketOption name) throws IOException {
        if (!(name instanceof StandardSocketOption)) {
            throw new IllegalArgumentException("Unsupported option " + name);
        }

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final DatagramSocket socket = channel.socket();
        MulticastSocket msocket;
        
        try {
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

            case IP_MULTICAST_IF: 
                msocket = (MulticastSocket) socket;
                return msocket.getNetworkInterface();

            case IP_MULTICAST_TTL: 
                msocket = (MulticastSocket) socket;
                return msocket.getTimeToLive();

            case IP_MULTICAST_LOOP: 
                msocket = (MulticastSocket) socket;
                // TODO should we reverse the sense of getLoopbackMode? -JM
                return (msocket.getLoopbackMode());

            default:
                throw new IllegalArgumentException("Unsupported option " +
                    name);
            }
        } catch (SocketException e) {
            if (socket.isClosed()) {
                throw Util.initCause(new ClosedChannelException(), e);
            }
            throw e;
        }
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
        if (!(channel.socket() instanceof MulticastSocket)) {
            throw new UnsupportedOperationException("not a multicast socket");
        }

        MulticastSocket msocket = ((MulticastSocket) channel.socket());

        if (group == null) {
            throw new NullPointerException("null multicast group");
        }

        if (interf == null) {
            throw new NullPointerException("null interface");
        }

        if (!group.isMulticastAddress()) {
            throw new UnsupportedAddressTypeException();
        }

        if (protocolFamily == StandardProtocolFamily.INET) {
            if (!((group instanceof Inet4Address) ||
                   ((group instanceof Inet6Address) &&
                       ((Inet6Address) group).isIPv4CompatibleAddress()))) {
                throw new UnsupportedAddressTypeException();
            }
        } else if (protocolFamily == StandardProtocolFamily.INET6) {
            if (!(group instanceof Inet6Address)) {
                throw new UnsupportedAddressTypeException();
            }
        }

        InetSocketAddress mcastaddr = new InetSocketAddress(group, 0);
        MembershipKeyImpl newKey = new MembershipKeyImpl(mcastaddr, interf);

        MembershipKeyImpl existingKey =
            mcastKeys.putIfAbsent(newKey, newKey);

        if (existingKey != null) {
            return existingKey;
        }

        boolean success = false;
        try {
            msocket.joinGroup(mcastaddr, interf);
            success = true;
            return newKey;
        } finally {
            if (!success) {
                mcastKeys.remove(newKey);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always throws {@code UnsupportedOperationException}.
     */
    public MembershipKey join(InetAddress group, NetworkInterface interf,
        InetAddress source) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getConnectedAddress() throws IOException
    {
        return channel.socket().getRemoteSocketAddress();
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
        if ((remote != null) && (!(remote instanceof InetSocketAddress))) {
            throw new UnsupportedAddressTypeException();
        }

        InetSocketAddress inetRemote = (InetSocketAddress) remote;
        if ((inetRemote != null) && inetRemote.isUnresolved()) {
            throw new UnresolvedAddressException();
        }

        FutureTask<Void> task = new FutureTask<Void>(
            new Callable<Void>() {
                public Void call() throws IOException {
                    try {
                        channel.connect(remote);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new ClosedAsynchronousChannelException(), e);
                    }
                    return null;
                } })
            {
                @Override protected void done() {
                    connectionPending.set(false);
                    key.runCompletion(handler, attachment, this);
                }
            };

        if (!channel.isOpen()) {
            throw new ClosedAsynchronousChannelException();
        }

        if (!connectionPending.compareAndSet(false, true)) {
            throw new ConnectionPendingException();
        }

        key.execute(task);
        return AttachedFuture.wrap(task, attachment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<Void, A> disconnect(final A attachment,
        final CompletionHandler<Void, ? super A> handler)
    {
        FutureTask<Void> task = new FutureTask<Void>(
            new Callable<Void>() {
                public Void call() throws IOException {
                    if (!channel.isOpen()) {
                        throw new ClosedAsynchronousChannelException();
                    }

                    channel.disconnect();
                    return null;
                } })
            {
                @Override protected void done() {
                    key.runCompletion(handler, attachment, this);
                }
            };

        if (!channel.isOpen()) {
            throw new ClosedAsynchronousChannelException();
        }

        key.execute(task);
        return AttachedFuture.wrap(task, attachment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadPending() {
        return key.isOpPending(OP_READ);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWritePending() {
        return key.isOpPending(OP_WRITE);
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
        return key.execute(OP_READ, attachment, handler, timeout, unit,
            new Callable<SocketAddress>() {
                public SocketAddress call() throws IOException {
                    try {
                        return channel.receive(dst);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
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
        return key.execute(OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    try {
                        return channel.send(src, target);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
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
        return key.execute(OP_READ, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    try {
                        return channel.read(dst);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
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
        return key.execute(OP_WRITE, attachment, handler, timeout, unit,
            new Callable<Integer>() {
                public Integer call() throws IOException {
                    try {
                        return channel.write(src);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }

    /**
     * A simple multicast membership key for datagram channels.
     * Does <strong>not</strong> support source-specific filtering.
     */
    final class MembershipKeyImpl extends MembershipKey {

        /** The multicast address for this group. */
        private final InetSocketAddress mcastaddr;

        /** The network interface for this group. */
        private final NetworkInterface netIf;

        /**
         * Creates a new membership key for the group.
         * 
         * @param mcastaddr the multicast address of the group
         * @param netIf the network interface of the group
         */
        MembershipKeyImpl(InetSocketAddress mcastaddr,
                          NetworkInterface netIf)
        {
            this.mcastaddr = mcastaddr;
            this.netIf = netIf;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isValid() {
            return mcastKeys.contains(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void drop() throws IOException {
            mcastKeys.remove(this);
            MulticastSocket msocket = (MulticastSocket) channel.socket();
            msocket.leaveGroup(mcastaddr, netIf);
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation always throws {@code 
         * UnsupportedOperationException}.
         */
        @Override
        public MembershipKey block(InetAddress source) throws IOException {
            if (source == null) {
                throw new NullPointerException("null source");
            }
            throw new UnsupportedOperationException(
                "source filtering not supported");
        }

        /**
         * {@inheritDoc}
         * 
         * This implementation always throws
         * {@code UnsupportedOperationException}.
         */
        @Override
        public MembershipKey unblock(InetAddress source) throws IOException {
            if (source == null) {
                throw new NullPointerException("null source");
            }
            throw new IllegalStateException(
                "source filtering not supported, so none are blocked");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MulticastChannel getChannel() {
            return AsyncDatagramChannelImpl.this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public InetAddress getGroup() {
            return mcastaddr.getAddress();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NetworkInterface getNetworkInterface() {
            return netIf;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation is not source-specific, so always
         * returns {@code null}.
         */
        @Override
        public InetAddress getSourceAddress() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof MembershipKey)) {
                return false;
            }
            MembershipKey o = (MembershipKey) obj;
            return getChannel().equals(o.getChannel()) &&
                   getGroup().equals(o.getGroup())     &&
                   getNetworkInterface().equals(o.getNetworkInterface());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return getChannel().hashCode() ^
                   getGroup().hashCode()   ^
                   getNetworkInterface().hashCode();
        }
    }
}
