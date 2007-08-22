/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ThreadPoolFactory;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public class DefaultAsynchronousChannelProvider
    extends AsynchronousChannelProvider
{
    private AsyncChannelGroupImpl defaultGroupInstance = null;

    public static DefaultAsynchronousChannelProvider create() {
        return new DefaultAsynchronousChannelProvider();
    }

    protected DefaultAsynchronousChannelProvider() {
        super();
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
        if (! this.equals(group.provider())) {
            throw new IllegalArgumentException(
                "AsynchronousChannelGroup not created by this provider");
        }
        return ((AsyncChannelGroupImpl) group).checkShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncChannelGroupImpl openAsynchronousChannelGroup(
                                                    ExecutorService executor)
        throws IOException
    {
        return new AsyncChannelGroupImpl(this, executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncDatagramChannelImpl openAsynchronousDatagramChannel(
                                                AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncDatagramChannelImpl(this, checkGroup(group));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl openAsynchronousServerSocketChannel(
                                                AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncServerSocketChannelImpl(this, checkGroup(group));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncSocketChannelImpl openAsynchronousSocketChannel(
                                                AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncSocketChannelImpl(this, checkGroup(group));
    }

}
