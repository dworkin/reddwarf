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
        if (filterClass == null) {
            filterClass = PassthroughFilter.class;
        }
        acceptor.bind(address,
                new AcceptedHandleAdapter(listener, filterClass));
        logger.log(Level.FINE, "listening on {0}", address);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses a {@code PassthroughFilter} which simply
     * forwards any data on untouched.
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener) 
                                                        throws IOException {

        listen(address, listener, PassthroughFilter.class);
    }
    
    /**
     * {@inheritDoc}
     */
    public void unbind(SocketAddress address) {
        acceptor.unbind(address);
    }
    
    /**
     * {@inheritDoc}
     */
    // MINA returns an raw Set type, but it is guaranteed to be
    // a Set<SocketAddress> so this cast is safe. -JM
    @SuppressWarnings("cast")
    public Set<SocketAddress> listAddresses() {
        Set<? extends SocketAddress> boundAddresses =
            (Set<? extends SocketAddress>)
                acceptor.getManagedServiceAddresses();
        return Collections.unmodifiableSet(boundAddresses);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        acceptor.unbindAll();
    }
}
