package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.CompletionHandler;
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

    protected SelectorProvider selectorProvider() {
        return ((AsyncProviderImpl) provider()).selectorProvider();
    }

    protected void checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
    }

    AsyncDatagramChannelImpl
    openAsynchronousDatagramChannel()
        throws IOException
    {
        // TODO
        //return new AsyncDatagramChannelImpl(this);
        return null;
    }

    AsyncServerSocketChannelImpl
    openAsynchronousServerSocketChannel()
        throws IOException
    {
        // TODO
        //return new AsyncServerSocketChannelImpl(this);
        return null;
    }

    AsyncSocketChannelImpl
        openAsynchronousSocketChannel()
            throws IOException
    {
        return new AsyncSocketChannelImpl(this);
    }

    abstract void
    registerChannel(SelectableChannel channel) throws IOException;

    abstract void
    closeChannel(SelectableChannel channel) throws IOException;

    void
    submit(SelectableChannel channel, int op) {
        submit(channel, op, 0, TimeUnit.MILLISECONDS);
    }

    abstract void
    submit(SelectableChannel channel,
           int op,
           long timeout,
           TimeUnit unit);

    protected static <R, A> void
    runCompletion(CompletionHandler<R, A> handler,
                  A attachment,
                  Future<R> future)
    {
        if (handler == null)
            return;
        handler.completed(AttachedFuture.wrap(future, attachment));
    }
}
