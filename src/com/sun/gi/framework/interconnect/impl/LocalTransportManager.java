/**
 *
 * <p>Title: LocalTransportManager.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.interconnect.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportManager;

/**
 *
 * <p>Title: LocalTransportManager.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class LocalTransportManager implements TransportManager {
    Map<String,LocalTransportChannel> channels =
           new HashMap<String,LocalTransportChannel>();
    /* (non-Javadoc)
     * @see com.sun.gi.framework.interconnect.TransportManager#openChannel(java.lang.String)
     */
    public TransportChannel openChannel(String channelName) throws IOException {
        LocalTransportChannel chan;
        synchronized(channels){
            chan = channels.get(channelName);
            if (chan==null){
                chan = new LocalTransportChannel(this,channelName);
                channels.put(channelName,chan);
            }
        }
        return chan;
    }
    
    void closeChannel(LocalTransportChannel chan){
       synchronized(channels){ 
           channels.remove(chan.getName()); 
           chan.close();
       }
    }

}
