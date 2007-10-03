/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ProtocolFamily;
import com.sun.sgs.nio.channels.ThreadPoolFactory;
import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

abstract class AsyncProviderImpl extends AsynchronousChannelProvider {

    /** The SelectorProvider. */
    private final SelectorProvider selectorProvider;

    /** The default group, or {@code null} if one has not been created yet. */
    private AsyncGroupImpl defaultGroupInstance = null;

    /**
     * The default uncaught exception handler until the default group
     * is created, at which point this field becomes {@code null}.
     */
    private UncaughtExceptionHandler defaultUncaughtHandler = null;

    /**
     * Creates an asynchronous channel provider using the given
     * {@link SelectorProvider}. If the parameter is {code null}, the
     * system default {@code SelectorProvider} will be used.
     * 
     * @param selProvider the {@code SelectorProvider}, or {@code null} to
     *        use the system default {@code SelectorProvider}
     * 
     * @see SelectorProvider#provider()
     */
    protected AsyncProviderImpl(SelectorProvider selProvider) {
        selectorProvider =
            selProvider != null ? selProvider
                                : SelectorProvider.provider();
        assert selectorProvider != null;
    }

    /**
     * Returns the {@link SelectorProvider} for this async provider.
     * 
     * @return the {@code SelectorProvider} for this async provider
     */
    SelectorProvider selectorProvider() {
        return selectorProvider;
    }


    // Methods related to default thread pools and channel groups.

    private ThreadPoolFactory getThreadPoolFactory() {
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
                        } catch (Exception ignore) {
                            // Any exception will fall-thru and return
                            // the default factory.
                        }
                    }
                    return createDefaultThreadPoolFactory();
                }
            });
    }

    /**
     * Creates a default {@link ThreadPoolFactory} when none has been
     * specified by other mechanisms.
     * 
     * @return a {@code ThreadPoolFactory}
     */
    protected ThreadPoolFactory createDefaultThreadPoolFactory() {
        return DefaultThreadPoolFactory.create();
    }

    private AsyncGroupImpl defaultGroup() throws IOException {
        synchronized (this) {
            if (defaultGroupInstance == null) {
                ThreadPoolFactory tpf = getThreadPoolFactory();
                ExecutorService executor = tpf.newThreadPool();
                defaultGroupInstance = openAsynchronousChannelGroup(executor);
                defaultGroupInstance.uncaughtHandler = defaultUncaughtHandler;
                defaultUncaughtHandler = null;
                // TODO is a cleanup thread needed/useful? -JM
            }
            return defaultGroupInstance;
        }
    }

    private AsyncGroupImpl checkGroup(AsynchronousChannelGroup group)
        throws IOException
    {
        if (group == null) {
            return defaultGroup();
        }

        if (group.provider() != this) {
            throw new IllegalArgumentException(
                "AsynchronousChannelGroup not created by this provider");
        }

        return (AsyncGroupImpl) group;
    }

    // AsynchronousChannelProvider methods

    /** {@inheritDoc} */
    @Override
    abstract public 
    AsyncGroupImpl
    openAsynchronousChannelGroup(ExecutorService executor) throws IOException;
    
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

    /** {@inheritDoc} */
    @Override
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        synchronized (this) {
            if (defaultGroupInstance != null)
                return defaultGroupInstance.uncaughtHandler;
            return defaultUncaughtHandler;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        synchronized (this) {
            if (defaultGroupInstance != null)
                defaultGroupInstance.uncaughtHandler = eh;
            else
                defaultUncaughtHandler = eh;
        }
    }    
}
