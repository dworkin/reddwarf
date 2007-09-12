package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;

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

    SelectorProvider getSelectorProvider() {
        return ((AsyncProviderImpl) provider()).getSelectorProvider();
    }

    abstract void addChannel(AsyncChannelInternals channel);
    abstract void channelClosed(AsyncChannelInternals channel);

    <R> Future<R> submit(SelectableChannel channel,
                         int op,
                         Callable<R> command)
    {
        return submit(channel, op, command, 0, null);
    }

    abstract <R> Future<R>
    submit(SelectableChannel channel,
           int op,
           Callable<R> command,
           long timeout,
           TimeUnit unit);
}
