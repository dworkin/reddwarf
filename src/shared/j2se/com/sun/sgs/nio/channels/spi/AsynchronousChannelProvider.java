package com.sun.sgs.nio.channels.spi;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.AsynchronousDatagramChannel;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;

/**
 * Service-provider class for asynchronous channels.
 * <p>
 * An asynchronous channel provider is a concrete subclass of this class
 * that has a zero-argument constructor and implements the abstract methods
 * specified below. A given invocation of the Java virtual machine maintains
 * a single system-wide default provider instance, which is returned by the
 * provider method. The first invocation of that method will locate the
 * default provider as specified below.
 * <p>
 * If the system-wide default provider implements ManagedChannelFactory then
 * the management interfaces for the pools of channels created by the
 * provider will be included in the list of ChannelPoolMXBean objects
 * returned by the Channels.getChannelPoolMXBeans method and also registered
 * with the platform MBeanServer when the MBeanServer is obtained by
 * invoking the ManagementFactory.getPlatformMBeanServer method. To ensure
 * that the ObjectName for uniquely identifying the ChannelPoolMXBean
 * objects is unique it is recommended that the pool name is
 * "asynchronous.name" where name identifies the provider's channel pool.
 * <p>
 * All of the methods in this class are safe for use by multiple concurrent
 * threads.
 */
public abstract class AsynchronousChannelProvider {

    private static final Object lock = new Object();
    private static AsynchronousChannelProvider provider = null;

    /**
     * Initializes a new instance of this class.
     * 
     * @throws SecurityException if a security manager has been installed and
     *             it denies RuntimePermission("asynchronousChannelProvider")
     */
    protected AsynchronousChannelProvider() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(
                new RuntimePermission("asynchronousChannelProvider"));
    }

    private static boolean loadProviderFromProperty() {
        String cn = System.getProperty(
            "com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider");
        if (cn == null)
            return false;
        try {
            Class<?> c = Class.forName(cn, true,
                ClassLoader.getSystemClassLoader());
            provider = (AsynchronousChannelProvider)c.newInstance();
            return true;
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
     * com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider
     * is defined then it is taken to be the fully-qualified name of a
     * concrete provider class. The class is loaded and instantiated;
     * if this process fails then an unspecified error is thrown.
     * <li>
     * Finally, if no provider has been specified by any of the above
     * means then the system-default provider class is instantiated and
     * the result is returned.
     * </ol>
     * Subsequent invocations of this method return the provider that
     * was returned by the first invocation.
     *
     * @return the system-wide default AsynchronousChannel provider
     */
    public static AsynchronousChannelProvider provider() {
        synchronized (lock) {
            if (provider != null)
                return provider;
            return AccessController.doPrivileged(
                new PrivilegedAction<AsynchronousChannelProvider>() {
                        public AsynchronousChannelProvider run() {
                            if (loadProviderFromProperty())
                                return provider;
//                            if (loadProviderAsService())
//                                return provider;
                            provider = com.sun.sgs.impl.nio.DefaultAsynchronousChannelProvider.create();
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
     * @param group the group to which the channel is bound, or null to bind
     *        to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousServerSocketChannel
        openAsynchronousServerSocketChannel(AsynchronousChannelGroup group)
            throws IOException;

    /**
     * Opens an asynchronous socket channel.
     * 
     * @param group the group to which the channel is bound, or null to bind
     *        to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousSocketChannel
        openAsynchronousSocketChannel(AsynchronousChannelGroup group)
            throws IOException;

    /**
     * Opens an asynchronous datagram channel.
     * 
     * @param group the group to which the channel is bound, or null to bind
     *        to the default group
     * @return the new channel
     * @throws IllegalArgumentException if the provider that created the
     *         group differs from this provider
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousDatagramChannel
        openAsynchronousDatagramChannel(AsynchronousChannelGroup group)
            throws IOException;

}
