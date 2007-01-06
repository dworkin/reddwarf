package com.sun.sgs.impl.io;

import java.util.concurrent.Executor;

import org.apache.mina.common.IoConnector;
import org.apache.mina.filter.executor.ExecutorExecutor;
import org.apache.mina.transport.socket.nio.DatagramConnector;

import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOConnector;

/**
 * This class contains static methods for obtaining {@code IOConnector}
 * instances.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ConnectorFactory {
    
    private ConnectorFactory() {}
    
    /**
     * Constructs an {@code IOConnector} with the given TransportType.  This is
     * the simplest way to construct an IOConnector.  The returned connector 
     * creates new threads as necessary for processing. 
     * 
     * @param transportType             the type of transport
     * 
     * @return an IOConnector with the appropriate transport type.
     */
    public static IOConnector createConnector(TransportType transportType) {
        return createConnector(transportType, new DaemonExecutor(), 1);       
    }
    
    /**
     * Constructs an {@code IOConnector} with the given TransportType using 
     * the given {@link Executor} for thread management.
     * 
     * @param transportType             the type of transport
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage. 
     * 
     * @return an appropriate IOConnector
     */
    public static IOConnector createConnector(TransportType transportType, 
                                                           Executor executor) {
        return createConnector(transportType, executor, 1);
    }
    
    /**
     * Constructs an {@code IOConnector} with the given TransportType using 
     * the given {@link Executor} for thread management.  
     * The {@code numProcessors} parameter refers to the number of 
     * {@code SocketIOProcessors} to initially create.  
     * <p>
     * A {@code SocketIOProcessor} is a Mina internal implementation detail
     * that controls the internal processing of the IO.  It is exposed here
     * to allow clients the option to configure this value.  
     * 
     * @param numProcessors             the number of processors for the 
     *                                  underlying Mina connector to create
     * 
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage. 
     *                                  
     * @return an appropriate IOConnector                                                         
     */
    public static IOConnector createConnector(TransportType transportType, 
                                        Executor executor, int numProcessors) {
        
        IoConnector minaConnector = null;  
        ExecutorExecutor adapter = new ExecutorExecutor(executor); 
        if (transportType.equals(TransportType.RELIABLE)) {
            minaConnector = new org.apache.mina.transport.socket.nio.SocketConnector(
                                    numProcessors, adapter);
        }
        else {
            minaConnector = new DatagramConnector(adapter);
        }
        return new SocketConnector(minaConnector);
        
    }
}
