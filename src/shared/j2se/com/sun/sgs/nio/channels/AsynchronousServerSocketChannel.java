package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnsupportedAddressTypeException;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public abstract class AsynchronousServerSocketChannel
    extends AsynchronousChannel implements NetworkChannel
{
    /**
     * Initializes a new instance of this class.
     * 
     * @param provider the asynchronous channel provider for this channel
     */
    protected AsynchronousServerSocketChannel(AsynchronousChannelProvider provider)
    {
        super(provider);
    }

    /**
     * Opens an asynchronous server-socket channel.
     */
    public static AsynchronousServerSocketChannel open() throws IOException {
        return open((AsynchronousChannelGroup) null);
    }

    /**
     * Opens an asynchronous server-socket channel.
     */
    public static AsynchronousServerSocketChannel open(
        AsynchronousChannelGroup group) throws IOException
    {
        return AsynchronousChannelProvider.provider()
            .openAsynchronousServerSocketChannel(group);
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
     * Sets the value of a socket option.
     */
    public abstract AsynchronousServerSocketChannel setOption(
        SocketOption name, Object value) throws IOException;

    /**
     * Accepts a connection.
     */
    public abstract <A> IoFuture<AsynchronousSocketChannel, A> accept(
        A attachment,
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler);

    /**
     * Accepts a connection.
     */
    public final <A> IoFuture<AsynchronousSocketChannel, A> accept(
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        return accept(null, handler);
    }

    /**
     * Tells whether or not an accept is pending for this channel.
     */
    public abstract boolean isAcceptPending();

}
