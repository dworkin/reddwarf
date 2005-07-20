package com.sun.gi.comm.routing.old.gtg;

import com.sun.gi.utils.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface JRMSCtrlListener {
  /**
   * peerAdded
   *
   * @param mid UUID
   */
  public void peerAdded(SGSUUID mid);

  /**
   * peerRemoved
   *
   * @param uUID UUID
   */
  public void peerRemoved(SGSUUID uUID);

  public void channelAdded(String appname, String channame, JRMSChannelID cid);

  public void channelRemoved(JRMSChannelID cid);

  public void userRemovedFromChannel(JRMSUserID uid, JRMSChannelID cid);

  public void userAddedToChannel(JRMSUserID uid, JRMSChannelID cid);

  public void userRemoved(JRMSUserID uid);

  public void userAdded(JRMSUserID uid);

  public void serverPacketArrived(JRMSUserID user, byte[] buff);

  public void userPacketArrived(JRMSUserID from, JRMSUserID to, byte[] buff);

  public void channelPacketArrived(JRMSChannelID cid, JRMSUserID from,
                                   byte[] buff);
}
