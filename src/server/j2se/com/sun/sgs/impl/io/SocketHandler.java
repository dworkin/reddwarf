package com.sun.sgs.impl.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOHandler;

/**
 *  This is an adapter between an Apache Mina {@link IoHandler} and the SGS
 *  IO framework {@link IOHandler}.  SocketHandlers exist one per handle on
 *  the client {@code IOConnector} side, and exit one per {@code IOAcceptor}
 *  on the server side.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SocketHandler extends IoHandlerAdapter {
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketHandler.class.getName()));

    public void sessionOpened(IoSession session) throws Exception {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        logger.log(Level.FINE, "opened session {0}", session);
        handle.getIOHandler().connected(handle);
    }
    
    /**
     * Called by the Mina framework when the given session is closed.  Forward
     * this onto the associated handler as "disconnected".
     */
    public void sessionClosed(IoSession session) throws Exception {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        logger.log(Level.FINE, "disconnect on {0}", handle);
        handle.getIOHandler().disconnected(handle);
    }

    /**
     * This call-back is fired when there is an exception somewhere in Mina's
     * framework.  The exception is forwarded onto the associated 
     * {@code IOHandler}. 
     * 
     * @param session           the session where the exception occurred
     * @param exception         the exception
     */
    public void exceptionCaught(IoSession session, Throwable exception)
                                                            throws Exception {
        
        SocketHandle handle = (SocketHandle) session.getAttachment();
        //logger.logThrow(Level.FINEST, exception, "exception on {0}", handle);
        handle.getIOHandler().exceptionThrown(exception, handle);
    }

    /**
     * Called by the Mina framework when some amount of data comes in on the 
     * given session.  The data is packed into a byte array and forwarded to
     * the associated filter.  The filter decides if and how to forward the
     * data on to the associated handler.
     * 
     * @param session           the session on which the data has arrived
     * @param message           the data, which in practice is a Mina ByteBuffer
     */
    public void messageReceived(IoSession session, Object message) throws Exception {
        SocketHandle handle = (SocketHandle) session.getAttachment();

        logger.log(Level.FINEST, "recv on {0}: {1}", handle, message);
        
        ByteBuffer minaBuffer = (ByteBuffer) message;
        
        byte[] array = new byte[minaBuffer.remaining()];
        minaBuffer.get(array);

        handle.getFilter().filterReceive(handle, array);
    }

}
