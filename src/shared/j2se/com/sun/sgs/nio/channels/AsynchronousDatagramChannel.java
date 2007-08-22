package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public abstract class AsynchronousDatagramChannel extends AsynchronousChannel
    implements AsynchronousByteChannel, NetworkChannel, MulticastChannel
{
    protected AsynchronousDatagramChannel(AsynchronousChannelProvider provider) {
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
     *
     * @param group the group to which the newly constructed channel should
     *        be bound, or null for the default group
     * @return a new asynchronous datagram channel
     * @throws ShutdownChannelGroupException if the specified group is
     *        shutdown
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousDatagramChannel open(AsynchronousChannelGroup group)
        throws IOException
    {
        return AsynchronousChannelProvider.provider().openAsynchronousDatagramChannel(group);
    }

    /**
     * Opens an asynchronous datagram channel.
     * <p>
     * This method returns an asynchronous datagram channel that is bound
     * to the default group.This method is equivalent to evaluating the
     * expression:
     * <pre>
     *       open((AsynchronousChannelGroup)null);
     * </pre>
     * @return a new asynchronous datagram channel
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousDatagramChannel open() throws IOException {
        return open((AsynchronousChannelGroup)null);
    }

    public abstract AsynchronousDatagramChannel bind(SocketAddress local)
        throws IOException;

    public abstract AsynchronousDatagramChannel setOption(SocketOption name,
        Object value)
    throws IOException;

    public abstract SocketAddress getConnectedAddress()
    throws IOException;

    public abstract boolean isReadPending();
    public abstract boolean isWritePending();

    public abstract <A> IoFuture<Void, A> connect(SocketAddress remote,
        A attachment,
        CompletionHandler<Void, ? super A> handler);

    public final <A> IoFuture<Void, A> connect(SocketAddress remote,
        CompletionHandler<Void, ? super A> handler)
    {
        return connect(remote, null, handler);
    }

    public abstract <A> IoFuture<Void, A> disconnect(A attachment,
        CompletionHandler<Void, ? super A> handler);

    public final <A> IoFuture<Void, A> disconnect(CompletionHandler<Void, ? super A> handler)
    {
        return disconnect(null, handler);
    }

    public abstract <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<SocketAddress, ? super A> handler);

    public final <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        A attachment,
        CompletionHandler<SocketAddress, ? super A> handler)
    {
        return receive(dst, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    public final <A> IoFuture<SocketAddress, A> receive(ByteBuffer dst,
        CompletionHandler<SocketAddress, ? super A> handler)
    {
        return receive(dst, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    public abstract <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    public final <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return send(src, target, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    public final <A> IoFuture<Integer, A> send(ByteBuffer src,
        SocketAddress target,
        CompletionHandler<Integer, ? super A> handler)
    {
        return send(src, target, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    public abstract <A> IoFuture<Integer, A> read(ByteBuffer dst,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    public final <A> IoFuture<Integer, A> read(ByteBuffer dst,
        CompletionHandler<Integer, ? super A> handler)
    {
        return read(dst, 0L, TimeUnit.NANOSECONDS, null, handler);
    }

    public abstract <A> IoFuture<Integer, A> write(ByteBuffer src,
        long timeout,
        TimeUnit unit,
        A attachment,
        CompletionHandler<Integer, ? super A> handler);

    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        A attachment,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, attachment, handler);
    }

    public final <A> IoFuture<Integer, A> write(ByteBuffer src,
        CompletionHandler<Integer, ? super A> handler)
    {
        return write(src, 0L, TimeUnit.NANOSECONDS, null, handler);
    }


}
