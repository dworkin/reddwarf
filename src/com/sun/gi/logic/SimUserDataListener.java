package com.sun.gi.logic;

import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

/**
 *
 * <p>Title: SimUserDataListener </p>
 * <p>Description: This interface must be implemented by any GLO that
 * will be registered to receieve game user data events.</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimUserDataListener extends GLO {

    /**
     * This method is the start method of a task that gets queued whenever
     * data arrives for a user ID that this GLO has been bound to as a reciever.
     *
     * @param task SimTask The task context.
     * @param from UserID the UserID the data came from
     * @param data byte[] the data sent.
     */
    public void userDataReceived(UserID from, ByteBuffer data);

    public void userJoinedChannel(ChannelID cid, UserID uid);

    public void userLeftChannel(ChannelID cid, UserID uid);
    
    public void dataArrivedFromChannel(ChannelID id,
    		UserID from, ByteBuffer buff);
}
