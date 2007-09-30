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
import com.sun.sgs.nio.channels.ProtocolFamily;
import com.sun.sgs.nio.channels.ThreadPoolFactory;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * Base implementation of AsynchronousChannelProvider.  Handles the
 * creation of the default thread pool and default channel group.
 */
abstract class AsyncProviderImpl
    extends AsynchronousChannelProvider
{
    /** The SelectorProvider. */
    private final SelectorProvider selectorProvider;

    /** The default group, or {@code null}. */
    private AbstractAsyncChannelGroup defaultGroupInstance = null;

    /**
     * Default constructor that initializes this async provider using
     * the system-default {@link SelectorProvider}.
     *
     * @see SelectorProvider#provider()
     */
    protected AsyncProviderImpl() {
        this(SelectorProvider.provider());
    }

    /**
     * Constructor that initializes this async provider using
     * the given {@link SelectorProvider}.
     *
     * @param selProvider the {@code SelectorProvider}
     */
    protected AsyncProviderImpl(SelectorProvider selProvider) {
        if (selProvider == null)
            throw new NullPointerException("null SelectorProvider");
        selectorProvider = selProvider;
    }

    // Private methods related to thread pools and channel groups.

    private static ThreadPoolFactory getThreadPoolFactory() {
        return AccessController.doPrivileged(
            new PrivilegedAction<ThreadPoolFactory>() {
                public ThreadPoolFactory run() {
                    String cn = System.getProperty(
                        "com.sun.sgs.nio.channels.DefaultThreadPoolFactory");
                    if (cn != null) {
                        Class<?> c;
                        try {
                            c = Class.forName(cn, true,
                                ClassLoader.getSystemClassLoader());
                            return (ThreadPoolFactory) c.newInstance();
                        } catch (ClassNotFoundException ignore) {
                        } catch (InstantiationException ignore) {
                        } catch (IllegalAccessException ignore) {
                        }
                        // Any exception will fall-thru and return
                        // the default pool.
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
            return defaultGroupInstance;
        }
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

    // Package-private methods

    SelectorProvider selectorProvider() {
        return selectorProvider;
    }

    // AsynchronousChannelProvider methods

    /** {@inheritDoc} */
    @Override
    abstract public AbstractAsyncChannelGroup
    openAsynchronousChannelGroup(ExecutorService executor)
        throws IOException;

    /** {@inheritDoc} */
    @Override
    public AsyncDatagramChannelImpl
    openAsynchronousDatagramChannel(ProtocolFamily pf,
                                    AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncDatagramChannelImpl(pf, checkGroup(group));
    }

    /** {@inheritDoc} */
    @Override
    public AsyncServerSocketChannelImpl
    openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncServerSocketChannelImpl(checkGroup(group));
    }

    /** {@inheritDoc} */
    @Override
    public AsyncSocketChannelImpl
    openAsynchronousSocketChannel(AsynchronousChannelGroup group)
        throws IOException
    {
        return new AsyncSocketChannelImpl(checkGroup(group));
    }
}
