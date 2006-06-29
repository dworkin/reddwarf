/**
 *
 * <p>Title: LocalTransportChannel.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.interconnect.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;

/**
 *
 * <p>Title: LocalTransportChannel.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class LocalTransportChannel implements TransportChannel {
    List<TransportChannelListener> listeners = 
        new ArrayList<TransportChannelListener>();
    LocalTransportManager mgr;
    boolean closed=false;
    String name;
    
    public LocalTransportChannel(LocalTransportManager mgr,
            String name){
        this.mgr = mgr;
        this.name = name;
    }
    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#addListener(com.sun.gi.framework.interconnect.TransportChannelListener)
     */
    public void addListener(TransportChannelListener l) {
        listeners.add(l);
    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#close()
     */
    public void close() {
        listeners.clear();
        closed = true;
    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#closeChannel()
     */
    public void closeChannel() {
        mgr.closeChannel(this);
    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#getName()
     */
    public String getName() {
        return name;
    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#removeListener(com.sun.gi.framework.interconnect.TransportChannelListener)
     */
    public void removeListener(TransportChannelListener l) {
        synchronized(listeners){
            listeners.remove(l);
        }

    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#sendData(java.nio.ByteBuffer)
     */
    public void sendData(ByteBuffer data) throws IOException {
        data.flip();
        synchronized(listeners){
            for(TransportChannelListener listener : listeners){
                listener.dataArrived(data.duplicate());
            }
        }

    }

    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportChannel#sendData(java.nio.ByteBuffer[])
     */
    public void sendData(ByteBuffer[] byteBuffers) throws IOException {        
        int sz = 0;
        for(ByteBuffer buff : byteBuffers){
            buff.flip();
            sz += buff.remaining();
        }
        ByteBuffer outbuff = ByteBuffer.allocate(sz);
        for(ByteBuffer buff : byteBuffers){
            outbuff.put(buff);
        }
        outbuff.flip();
        synchronized(listeners){
            for(TransportChannelListener listener : listeners){
                listener.dataArrived(outbuff.duplicate());
            }
        }
    }

}
