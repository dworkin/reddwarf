package com.sun.gi.comm.routing.old;

import com.sun.gi.comm.routing.old.gtg.JRMSChannelID;
import com.sun.gi.comm.routing.old.gtg.JRMSUserID;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface ChannelListener {

  public void channelAdded(String channname, ChannelID id);
  public void channelRemoved(ChannelID id);
  public void channelDataChanged(ChannelID id, byte[] channelData);
  public void channelPacketArrived(ChannelID channel, UserID from,
                                   byte[] data);
  public void userJoinedChannel(ChannelID cid, JRMSUserID uid);
  public void userLeftChannel(ChannelID cid, JRMSUserID uid);

}
