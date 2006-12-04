package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;

import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 *  This is an adapter between an Apache Mina {@link IoHandler} and the SGS
 *  IO framework {@link IOHandler}.  This is used on the client side to 
 *  pass messages from Mina onto the associated {@code IOHandler}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SocketHandler implements IoHandler {
    
    private IOHandler handler;
    
    /**
     * Constructs a new {@code SocketHandler}.  As callbacks get fired from
     * the Apache Mina framework, they are forwarded on to the given
     * {@code IOHandler}.
     * 
     * @param handler           the {@code IOHandler} that will receive 
     *                          callbacks.
     */
    public SocketHandler(IOHandler handler) {
        this.handler = handler;
    }

    /**
     * Called by the Mina framework when the given session is closed.  Forward
     * this onto the associated handler as "disconnected".
     */
    public void sessionClosed(IoSession session) throws Exception {
        handler.disconnected(getHandle(session));
    }

    /**
     * This call-back is fired when there is an exception somewhere in Mina's
     * framework.  The exception is forwarded onto the associated 
     * {@code IOHandler}. 
     * 
     * @param session           the session where the exception occurred
     * @param throwable         the exception
     */
    public void exceptionCaught(IoSession session, Throwable exception)
                                                            throws Exception {
        
        handler.exceptionThrown(exception, getHandle(session));
        
    }

    /**
     * Called by the Mina framework when some amount of data comes in on the 
     * given session.  The data is packed into a byte array and forwarded to
     * the associated handler.
     * 
     * @param session           the session on which the data has arrived
     * @param message           the data, which in practice is a mina ByteBuffer
     */
    public void messageReceived(IoSession session, Object message) throws Exception {
        ByteBuffer minaBuffer = (ByteBuffer) message;
        
        byte[] array = new byte[minaBuffer.remaining()];
        minaBuffer.get(array);
        handler.messageReceived(array, getHandle(session));
        
    }

    private IOHandle getHandle(IoSession session) {
        return (IOHandle) session.getAttachment();
    }
    
    
    // these Mina call-backs aren't used.
    public void sessionCreated(IoSession session) throws Exception {}

    public void sessionOpened(IoSession arg0) throws Exception {}
    
    public void sessionIdle(IoSession arg0, IdleStatus arg1) throws Exception {}
    
    public void messageSent(IoSession arg0, Object arg1) throws Exception {}

}
