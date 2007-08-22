package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;

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
     * Accepts a connection.
     */
    public final <A> IoFuture<AsynchronousSocketChannel, A> accept(
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        return accept(null, handler);
    }

    /**
     * Accepts a connection.
     */
    public abstract <A> IoFuture<AsynchronousSocketChannel, A> accept(
        A attachment,
        CompletionHandler<AsynchronousSocketChannel, ? super A> handler);

    /**
     * Binds the channel's socket to a local address and configures the
     * socket to listen for connections.
     */
    public final AsynchronousServerSocketChannel bind(SocketAddress local)
        throws IOException
    {
        return bind(local, 0);
    }

    /**
     * Binds the channel's socket to a local address and configures the
     * socket to listen for connections.
     */
    public abstract AsynchronousServerSocketChannel bind(SocketAddress local,
        int backlog) throws IOException;

    /**
     * Tells whether or not an accept is pending for this channel.
     */
    public abstract boolean isAcceptPending();

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
     * Sets the value of a socket option.
     */
    public abstract AsynchronousServerSocketChannel setOption(
        SocketOption name, Object value) throws IOException;

}
