package com.sun.sgs.impl.io;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;

import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOAcceptor;

/**
 * This class contains static methods for obtaining {@code IOAcceptor}
 * instances.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class AcceptorFactory {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(AcceptorFactory.class.getName()));
    
    private AcceptorFactory() {}
    
    /**
     * Constructs an {@code IOAcceptor} with the given TransportType.  This is
     * the simplest way to construct an  IOAcceptor.  The returned acceptor
     * will launch new threads as needed for processing. 
     * 
     * @param transportType             the type of transport
     * 
     * @return an IOAcceptor with the appropriate transport type.
     */
    public static IOAcceptor createAcceptor(TransportType transportType) {
        IoAcceptor minaAcceptor = null;  
        if (transportType.equals(TransportType.RELIABLE)) {
            minaAcceptor = new org.apache.mina.transport.socket.nio.SocketAcceptor();
        }
        else {
            minaAcceptor = new DatagramAcceptor();
        }
        SocketAcceptor acceptor = new SocketAcceptor(minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }
    
    /**
     * Constructs an {@code IOAcceptor} with the given TransportType using 
     * the given {@link Executor} for thread management.
     * 
     * @param transportType             the type of transport
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage. 
     * 
     * @return an appropriate IOAcceptor
     */
    public static IOAcceptor createAcceptor(TransportType transportType, 
                                                           Executor executor) {
        return createAcceptor(transportType, executor, 1);
    }
    
    /**
     * Constructs an {@code IOAcceptor} with the given TransportType using 
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
     * @return an appropriate IOAcceptor
     */
    public static IOAcceptor createAcceptor(TransportType transportType, 
                                        Executor executor, int numProcessors) {
        
        IoAcceptor minaAcceptor = null;  
        ExecutorAdapter adapter = new ExecutorAdapter(executor); 
        if (transportType.equals(TransportType.RELIABLE)) {
            minaAcceptor = new org.apache.mina.transport.socket.nio.SocketAcceptor(
                                    numProcessors, adapter);
        }
        else {
            minaAcceptor = new DatagramAcceptor(adapter);
        }
        SocketAcceptor acceptor = new SocketAcceptor(minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }
}
