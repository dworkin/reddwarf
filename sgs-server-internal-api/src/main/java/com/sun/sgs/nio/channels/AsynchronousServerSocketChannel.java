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
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.ExecutionException;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * An asynchronous channel for stream-oriented listening sockets.
 * <p>
 * An asynchronous server-socket channel is created by invoking the
 * {@link AsynchronousServerSocketChannel#open(AsynchronousChannelGroup) open}
 * method of this class. A newly-created asynchronous server-socket channel
 * is open but not yet bound. It can be bound to a local address and
 * configured to listen for connections by invoking the
 * {@link AsynchronousServerSocketChannel#bind(SocketAddress, int) bind}
 * method. Once bound, the
 * {@link AsynchronousServerSocketChannel#accept(Object, CompletionHandler)
 * accept} method is used to initiate the accepting of connections
 * to the channel's socket. An attempt to invoke the {@code accept} method on
 * an unbound channel will cause a {@link NotYetBoundException} to be thrown.
 * <p>
 * Channels of this type are safe for use by multiple concurrent threads
 * though at most one accept operation can be outstanding at any time. If a
 * thread initiates an accept operation before a previous accept operation
 * has completed then an {@link AcceptPendingException} will be thrown.
 * Whether or not an operation is pending may be determined by invoking the
 * {@link AsynchronousServerSocketChannel#isAcceptPending isAcceptPending}
 * method.
 * <p>
 * Socket options are configured using the
 * {@link AsynchronousServerSocketChannel#setOption setOption} method.
 * Channels of this type support the following options:
 * <blockquote>
 *   <table>
 *     <tr>
 *       <th>Option Name</th>
 *       <th>Description</th>
 *     </tr>
 *     <tr>
 *       <td>{@link StandardSocketOption#SO_RCVBUF SO_RCVBUF}</td>
 *       <td>The size of the socket receive buffer</td>
 *     </tr>
 *     <tr>
 *       <td>{@link StandardSocketOption#SO_REUSEADDR SO_REUSEADDR}</td>
 *       <td>Re-use address</td>
 *     </tr>
 *   </table>
 * </blockquote>
 * and may support additional (implementation specific) options. The list of
 * options supported is obtained by invoking the
 * {@link NetworkChannel#options options} method.
 * 
 * <h4>Usage Example:</h4>
 * <pre>
 *   final AsynchronousServerSocketChannel listener = 
 *       AsynchronousServerSocketChannel.open().bind(
 *           new InetSocketAddress(5000));
 * 
 *   listener.accept(
 *     new CompletionHandler&lt;AsynchronousSocketChannel,Void&gt;() {
 *       public void completed(IoFuture&lt;AsynchronousSocketChannel,Void&gt; 
 *                             result) 
 *       {
 *           try {
 *               AsynchronousSocketChannel ch = result.getNow();
 *               :
 *           } catch (ExecutionException x) { .. } 
 * 
 *           // accept the next connection
 *           listener.accept(this);
 *       }
 *   });
 * </pre>
 */
public abstract class AsynchronousServerSocketChannel
    extends AsynchronousChannel implements NetworkChannel
{
    /**
     * Initializes a new instance of this class.
     * 
     * @param provider the asynchronous channel provider for this channel
     */
    protected AsynchronousServerSocketChannel(
                                AsynchronousChannelProvider provider)
    {
        super(provider);
    }

    /**
     * Opens an asynchronous server-socket channel.
     * <p>
     * The new channel is created by invoking the
     * {@link AsynchronousChannelProvider#openAsynchronousServerSocketChannel
     * openAsynchronousServerSocketChannel} method on the
     * {@link AsynchronousChannelProvider} object that created the given
     * group. If the group parameter is {@code null} then the resulting
     * channel is created by the system-wide default provider, and bound
     * to the <em>default group</em>.
     * 
     * @param group the group to which the newly constructed channel should
     *        be bound, or {@code null} for the default group
     * @return a new asynchronous server socket channel
     * @throws ShutdownChannelGroupException the specified group is shutdown
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousServerSocketChannel open(
        AsynchronousChannelGroup group) throws IOException
    {
        return AsynchronousChannelProvider.provider()
            .openAsynchronousServerSocketChannel(group);
    }

    /**
     * Opens an asynchronous server-socket channel.
     * <p>
     * This method returns an asynchronous server socket channel that is
     * bound to the <em>default group</em>. This method is equivalent to
     * evaluating the expression:
     * <pre>
     *     open((AsynchronousChannelGroup)null);
     * </pre>
     * 
     * @return a new asynchronous server socket channel
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousServerSocketChannel open() throws IOException {
        return open((AsynchronousChannelGroup) null);
    }

    /**
     * Binds the channel's socket to a local address and configures the
     * socket to listen for connections.
     * <p>
     * This method works as if invoking it were equivalent to evaluating the
     * expression:
     * <pre>
     *         bind(local, 0);
     * </pre>
     *
     * @param local the local address to bind the socket, or {@code null}
     *               to bind to an automatically assigned socket address
     * @return this channel
     * @throws AlreadyBoundException if the socket is already bound
     * @throws UnsupportedAddressTypeException if the type of the given
     *         address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and its checkListen method denies the operation
     * @throws ClosedChannelException if the channel is closed
     * @throws IOException if some other I/O error occurs
     */
    public final AsynchronousServerSocketChannel bind(SocketAddress local)
        throws IOException
    {
        return bind(local, 0);
    }

    /**
     * Binds the channel's socket to a local address and configures the
     * socket to listen for connections.
     * <p>
     * This method is used to establish an association between the socket
     * and a local address. Once an association is established then the
     * socket remains bound until the associated channel is closed. An
     * attempt to bind a socket that is already bound throws
     * AlreadyBoundException. If the local parameter has the value null then
     * the socket address is assigned automatically.
     * <p>
     * The backlog parameter is the maximum number of pending connections on
     * the socket. Its exact semantics are implementation specific. An
     * implementation may impose an implementation specific maximum length
     * or may choose to ignore the parameter. If the backlog parameter has
     * the value 0, or a negative value, then an implementation specific
     * default is used.
     *
     * @param local the local address to bind the socket, or {@code null}
     *               to bind to an automatically assigned socket address
     * @param backlog the maximum number number of pending connections
     * @return this channel
     * @throws AlreadyBoundException if the socket is already bound
     * @throws UnsupportedAddressTypeException if the type of the given
     *         address is not supported
     * @throws SecurityException if a security manager has been installed
     *         and its checkListen method denies the operation
     * @throws ClosedChannelException if the channel is closed
     * @throws IOException if some other I/O error occurs
     */
    public abstract AsynchronousServerSocketChannel bind(SocketAddress local,
        int backlog) throws IOException;

    /**
     * {@inheritDoc}
     */
    public abstract AsynchronousServerSocketChannel setOption(
        SocketOption name, Object value) throws IOException;

    /**
     * Accepts a connection.
     * <p>
     * This method initiates accepting a connection made to this channel's
     * socket, returning an {@link IoFuture} representing the pending result
     * of the operation. The {@code IoFuture}'s {@link IoFuture#get() get}
     * method will return the {@link AsynchronousSocketChannel} for the new
     * connection on successful completion.
     * <p>
     * When a new connection is accepted then the resulting
     * {@code AsynchronousSocketChannel} will be bound to the same
     * {@link AsynchronousChannelGroup} as this channel.
     * <p>
     * If a security manager has been installed then it verifies that the
     * address and port number of the connection's remote endpoint are
     * permitted by the security manager's {@link SecurityManager#checkAccept
     * checkAccept} method. The permission check is performed with privileges
     * that are restricted by the calling context of this method. If the
     * permission check fails then the operation completes by throwing
     * {@link ExecutionException} with cause {@link SecurityException}.
     * 
     * @param <A> the attachment type
     * @param attachment the object to {@link IoFuture#attach attach} to the
     *                   returned {@code IoFuture} object; can be {@code null}
     * @param handler the handler for consuming the result; can be
     *                {@code null}
     * @return an {@code IoFuture} object representing the pending result
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws AcceptPendingException if an accept operation is already in
     *         progress on this channel
     * @throws NotYetBoundException if this channel's socket has not yet
     *         been bound
     */
    public abstract <A> IoFuture<AsynchronousSocketChannel, A> accept(
        A attachment,
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler);

    /**
     * Accepts a connection.
     * <p>
     * This method initiates accepting a connection made to this channel's
     * socket, returning an {@link IoFuture} representing the pending result
     * of the operation. Its {@link IoFuture#get() get} method will return
     * the {@link AsynchronousSocketChannel} for the new connection on
     * successful completion.
     * <p>
     * This method works as if invoking it were equivalent to evaluating the
     * expression:
     * <pre>
     *     accept((Object)null, handler);
     * </pre>
     * 
     * When a new connection is accepted then the resulting
     * {@code AsynchronousSocketChannel} will be bound to the same
     * {@link AsynchronousChannelGroup} as this channel.
     * 
     * @param <A> the attachment type
     * @param handler the handler for consuming the result; can be {@code null}
     * 
     * @return an {@code IoFuture} object representing the pending result
     * 
     * @throws ClosedAsynchronousChannelException if this channel is closed
     * @throws AcceptPendingException if an accept operation is already in
     *         progress on this channel
     * @throws NotYetBoundException if this channel's socket has not yet
     *         been bound
     */
    public final <A> IoFuture<AsynchronousSocketChannel, A> accept(
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        return accept(null, handler);
    }

    /**
     * Tells whether or not an accept is pending for this channel.
     * <p>
     * The result of this method is a <em>snapshot</em> of the channel
     * state. It may be invalid when the caller goes to examine the result
     * and should not be used for the purpose of coordination.
     * 
     * @return {@code true} if, and only if, an accept is pending for this
     *         this channel but has not yet completed.
     */
    public abstract boolean isAcceptPending();
}
