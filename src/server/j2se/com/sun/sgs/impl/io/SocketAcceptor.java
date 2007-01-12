package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOFilter;

/**
 * This is an implementation of an {@code IOAcceptor} that uses a Mina 
 * {@link IoAcceptor} to accept incoming connections.  
 * This implementation is thread-safe.
 * 
 * @author  Sten Anderson
 * @version 1.0
 */
public class SocketAcceptor implements IOAcceptor {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketAcceptor.class.getName()));
    
    /** The associated Mina acceptor that handles the binding */
    private final IoAcceptor acceptor;

    /** Whether this acceptor has been shutdown. */
    private volatile boolean shutdown = false;

    /**
     * Constructs a {@code SocketAcceptor} with the given {@code IoAcceptor}.
     * 
     * @param acceptor          the mina {@code IoAcceptor} to use for the
     *                          underlying IO processing.
     */
    SocketAcceptor(IoAcceptor acceptor) {
        this.acceptor = acceptor;
    }
    
    /**
     * {@inheritDoc}
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener, 
                    Class<? extends IOFilter> filterClass) throws IOException
    {
        checkShutdown();
        AcceptedHandleAdapter adapter =
            new AcceptedHandleAdapter(listener,
                    (filterClass == null)
                    ? PassthroughFilter.class
                    : filterClass);
        acceptor.bind(address, adapter);
        logger.log(Level.FINE, "listening on {0}", address);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses a {@code PassthroughFilter} which simply
     * forwards any data on untouched.
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener) 
        throws IOException
    {
        listen(address, listener, PassthroughFilter.class);
    }
    
    /**
     * {@inheritDoc}
     */
    public void unbind(SocketAddress address) {
        checkShutdown();
        acceptor.unbind(address);
    }
    
    /**
     * {@inheritDoc}
     */
    // MINA returns an raw Set type, but it is guaranteed to be
    // a Set<SocketAddress> so this cast is safe. -JM
    @SuppressWarnings("cast")
    public Set<SocketAddress> listAddresses() {
        checkShutdown();

        Set<? extends SocketAddress> boundAddresses =
            (Set<? extends SocketAddress>)
                acceptor.getManagedServiceAddresses();
        return Collections.unmodifiableSet(boundAddresses);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        // TODO currently allow multiple calls to shutdown; shouuld we
        // only allow one? -JM
        shutdown = true;
        acceptor.unbindAll();
    }
    
    /**
     * Check whether this acceptor has been shutdown, throwing
     * IllegalStateException if it has.
     *
     * @throws IllegalStateException if this acceptor has been shutdown.
     */
    private void checkShutdown() {
        if (shutdown) {
            throw new IllegalStateException("Acceptor has been shutdown");
        }
    }
}
