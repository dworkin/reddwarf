package com.sun.gi.logic;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

/**
 *
 * <p>Title: SimChannelListener.java</p>
 * <p>Description: This interface is used by GLOs that want to know about
 * users coming and going on one or more channels.</p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimChannelListener extends GLO {

    public void joinedChannel(SimTask task, ChannelID cid, UserID uid);

    public void leftChannel(SimTask task, ChannelID cid, UserID uid);
}
