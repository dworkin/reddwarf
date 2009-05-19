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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * An asynchronous channel for datagram-oriented sockets.
 * <p>
 * An asynchronous datagram channel is created by invoking one of the open
 * methods defined by this class. It is not possible to create a channel for
 * an arbitrary, pre-existing datagram socket. A newly-created asynchronous
 * datagram channel is open but not connected. It need not be connected in
 * order for the send and receive methods to be used. A datagram channel may
 * be connected, by invoking its connect method, in order to avoid the
 * overhead of the security checks that are otherwise performed as part of
 * every send and receive operation when a security manager is set. The
 * channel must be connected in order to use the read and write methods,
 * since those methods do not accept or return socket addresses. Once
 * connected, an asynchronous datagram channel remains connected until it is
 * disconnected or closed.
 * <p>
 * Socket options are configured using the setOption method. Channels of
 * this type support the following options:
 * <blockquote>
 * <table>
 * <tr>
 * <th>Option Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_SNDBUF SO_SNDBUF}</td>
 * <td>The size of the socket send buffer</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_RCVBUF SO_RCVBUF}</td>
 * <td>The size of the socket receive buffer</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_REUSEADDR SO_REUSEADDR}</td>
 * <td>Re-use address</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#SO_BROADCAST SO_BROADCAST}</td>
 * <td>Allow transmission of broadcast datagrams</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#IP_TOS IP_TOS}</td>
 * <td>The Type of Service (ToS) octet in the Internet Protocol (IP) header</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#IP_MULTICAST_IF IP_MULTICAST_IF}</td>
 * <td>The network interface for Internet Protocol (IP) multicast datagrams</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#IP_MULTICAST_TTL IP_MULTICAST_TTL}</td>
 * <td>The <em>time-to-live</em> for Internet Protocol (IP) multicast
 * datagrams</td>
 * </tr>
 * <tr>
 * <td>{@link StandardSocketOption#IP_MULTICAST_LOOP IP_MULTICAST_LOOP}</td>
 * <td>Loopback for Internet Protocol (IP) multicast datagrams</td>
 * </tr>
 * </table>
 * </blockquote>
 * and may support additional (implementation
 * specific) options. The list of options supported is obtained by invoking
 * the options method.
 * <h4>Timeouts</h4>
 * [TBD]
 * <h4>Usage Example:</h4>
 * <pre>
 *   final AsynchronousDatagramChannel dc = AsynchronousDatagramChannel.open()
 *       .bind(new InetSocketAddress(4000));
 * 
 *   // print the source address of all packets that we receive
 *   dc.receive(buffer, buffer, 
 *     new CompletionHandler&lt;SocketAddress,ByteBuffer&gt;() {
 *       public void completed(IoFuture&lt;SocketAddress,ByteBuffer&gt; result)
 *       {
 *           try {
 *                SocketAddress sa = result.getNow();
 *                System.out.println(sa);
 * 
 *                ByteBuffer buffer = result.attachment();
 *                buffer.clear();
 *                dc.receive(buffer, buffer, this); 
 *            } catch (ExecutionException x) { ... }
 *       }
 *   });
 * </pre>
 */
public abstract class AsynchronousDatagramChannel extends AsynchronousChannel
    implements AsynchronousByteChannel, NetworkChannel, MulticastChannel
{
    /**
     * Initializes a new instance of this class.
     * 
     * @param provider the asynchronous channel provider for this channel
     */
    protected AsynchronousDatagramChannel(AsynchronousChannelProvider provider)
    {
        super(provider);
    }

    /**
     * Opens an asynchronous datagram channel.
     * <p>
     * The new channel is created by invoking the
     * openAsynchronousDatagramChannel method on the
     * AsynchronousChannelProvider object that created the given group. If
     * the group parameter is null then the resulting channel is created by
     * the system-wide default provider, and bound to the default group.
     * <p>
     * The pf parameter is used to specify the ProtocolFamily. If the
     * datagram channel is to be used for Internet Protocol multicasting
     * then this parameter should correspond to the address type of the
     * multicast groups that this channel will join.
     * 
     * @param pf the protocol family, or null to use the default protocol family
     * @param group the group to which the newly constructed channel should
     *        be bound, or null for the default group
     * @return a new asynchronous datagram channel
     * @throws ShutdownChannelGroupException if the specified group is
     *         shutdown
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousDatagramChannel
    open(ProtocolFamily pf, AsynchronousChannelGroup group) throws IOException
    {
        return AsynchronousChannelProvider.provider()
                        .openAsynchronousDatagramChannel(pf, group);
    }

    /**
     * Opens an asynchronous datagram channel.
     * <p>
     * This method returns an asynchronous datagram channel that is bound
     * to the default group.This method is equivalent to evaluating the
     * expression:
     * <pre>
     *     open((ProtocolFamily)null, (AsynchronousChannelGroup)null);
     * </pre>
     * @return a new asynchronous datagram channel
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousDatagramChannel open() throws IOException {
        return open((ProtocolFamily) null, (AsynchronousChannelGroup) null);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws SecurityException if a security manager exists and its
     *         {@code checkListen} method doesn't allow the operation
     */
    public abstract AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException;

    /**
     * {@inheritDoc}
     */
    public abstract
    AsynchronousDatagramChannel setOption(SocketOption name, Object value)
        throws IOException;

    /**
     * Returns the remote address to which this channel is connected, or
     * null if the channel is not connected.
     * 
     * @return the remote address; null if the channel is not open or the
     *         channel's socket is not connected
     * @throws IOException if an I/O error occurs
     */
    public abstract SocketAddress getConnectedAddress()
    throws IOException;

    /**
     * Tells whether or not a read is pending for this channel.
     * <p>
     * The result of this method is a snapshot of the channel state. It may
     * be invalid when the caller goes to examine the result and should not
     * be used for purposes of coordination.
     * 
     * @return true, if and only if, a read is pending for this channel but
     *         has not yet completed.
     * @see ReadPendingException
     */
    public abstract boolean isReadPending();
    
    /**
     * Tells whether or not a write is pending for this channel.
     * <p>
     * The result of this method is a snapshot of the channel state. It may
     * be invalid when the caller goes to examine the result and should not
     * be used for purposes of coordination.
     * 
     * @return true, if and only if, a write is pending for this channel but
     *         has not yet completed.
     * @see WritePendingException
     */
    public abstract boolean isWritePending();

    /**
     * Connects this channel.
     * <p>
     * This method initiates an operation to configure the channel so that
     * it only receives datagrams from, and sends datagrams to, the given
     * remote <em>peer</em> address. Once connected, datagrams may not be
     * received from or sent to any other address. The channel remains
     * connected until it is
     * {@link #disconnect(Object, CompletionHandler) disconnected} or until
     * it is closed. This method returns an {@code IoFuture} representing
     * the pending result of the operation. The {@link IoFuture#get() get}
     * method returns {@code null} upon successful completion.
     * <p>
     * This method performs exactly the same security checks as the
     * {@link DatagramSocket#connect(java.net.InetAddress, int) connect}
     * method of the {@link DatagramSocket} class. That is, if a security
     * manager has been installed then this method verifies that its
     * {@link SecurityManager#checkAccept checkAccept} and
     * {@link SecurityManager#checkConnect(String, int) checkConnect}
     * methods permit datagrams to be received from and sent to,
     * respectively, the given remote address.
     * <p>
     * This method may be invoked at any time and may thus affect read or
     * write operations that are already in progress at the moment that the
     * socket is connected.
     * 
     * @param <A> the attachment type
     * @param remote the remote address to which this channel is to be
     *        connected
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * 
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ConnectionPendingException if a connection operation is
     *         already in progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the given remote address
     */
    public abstract <A> IoFuture<Void, A> connect(SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler);

    /**
     * Connects this channel.
     * <p>
     * This method initiates an operation to configure the channel so that
     * it only receives datagrams from, and sends datagrams to, the given
     * remote <em>peer</em> address. Once connected, datagrams may not be
     * received from or sent to any other address. The channel remains
     * connected until it is
     * {@link #disconnect(Object, CompletionHandler) disconnected} or until
     * it is closed. This method returns an {@code IoFuture} representing
     * the pending result of the operation. The {@link IoFuture#get() get}
     * method returns {@code null} upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #connect(SocketAddress,Object,CompletionHandler)} with an
     * attachment of {@code null}.
     * 
     * @param <A> the attachment type
     * @param remote the remote address to which this channel is to be
     *        connected
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * 
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ConnectionPendingException if a connection operation is
     *         already in progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit access to the given remote address
     */
    public final <A> IoFuture<Void, A> connect(SocketAddress remote,
        CompletionHandler<Void, ? super A> handler)
    {
        return connect(remote, null, handler);
    }

    /**
     * Disconnects this channel.
     * <p>
     * This method initiates an operation to configure the channel so that
     * it can receive datagrams from, and sends datagrams to, any remote
     * address so long as the security manager, if installed, permits it.
     * This method returns an {@code IoFuture} representing
     * the pending result of the operation. The {@link IoFuture#get() get}
     * method returns {@code null} upon successful completion.
     * <p>
     * This method may be invoked at any time and may thus affect read or
     * write operations that are already in progress at the moment that the
     * socket is disconnected.
     * <p>
     * If this channel's is not connected then the operation completes
     * succesfully (meaning that the completion handler is invoked, and the
     * {@code IoFuture}'s {@code get} method returns {@code null}).
     * 
     * @param <A> the attachment type
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     */
    public abstract <A> IoFuture<Void, A> disconnect(A attachment,
        CompletionHandler<Void, ? super A> handler);

    /**
     * Disconnects this channel.
     * <p>
     * This method initiates an operation to configure the channel so that
     * it can receive datagrams from, and sends datagrams to, any remote
     * address so long as the security manager, if installed, permits it.
     * This method returns an {@code IoFuture} representing the pending
     * result of the operation. The {@link IoFuture#get() get} method
     * returns {@code null} upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #disconnect(Object,CompletionHandler)} with an attachment of
     * {@code null}.
     * 
     * @param <A> the attachment type
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     */
    public final <A> IoFuture<Void, A> 
            disconnect(CompletionHandler<Void, ? super A> handler)
    {
        return disconnect(null, handler);
    }

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * source address of the datagram upon successful completion.
     * <p>
     * The datagram is transferred into the given byte buffer starting at
     * its current position, as if by a regular {@link 
     *  AsynchronousByteChannel#read(ByteBuffer, Object, 
     *  CompletionHandler) read}
     * operation. If there are fewer bytes remaining in the buffer than are
     * required to hold the datagram then the remainder of the datagram is
     * silently discarded.
     * <p>
     * When a security manager has been installed and the channel is not
     * connected, then it verifies that the source's address and port number
     * are permitted by the security manager's
     * {@link SecurityManager#checkAccept checkAccept} method. The
     * permission check is performed with privileges that are restricted by
     * the calling context of this method. If the permission check fails
     * then the operation completes by throwing {@link ExecutionException}
     * with cause {@link SecurityException}. The overhead of this security
     * check can be avoided by first connecting the socket via the
     * {@link #connect(SocketAddress, Object, CompletionHandler) connect}
     * method.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param timeout the timeout, or {@code 0L} for no timeout
     * @param unit the time unit of the {@code timeout} argument
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     */
    public abstract <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<SocketAddress, ? super A> handler);

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * source address of the datagram upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #receive(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     */
    public final <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        A attachment,
        CompletionHandler<SocketAddress, ? super A> handler)
    {
        return receive(dst, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * source address of the datagram upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #receive(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}, and an attachment of {@code null}.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     */
    public final <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        CompletionHandler<SocketAddress, ? super A> handler)
    {
        return receive(dst, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    /**
     * Sends a datagram via this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram to the given target address. The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * The datagram is transferred from the byte buffer as if by a regular
     * {@link AsynchronousByteChannel#write(ByteBuffer, 
     *      Object, CompletionHandler) write}
     * operation.
     * <p>
     * If there is a security manager installed and the the channel is not
     * connected then this method verifies that the target address and port
     * number are permitted by the security manager's
     * {@link SecurityManager#checkConnect(String, int) checkConnect}
     * method. The overhead of this security check can be avoided by first
     * connecting the socket via the
     * {@link #connect(SocketAddress, Object, CompletionHandler) connect}
     * method.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param target the address to which the datagram is to be sent
     * @param timeout the timeout, or {@code 0L} for no timeout
     * @param unit the time unit of the {@code timeout} argument
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit datagrams to be sent to the given
     *         address
     */
    public abstract <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Sends a datagram via this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram to the given target address. The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * This method is equivalent to invoking {@link #send(ByteBuffer, 
     *  SocketAddress, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param target the address to which the datagram is to be sent
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit datagrams to be sent to the given
     *         address
     */
    public final <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return send(src, target, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Sends a datagram via this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram to the given target address. The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * This method is equivalent to invoking {@link #send(ByteBuffer, 
     *   SocketAddress, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}, and an attachment of {@code null}.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param target the address to which the datagram is to be sent
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a write operation is already in
     *         progress on this channel
     * @throws UnresolvedAddressException if the given remote address is not
     *         fully resolved
     * @throws UnsupportedAddressTypeException if the type of the given
     *         remote address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and it does not permit datagrams to be sent to the given
     *         address
     */
    public final <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        CompletionHandler<Integer, ? super A> handler)
    {
        return send(src, target, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * the number of bytes transferred upon successful completion.
     * <p>
     * This method may only be invoked if this channel is connected, and it
     * only accepts datagrams from the peer that the channel is connected
     * too. The datagram is transferred into the given byte buffer starting
     * at its current position and exactly as specified in the
     * {@link AsynchronousByteChannel} interface. If there are fewer bytes
     * remaining in the buffer than are required to hold the datagram then
     * the remainder of the datagram is silently discarded.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param timeout the timeout, or {@code 0L} for no timeout
     * @param unit the time unit of the {@code timeout} argument
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public abstract <A> IoFuture<Integer, A> read(ByteBuffer dst,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * the number of bytes transferred upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #read(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Receives a datagram via this channel.
     * <p>
     * This method initiates the receiving of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The {@code IoFuture}'s {@link IoFuture#get() get} method returns the
     * the number of bytes transferred upon successful completion.
     * <p>
     * This method is equivalent to invoking
     * {@link #read(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}, and an attachment of {@code null}.
     * 
     * @param <A> the attachment type
     * @param dst the buffer into which the datagram is to be transferred
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws ReadPendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    /**
     * Writes a datagram to this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram.  The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * The datagram is transferred from the byte buffer as if by a regular
     * {@link AsynchronousByteChannel#write(
     *     ByteBuffer, Object, CompletionHandler) write}
     * operation.
     * <p>
     * This method may only be invoked if this channel is connected, in
     * which case it sends datagrams directly to the socket's peer.
     * Otherwise it behaves exactly as specified in the
     * {@link AsynchronousByteChannel} interface.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param timeout the timeout, or {@code 0L} for no timeout
     * @param unit the time unit of the {@code timeout} argument
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public abstract <A> IoFuture<Integer, A> write(ByteBuffer src,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Writes a datagram to this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram.  The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * This method is equivalent to invoking
     * {@link #write(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *        returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    /**
     * Writes a datagram to this channel.
     * <p>
     * This method initiates sending of a datagram, returning an
     * {@code IoFuture} representing the pending result of the operation.
     * The operation sends the remaining bytes in the given buffer as a
     * single datagram.  The result of the
     * operation, obtained by invoking the {@code IoFuture}'s
     * {@link IoFuture#get() get} method, is the number of bytes sent. The
     * number of bytes sent is either the number of bytes that were
     * remaining in the buffer or zero if there was insufficient room for
     * the datagram in the underlying output buffer.
     * <p>
     * This method is equivalent to invoking
     * {@link #write(ByteBuffer, long, TimeUnit, Object, CompletionHandler)}
     * with a timeout of {@code 0L}, and an attachment of {@code null}.
     * 
     * @param <A> the attachment type
     * @param src the buffer containing the datagram to be sent
     * @param handler the handler for consuming the result; can be
     *        {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws WritePendingException if a read operation is already in
     *         progress on this channel
     * @throws NotYetConnectedException if this channel is not connected
     */
    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, null, handler);
    }


}
