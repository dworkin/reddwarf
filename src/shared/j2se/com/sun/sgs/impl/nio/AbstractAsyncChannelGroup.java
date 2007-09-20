package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

abstract class AbstractAsyncChannelGroup
    extends AsynchronousChannelGroup
    implements Executor
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

    public void execute(Runnable command) {
        executor.execute(command);
    }

    protected SelectorProvider selectorProvider() {
        return ((AsyncProviderImpl) provider()).selectorProvider();
    }

    protected void checkShutdown() {
        if (isShutdown())
            throw new ShutdownChannelGroupException();
    }

    abstract void
    registerChannel(AsyncChannelImpl ach) throws IOException;

    abstract void
    unregisterChannel(AsyncChannelImpl ach);

    abstract void
    awaitReady(AsyncChannelImpl ach,
               int op);

    abstract void
    awaitReady(AsyncChannelImpl ach,
               int op,
               long timeout,
               TimeUnit unit);

    void setException(final AsyncOp<?> task, final Throwable t) {
        execute(new Runnable() {
            public void run() {
                task.setException(t);
            }});
    }

    /**
     * Invokes {@code shutdown} when this channel group is no longer
     * referenced.
     */
    @Override
    protected void finalize() {
        // TODO is this actually useful? -JM
        shutdown();
    }
}
