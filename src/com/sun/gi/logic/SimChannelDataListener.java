package com.sun.gi.logic;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

/**
 *
 * <p>Title: SimChannelDataListener.java</p>
 * <p>Description: This interface is used by GLOs that want to evesdrop on data
 * flow.  It should be used judiciously as the data flow between clients can
 * easily over-whelm the server.</p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimChannelDataListener extends GLO {
    public void dataArrived(SimTask task, ChannelID id,
	UserID from, ByteBuffer buff);
}
