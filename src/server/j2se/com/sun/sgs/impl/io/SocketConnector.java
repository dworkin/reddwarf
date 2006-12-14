package com.sun.sgs.impl.io;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoFuture;
import org.apache.mina.util.NewThreadExecutor;

import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

/**
 * This is a socket-based implementation of an {@code IOConnector} using
 * the Apache Mina framework for the underlying transport.  It uses an
 * {@link org.apache.mina.common.IoConnector} to initiate connections on 
 * remote hosts.  
 * <p>
 * Its constructor is "package-private", so use one of the 
 * {@code ConnectorFactory.createConnector} methods and specify a 
 * {@code TransportType} to create a new instance. This implementation is 
 * thread-safe.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public class SocketConnector implements IOConnector {
    
    private IoConnector connector;

    
    /**
     * Constructs a {@code SocketConnector} using the given {@code IoConnector}
     * for the underlying transport.  This constructor is only visible to the
     * package, so use one of the {@code ConnectorFactory.createConnector} 
     * methods to create a new instance.
     * 
     */
    SocketConnector(IoConnector connector) {
        this.connector = connector;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * A {@code PassthroughFilter} will be installed on the returned 
     * {@code IOHandle}, which simply passes the data on untouched.
     */
    public IOHandle connect(InetAddress address, int port, IOHandler handler) {
        return connect(address, port, handler, new PassthroughFilter());
    }

    /**
     * {@inheritDoc}
     */
    public IOHandle connect(InetAddress address, int port, IOHandler handler, 
                            IOFilter filter) {
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        
        SocketHandle handle = new SocketHandle(filter);
        handle.setIOHandler(handler);
        IoFuture future = connector.connect(socketAddress, new SocketHandler());
        future.addListener(handle);
        
        // avoid a race condition b/w the time of getting a reference to the
        // IoFuture and signing the handle up as a listener.
        if (future.isReady()) {
            handle.operationComplete(future);
        }
        
        return handle;
    }
    

    public void shutdown() {
    }


}
