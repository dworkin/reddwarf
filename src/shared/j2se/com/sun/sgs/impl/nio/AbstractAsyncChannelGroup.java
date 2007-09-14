package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
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

    protected SelectorProvider selectorProvider() {
        return ((AsyncProviderImpl) provider()).selectorProvider();
    }

    abstract void
    registerChannel(AsyncChannelImpl channel) throws IOException;

    void
    submit(AsyncChannelImpl channel, int op) {
        submit(channel, op, 0, TimeUnit.MILLISECONDS);
    }

    abstract void
    submit(AsyncChannelImpl channel,
           int op,
           long timeout,
           TimeUnit unit);
}
