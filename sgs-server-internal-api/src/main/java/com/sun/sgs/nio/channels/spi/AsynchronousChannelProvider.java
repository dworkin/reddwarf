/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels.spi;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.ChannelPoolMXBean;
import com.sun.sgs.nio.channels.Channels;
import com.sun.sgs.nio.channels.ManagedChannelFactory;
import com.sun.sgs.nio.channels.ProtocolFamily;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * Service-provider class for asynchronous channels.
 * <p>
 * An asynchronous channel provider is a concrete subclass of this class
 * that has a zero-argument constructor and implements the abstract methods
 * specified below. A given invocation of the Java virtual machine maintains
 * a single system-wide default provider instance, which is returned by the
 * {@link #provider} method. The first invocation of that method will locate
 * the default provider as specified below.
 * <p>
 * If the system-wide default provider implements
 * {@link ManagedChannelFactory} then the management interfaces for the
 * pools of channels created by the provider will be included in the list of
 * {@link ChannelPoolMXBean} objects returned by the
 * {@link Channels#getChannelPoolMXBeans} method
 * [[NOT IMPLEMENTED: and also registered with the platform {@link MBeanServer}
 * when the {@code MBeanServer} is obtained by invoking the
 * {@link ManagementFactory#getPlatformMBeanServer} method]].
 * To ensure that the {@link ObjectName} for uniquely identifying the
 * {@code ChannelPoolMXBean} objects is unique it is recommended that the
 * <i>pool name</i> is "asynchronous.<i>name</i>" where <i>name</i>
 * identifies the provider's channel pool.
 * <p>
 * All of the methods in this class are safe for use by multiple concurrent
 * threads.
 * <p>
 * NOT IMPLEMENTED: {@code openAsynchronousFileChannel}
 */
public abstract class AsynchronousChannelProvider {
    
    /** Name of the system property to use to get the provider class. */
    private static final String PROVIDER_PROPERTY = 
            "com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider";
    
    /** Name of the default provider class to use. */
    private static final String DEFAULT_PROVIDER = 
            "com.sun.sgs.impl.nio.ReactiveAsyncChannelProvider";

    /** Mutex held while accessing the provider instance. */
    private static final Object lock = new Object();
    
    /** The system-wide provider singleton instance. */
    private static AsynchronousChannelProvider provider = null;

    /**
     * Initializes a new instance of this class.
     * 
     * @throws SecurityException if a security manager has been installed
     *         and it denies
     *         {@link RuntimePermission}{@code ("asynchronousChannelProvider")}
     */
    protected AsynchronousChannelProvider() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(
                new RuntimePermission("asynchronousChannelProvider"));
        }
    }

    /**
     * Loads the system-wide provider from a property.
     * 
     * @return {@true} if the provider was loaded from the property
     */
    private static void loadProviderFromProperty() {
        String cn = System.getProperty(PROVIDER_PROPERTY);
        if (cn == null) {
            cn = DEFAULT_PROVIDER;
        }
        try {
            Class<?> c = Class.forName(cn, true,
                ClassLoader.getSystemClassLoader());
            provider = (AsynchronousChannelProvider) c.newInstance();
        } catch (ClassNotFoundException x) {
            throw new ExceptionInInitializerError(x);
        } catch (IllegalAccessException x) {
            throw new ExceptionInInitializerError(x);
        } catch (InstantiationException x) {
            throw new ExceptionInInitializerError(x);
        } catch (SecurityException x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    /**
     * Returns the system-wide default asynchronous channel provider for
     * this invocation of the Java virtual machine.
     * <p>
     * The first invocation of this method locates the default provider
     * object as follows:
     * <ol>
     * <li>
     * If the system property
     * {@code com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider} is
     * defined then it is taken to be the fully-qualified name of a concrete
     * provider class. The class is loaded and instantiated; if this process
     * fails then an unspecified error is thrown.
     * <li>
     * [[NOT IMPLEMENTED:
     * If a provider class has been installed in a jar file that is
     * visible to the system class loader, and that jar file contains a
     * provider-configuration file named
     * {@code java.nio.channels.spi.AsynchronousChannelProvider}
     * in the resource
     * directory {@code META-INF/services}, then the first class name specified
     * in that file is taken. The class is loaded and instantiated; if this
     * process fails then an unspecified error is thrown.]]
     * <li>
     * Finally, if no provider has been specified by any of the above
     * means then the system-default provider class is instantiated and the
     * result is returned.
     * </ol>
     * Subsequent invocations of this method return the provider that was
     * returned by the first invocation.
     * 
     * @return the system-wide default {@code AsynchronousChannel} provider
     */
    public static AsynchronousChannelProvider provider() {
        synchronized (lock) {
            if (provider != null) {
                return provider;
            }
            return AccessController.doPrivileged(
                new PrivilegedAction<AsynchronousChannelProvider>() {
                    public AsynchronousChannelProvider run() {
                        loadProviderFromProperty();
                        return provider;
                    }
                });
        }
    }

    /**
     * Constructs a new asynchronous channel group.
     *
     * @param executor the executor service
     * @return a new asynchronous channel group
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousChannelGroup
    openAsynchronousChannelGroup(ExecutorService executor)
        throws IOException;

    /**
     * Opens an asynchronous server-socket channel.
     *
     * @param group the group to which the channel is bound, or {@code null}
     *              to bind to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws ShutdownChannelGroupException if the group is shutdown
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousServerSocketChannel
    openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
        throws IOException;

    /**
     * Opens an asynchronous socket channel.
     *
     * @param group the group to which the channel is bound, or {@code null}
     *              to bind to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws ShutdownChannelGroupException if the group is shutdown
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousSocketChannel
    openAsynchronousSocketChannel(AsynchronousChannelGroup group)
        throws IOException;

    /**
     * Opens an asynchronous datagram channel.
     * 
     * @param pf the protocol family, or {@code null} for the default protocol
     *           family
     * @param group the group to which the channel is bound, or {@code null}
     *              to bind to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws ShutdownChannelGroupException if the group is shutdown
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousDatagramChannel
    openAsynchronousDatagramChannel(ProtocolFamily pf,
                                    AsynchronousChannelGroup group)
        throws IOException;

    /**
     * Set the uncaught exception handler for the default group.
     * 
     * @param eh the object to use as the default uncaught exception
     *        handler, or {@code null} for no default handler
     * @throws SecurityException [TBD]
     */
    public abstract void
    setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler eh);

    /**
     * Returns the uncaught exception handler for the default group.
     * 
     * @return the uncaught exception handler for the default group, or
     *         {@code null} if there is no default handler
     */
    public abstract Thread.UncaughtExceptionHandler
    getUncaughtExceptionHandler();
}
