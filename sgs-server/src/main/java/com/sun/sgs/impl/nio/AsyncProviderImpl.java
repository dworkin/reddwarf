/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
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

/**
 * Common base implementation of {@link AsynchronousChannelProvider}.
 * <ul>
 * <li>
 * Implements methods to create the default channel group and thread pool
 * factory as specified by {@link AsynchronousChannelGroup}.
 * <li>
 * Checks the channel group passed in to the various channel {@code open()}
 * methods, substituting the default group if appropriate.
 * <li>
 * Manages the default group's {@link UncaughtExceptionHandler}.
 * </ul>
 */
abstract class AsyncProviderImpl extends AsynchronousChannelProvider {

    /** The SelectorProvider. */
    private final SelectorProvider selectorProvider;

    /** The default group, or {@code null} if one has not been created yet. */
    private AsyncGroupImpl defaultGroupInstance = null;

    /**
     * The default uncaught exception handler (or {@code null} if no handler
     * is set), until the default group is created.  After the default group
     * has been created, this field is set to {@code null} to avoid pinning
     * the handler in memory.
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

    /**
     * Returns the default thread pool factory as specified by the
     * class documentation for {@link AsynchronousChannelGroup}.
     * Delegates to {@link #createDefaultThreadPoolFactory()} if the
     * other factory creation mechanisms fail.
     * 
     * @return the default {@code ThreadPoolFactory}
     */
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
                        } catch (Throwable ignore) {
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

    /**
     * Returns the default {@link AsynchronousChannelGroup} for this
     * provider, creating one if necessary.
     * 
     * @return the default channel group for this provider
     * 
     * @throws IOException if an I/O error occurs
     */
    private AsyncGroupImpl defaultGroup() throws IOException {
        synchronized (this) {
            if (defaultGroupInstance == null) {
                ThreadPoolFactory tpf = getThreadPoolFactory();
                ExecutorService executor = tpf.newThreadPool();
                defaultGroupInstance = openAsynchronousChannelGroup(executor);
                defaultGroupInstance.uncaughtHandler = defaultUncaughtHandler;
                defaultUncaughtHandler = null;
            }
            return defaultGroupInstance;
        }
    }

    /**
     * Checks that the given channel group was created by this provider,
     * throwing an exception if it was not.
     * 
     * @param group a channel group, or {@code null} to return the default
     *        group for this provider
     * @return the given group, if it was created by this provider, or
     *         the default group if {@code null} was given
     * 
     * @throws IllegalArgumentException if the group was not created
     *         by this provider
     * @throws IOException if an IO error occurs while constructing
     *         the default group
     */
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
    public abstract
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
            if (defaultGroupInstance != null) {
                return defaultGroupInstance.uncaughtHandler;
            } else {
                return defaultUncaughtHandler;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        synchronized (this) {
            if (defaultGroupInstance != null) {
                defaultGroupInstance.uncaughtHandler = eh;
            } else {
                defaultUncaughtHandler = eh;
            }
        }
    }    
}
