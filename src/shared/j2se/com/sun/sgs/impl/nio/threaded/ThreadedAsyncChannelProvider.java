/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.threaded;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ThreadPoolFactory;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public class ThreadedAsyncChannelProvider
    extends AsynchronousChannelProvider
{
    private final SelectorProvider selectorProvider;
    private AsyncChannelGroupImpl defaultGroupInstance = null;

    public static ThreadedAsyncChannelProvider create() {
        return new ThreadedAsyncChannelProvider(SelectorProvider.provider());
    }

    protected ThreadedAsyncChannelProvider(SelectorProvider selProvider) {
        selectorProvider = selProvider;
    }

    private static ThreadPoolFactory getThreadPoolFactory() {
        return AccessController.doPrivileged(
            new PrivilegedAction<ThreadPoolFactory>() {
                public ThreadPoolFactory run() {
                    String cn = System.getProperty(
                        "com.sun.sgs.nio.channels.DefaultThreadPoolFactory");
                    if (cn != null) {
                        try {
                            Class<?> c = Class.forName(cn, true,
                                ClassLoader.getSystemClassLoader());
                            return (ThreadPoolFactory) c.newInstance();
                        } catch (Exception ignore) { }
                    }
                    return DefaultThreadPoolFactory.create();
                }
            });
    }

    private AsyncChannelGroupImpl defaultGroup() throws IOException {
        synchronized (this) {
            if (defaultGroupInstance == null) {
                ThreadPoolFactory tpf = getThreadPoolFactory();
                ExecutorService executor = tpf.newThreadPool();
                defaultGroupInstance = openAsynchronousChannelGroup(executor);
                // TODO is a cleanup thread needed/useful? -JM
            }
        }
        return defaultGroupInstance;
    }

    private AsyncChannelGroupImpl checkGroup(AsynchronousChannelGroup group)
        throws IOException
    {
        if (group == null) {
            return defaultGroup();
        }

        if (group.provider() != this) {
            throw new IllegalArgumentException(
                "AsynchronousChannelGroup not created by this provider");
        }

        return (AsyncChannelGroupImpl) group;
    }

    void awaitSelectableOp(SelectableChannel channel, long timeout, int ops)
        throws IOException
    {
        if (timeout == 0)
            return;

        Selector sel = getSelectorProvider().openSelector();
        channel.register(sel, ops);
        if (sel.select(timeout) == 0)
            throw new AbortedByTimeoutException();
    }

    SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncChannelGroupImpl
        openAsynchronousChannelGroup(ExecutorService executor)
            throws IOException
    {
        return new AsyncChannelGroupImpl(this, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncDatagramChannelImpl
        openAsynchronousDatagramChannel(AsynchronousChannelGroup group)
            throws IOException
    {
        return new AsyncDatagramChannelImpl(this, checkGroup(group));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl
        openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
            throws IOException
    {
        return new AsyncServerSocketChannelImpl(this, checkGroup(group));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl
        openAsynchronousSocketChannel(AsynchronousChannelGroup group)
            throws IOException
    {
        return new AsyncSocketChannelImpl(this, checkGroup(group));
    }
}
