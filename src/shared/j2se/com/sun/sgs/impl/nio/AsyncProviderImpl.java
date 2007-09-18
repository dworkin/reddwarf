/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.ThreadPoolFactory;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public class AsyncProviderImpl
    extends AsynchronousChannelProvider
{
    private final SelectorProvider selectorProvider;
    private AbstractAsyncChannelGroup defaultGroupInstance = null;

    public static AsyncProviderImpl create() {
        return new AsyncProviderImpl(SelectorProvider.provider());
    }

    protected AsyncProviderImpl(SelectorProvider selProvider) {
        if (selProvider == null)
            throw new NullPointerException("null SelectorProvider");
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

    private AbstractAsyncChannelGroup defaultGroup() throws IOException {
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

    private AbstractAsyncChannelGroup checkGroup(AsynchronousChannelGroup group)
        throws IOException
    {
        if (group == null) {
            return defaultGroup();
        }

        if (group.provider() != this) {
            throw new IllegalArgumentException(
                "AsynchronousChannelGroup not created by this provider");
        }

        return (AbstractAsyncChannelGroup) group;
    }

    SelectorProvider selectorProvider() {
        return selectorProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractAsyncChannelGroup
    openAsynchronousChannelGroup(ExecutorService executor)
        throws IOException
    {
        return new ReactiveChannelGroup(this, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsynchronousDatagramChannel
    openAsynchronousDatagramChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        // TODO
        // return new AsyncDatagramChannelImpl(checkGroup(group));
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl
    openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncServerSocketChannelImpl(checkGroup(group));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl
        openAsynchronousSocketChannel(AsynchronousChannelGroup group)
            throws IOException
    {
        return new AsyncSocketChannelImpl(checkGroup(group));
    }
}
