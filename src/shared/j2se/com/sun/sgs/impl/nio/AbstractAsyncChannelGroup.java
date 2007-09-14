package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

abstract class AbstractAsyncChannelGroup
    extends AsynchronousChannelGroup
{
    /**
     * The executor service for this group's tasks.
     */
    private final ExecutorService executor;

    protected AbstractAsyncChannelGroup(AsyncProviderImpl provider,
                                        ExecutorService executor)
    {
        super(provider);
        if (executor == null)
            throw new NullPointerException("null ExecutorService");
        this.executor = executor;
    }

    protected ExecutorService executor() {
        return executor;
    }

    protected void checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
    }

    protected SelectorProvider getSelectorProvider() {
        checkShutdown();
        return ((AsyncProviderImpl) provider()).getSelectorProvider();
    }

    SocketChannel acceptChannel(ServerSocketChannel channel)
        throws IOException
    {
        checkShutdown();
        return channel.accept();
    }

    DatagramChannel openDatagramChannel() throws IOException {
        checkShutdown();
        return getSelectorProvider().openDatagramChannel();
    }

    ServerSocketChannel openServerSocketChannel() throws IOException {
        checkShutdown();
        return getSelectorProvider().openServerSocketChannel();
    }

    SocketChannel openSocketChannel() throws IOException {
        checkShutdown();
        return getSelectorProvider().openSocketChannel();
    }

    void closeChannel(SelectableChannel channel) throws IOException {
        channel.close();
    }

    abstract boolean
    isOperationPending(SelectableChannel channel, int op);

    <R, A> IoFuture<R, A>
    submit(A attachment,
           CompletionHandler<R, ? super A> handler,
           Callable<R> callable)
    {
        return submit(null, 0, attachment, handler, callable);
    }

    <R, A> IoFuture<R, A>
    submit(SelectableChannel channel,
           int op,
           A attachment,
           CompletionHandler<R, ? super A> handler,
           Callable<R> callable)
    {
        return submit(channel, op, attachment, handler, 0, null, callable);
    }

    abstract <R, A> IoFuture<R, A>
    submit(SelectableChannel channel,
           int op,
           A attachment,
           CompletionHandler<R, ? super A> handler,
           long timeout,
           TimeUnit unit,
           Callable<R> callable);

}
