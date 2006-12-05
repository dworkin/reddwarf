package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.mina.common.IoAcceptor;

import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;

/**
 * This is an Apache Mina implementation of an {@code IOAcceptor}.  This 
 * implementation uses a Mina {@link IoAcceptor} to accept incoming 
 * connections.  This implementation is thread-safe.
 * 
 * @author  Sten Anderson
 * @version 1.0
 */
public class SocketAcceptor implements IOAcceptor {
    
    private IoAcceptor acceptor;
    private List<SocketAddress> boundAddresses;

    /**
     * Constructs a {@code SocketAcceptor} with the given {@code IoAcceptor}.
     * 
     * @param acceptor          the mina {@code IoAcceptor} to use for the
     *                          underlying IO processing.
     */
    SocketAcceptor(IoAcceptor acceptor) {
        this.acceptor = acceptor;
        boundAddresses = new LinkedList<SocketAddress>();
    }
    
    /**
     * {@inheritDoc}
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener) 
                                                        throws IOException {
        acceptor.bind(address, new AcceptedHandleAdapter(listener));
        synchronized (boundAddresses) {
            boundAddresses.add(address);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void unbind(SocketAddress address) {
        synchronized(boundAddresses) {
            if (boundAddresses.contains(address)) {
                acceptor.unbind(address);
                boundAddresses.remove(address);
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public Collection<SocketAddress> listAddresses() {
        return Collections.unmodifiableCollection(boundAddresses);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        acceptor.unbindAll();
        synchronized (boundAddresses) {
            boundAddresses.clear();
        }
    }

}
