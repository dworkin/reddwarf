package com.sun.sgs.impl.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandler;

/**
 * This class functions as an adapter between the Mina {@link IoHandler}
 * callbacks for new, incoming connections, and the higher level framework's 
 * {@link AcceptedHandleListener}.  As new connections come in, the 
 * {@code AcceptedHandleListener.newHandle} method is called.
 * <p>
 * All other callbacks are forwarded to the {@code IOHandler} associated with 
 * the {@code IOHandle} from the given {@code IoSession}, as handled by the 
 * superclass.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class AcceptedHandleAdapter extends SocketHandler {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
                AcceptedHandleAdapter.class.getName()));
    
    private final AcceptedHandleListener listener;
    private final Class<? extends IOFilter> filterClass;
    
    /**
     * Constructs a new {@code AcceptedHandleAdapter} with an 
     * {@code AcceptedHandleListener} that will be notified as new 
     * connections arrive.
     * 
     * @param listener          the listener to be notified of incoming 
     *                          connections.
     * @param filterClass       the type of filter to be attached to new handles
     */
    public AcceptedHandleAdapter(AcceptedHandleListener listener, 
            Class<? extends IOFilter> filterClass)
    {    
        this.listener = listener; 
        this.filterClass = filterClass;
    }
    
    // Override this Mina IoHandler callback 

    /**
     * As new {@code IoSession}s come in, set up a {@code SocketHandle} and 
     * notify the associated {@code AcceptedHandleListener}.  A new instance
     * of the associated filter will be attached to the new handle.
     */
    public void sessionCreated(IoSession session) throws Exception {
        logger.log(Level.FINE, "accepted session {0}", session);
        IOFilter filter = filterClass.newInstance();
        SocketHandle handle = new SocketHandle(filter, session);
        IOHandler handler = listener.newHandle(handle);
        handle.setIOHandler(handler);
    }

    
}
