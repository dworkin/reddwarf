package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

import org.apache.mina.common.IoConnector;
import org.apache.mina.filter.executor.ExecutorExecutor;
import org.apache.mina.transport.socket.nio.DatagramConnector;

import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.IOConnector;

/**
 * An implementation of Endpoint that wraps a {@code SocketAddress}.
 * 
 * @author      Sten Anderson
 */
public class SocketEndpoint implements Endpoint {

    /** The remote socket address to which to connect */
    private SocketAddress address;
    
    private TransportType transportType;
    private Executor executor;
    
    /** A Mina configuration parameter whose value may affect performance. */
    private int numProcessors;
    

    /**
     * Constructs an {@code IOConnector} with the given TransportType.  This is
     * the simplest way to construct an IOConnector.  The returned connector 
     * creates new threads as necessary for processing. 
     * 
     * @param address                   the remote socket address to connect to
     * @param transportType             the type of transport
     * 
     * @return an IOConnector with the appropriate transport type.
     */
    public SocketEndpoint(SocketAddress address, TransportType type) {
        this(address, type, null);
    }
    
    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType using 
     * the given {@link Executor} for thread management.
     * 
     * @param address                   the remote socket address to connect to
     * @param transportType             the type of transport
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage. 
     * 
     * @return an appropriate IOConnector
     */
    public SocketEndpoint(SocketAddress address, TransportType type, 
                                                        Executor executor) {
        this(address, type, executor, 1);
    }
    
    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType using 
     * the given {@link Executor} for thread management.  
     * The {@code numProcessors} parameter refers to the number of 
     * {@code SocketIOProcessors} to initially create.  
     * <p>
     * A {@code SocketIOProcessor} is a Mina internal implementation detail
     * that controls the internal processing of the IO.  It is exposed here
     * to allow clients the option to configure this value, which may have a
     * positive impact on performance.  
     *
     * @param address                   the remote socket address to connect to
     * 
     * @param numProcessors             the number of processors for the 
     *                                  underlying Mina connector to create
     * 
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage. 
     *                                  
     *                                  
     * @return an appropriate IOConnector                                                         
     */
    public SocketEndpoint(SocketAddress address, TransportType type, 
                            Executor executor, int numProcessors) {
        
        this.address = address;
        this.transportType = type;
        this.executor = (executor == null) ? new DaemonExecutor() : executor;
        this.numProcessors = numProcessors;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public IOConnector createConnector() {
        IoConnector minaConnector = null; 
        ExecutorExecutor adapter = new ExecutorExecutor(executor);
        if (transportType.equals(TransportType.RELIABLE)) {
            minaConnector = new org.apache.mina.transport.socket.nio.SocketConnector(
                    numProcessors, adapter);
        }
        else {
            minaConnector = new DatagramConnector(adapter);
        }
        return new SocketConnector(address, minaConnector);
    }

}
