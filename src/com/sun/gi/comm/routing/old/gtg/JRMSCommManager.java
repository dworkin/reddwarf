package com.sun.gi.comm.routing.old.gtg;

import com.sun.gi.comm.routing.old.CommunicationsManager;
import com.sun.gi.comm.routing.old.ChannelID;
import com.sun.gi.comm.routing.old.ChannelListener;
import com.sun.gi.comm.routing.old.ServerListener;
import com.sun.gi.comm.routing.old.UserID;
import com.sun.gi.comm.routing.old.UserListener;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import java.net.InetAddress;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.Channel;
import java.rmi.*;
import com.sun.multicast.reliable.channel.*;
import com.sun.multicast.reliable.*;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.util.*;
import java.io.*;
import java.net.DatagramPacket;
import java.util.Map;
import java.util.HashMap;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * <p>Title: JRMSCommManager</p>
 * <p>Description: This is a simple comm manager that uses JRMS for
 *     everything, including channel implementation.  As such it may
 *     not be terribly scalable depending on how JRMS implements channels.
 *     It is however a good baseline starting
 *     point for further refinement. </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, Inc.</p>
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */

public class JRMSCommManager
    implements CommunicationsManager, JRMSCtrlListener {

  private static JRMSCommCtrlManager ctrlManager = new JRMSCommCtrlManager();
  private Map channelManagers = new HashMap();
  private List channelListeners = new ArrayList();
  private List serverListeners = new ArrayList();
  private List userListeners = new ArrayList();
  private List myusers = new ArrayList();
  private String appname = "dummyapp";

  private static final boolean DEBUG = true;
  private static final boolean DEBUGPEERS = true;
  private boolean DEBUGPKTS = true;

  private Map channelIDsByName = new HashMap();
  private Map channelNamesByID = new HashMap();
  public JRMSCommManager(String appname) {
    this.appname = appname;
    ctrlManager.addListener(this);
  }

  /**
   * getAppName
   *
   * @param appName String
   */
  public String getAppName() {
    return appname;
  }

  /**
   * createChannel
   *
   * @param channelName String
   * @param channelData byte[]
   * @return ChannelID
   */
  public ChannelID createChannel(UserID user, String channelName,
                                 byte[] channelData) {
    JRMSChannelID newid = (JRMSChannelID) channelIDsByName.get(channelName);
    if (newid != null) { // channel exists
      return null;
    }
    newid = new JRMSChannelID();
    channelIDsByName.put(channelName, newid);
    channelNamesByID.put(newid, channelName);
    // tell the world
    ctrlManager.createChannel(appname, channelName, newid);
    ctrlManager.joinChannel( (JRMSUserID) user, newid);
    return newid;
  }

  public ChannelID joinChannel(UserID user, String channelName) {
    JRMSChannelID cid = (JRMSChannelID) channelIDsByName.get(channelName);
    if (cid == null) { // channel does not exist
      return null;
    }
    ctrlManager.joinChannel( (JRMSUserID) user, cid);
    return cid;
  }

  /**
   * createUser
   *
   * @param userData byte[]
   * @return UserID
   */
  public UserID createUser(byte[] userData) {
    return ctrlManager.createUser(appname);
  }

  /**
   * leaveChannel
   *
   * @param id ChannelID
   */
  public void leaveChannel(UserID uid, ChannelID id) {
    ctrlManager.leaveChannel( (JRMSUserID) uid, (JRMSChannelID) id);
  }

  /**
   * deleteChannel
   *
   * @param id ChannelID
   */
  public void deleteChannel(ChannelID cid) {
    ctrlManager.removeChannel( (JRMSChannelID) cid);
  }

  /**
   * deleteUser
   *
   * @param id UserID
   */
  public void deleteUser(UserID id) {
    ctrlManager.removeUser( (JRMSUserID) id);
  }

  /**
   * getChannelData
   *
   * @param id ChannelID
   * @return byte[]
   */
  public byte[] getChannelData(ChannelID id) {
    return null;
  }

  /**
   * getUserData
   *
   * @param id UserID
   * @return byte[]
   */
  public byte[] getUserData(UserID id) {
    return null;
  }

  /**
   * setChannelData
   *
   * @param id ChannelID
   * @param data byte[]
   */
  public void setChannelData(ChannelID id, byte[] data) {
  }

  /**
   * setUserData
   *
   * @param id UserID
   * @param data byte[]
   */
  public void setUserData(UserID id, byte[] data) {
  }

  // callback registration

  /**
   * addChannelListener
   *
   * @param id ChannelID
   * @param listener ChannelListener
   */
  public void addChannelListener(ChannelListener listener) {
    channelListeners.add(listener);
  }

  /**
   * addServerListener
   *
   * @param listener ServerListener
   */
  public void addServerListener(ServerListener listener) {
    serverListeners.add(listener);
  }

  /**
   * addUserListener
   *
   * @param id UserID
   * @param listener UserListener
   */
  public void addUserListener(UserListener listener) {
    userListeners.add(listener);
  }

  // callbacks from CTRL manager

  public void peerAdded(SGSUUID mid) {
    // currrently do nothing, just means another Ctrl manager came up
    // all handled in CtrlManager
    if (DEBUGPEERS) {
      System.out.println("Peer Added: " + mid);
    }
  }

  public void peerRemoved(SGSUUID uUID) {
    // currrently do nothing, just means another Ctrl manager went away
    // all handled in CtrlManager
    if (DEBUGPEERS) {
      System.out.println("Peer Removed: " + uUID);
    }
  }

  public void channelAdded(String appnameIn, String channame, JRMSChannelID cid) {
    if (appname.equalsIgnoreCase(appnameIn)) { // its ours
      if (channelNamesByID.get(cid) == null) { // its new to us
        channelIDsByName.put(channame, cid);
        channelNamesByID.put(cid, channame);
      }
      for (Iterator i = channelListeners.iterator(); i.hasNext(); ) {
        ( (ChannelListener) i.next()).channelAdded(channame, cid);
      }
    }
  }

  public void channelRemoved(JRMSChannelID cid) {
    String name = (String) channelNamesByID.get(cid);
    if (name != null) { // its ours
      channelNamesByID.remove(cid);
      channelIDsByName.remove(name);
      for (Iterator i = channelListeners.iterator(); i.hasNext(); ) {
        ( (ChannelListener) i.next()).channelRemoved(cid);
      }
    }
  }

  public void userRemovedFromChannel(JRMSUserID uid, JRMSChannelID cid) {
    if (channelNamesByID.get(cid) != null) { // one of ours
      for (Iterator i = channelListeners.iterator(); i.hasNext(); ) {
        ( (ChannelListener) i.next()).userLeftChannel(cid, uid);
      }
    }
  }

  public void userAddedToChannel(JRMSUserID uid, JRMSChannelID cid) {
    if (channelNamesByID.get(cid) != null) { // one of ours
      for (Iterator i = channelListeners.iterator(); i.hasNext(); ) {
        ( (ChannelListener) i.next()).userJoinedChannel(cid, uid);
      }
    }
  }

  public void userRemoved(JRMSUserID uid) {
    if (uid.appname.equalsIgnoreCase(appname)) {
      for (Iterator i = userListeners.iterator(); i.hasNext(); ) {
        ( (UserListener) i.next()).userRemoved(uid, null);
      }
    }
  }

  public void userAdded(JRMSUserID uid) {
    if (uid.appname.equalsIgnoreCase(appname)) {
      for (Iterator i = userListeners.iterator(); i.hasNext(); ) {
        ( (UserListener) i.next()).userAdded(uid, null);
      }
    }
  }

  public void serverPacketArrived(JRMSUserID user, byte[] buff) {
    for (Iterator i = serverListeners.iterator(); i.hasNext(); ) {
      ( (ServerListener) i.next()).serverPacketArrived(user, buff);
    }

  }

  public void userPacketArrived(JRMSUserID from, JRMSUserID to, byte[] buff) {
    for (Iterator i = serverListeners.iterator(); i.hasNext(); ) {
      ( (UserListener) i.next()).userPacketArrived(from, to, buff);
    }
  }

  public void channelPacketArrived(JRMSChannelID cid, JRMSUserID from,
                                   byte[] buff) {
    for (Iterator i = serverListeners.iterator(); i.hasNext(); ) {
      ( (ChannelListener) i.next()).channelPacketArrived(cid, from, buff);
    }
  }


  //  data transmission functions

  public void sendDataToServer(UserID from, byte[] data) {
    ctrlManager.sendToServer( (JRMSUserID) from, data);
  }

  public void sendDataToUser(UserID from, UserID to, byte[] data) {
    ctrlManager.sendToUser( (JRMSUserID) from, (JRMSUserID) to, data);
  }

  public void sendDataToChannel(UserID from, ChannelID cid, byte[] data) {
    ctrlManager.sendToChannel( (JRMSChannelID) cid, (JRMSUserID) from, data);
  }

  public String getChannelName(ChannelID id) {
    return (String)channelNamesByID.get(id);
  }

  public ChannelID getChannelID(String name) {
    return (ChannelID)channelIDsByName.get(name);
  }

  /**
   * startHeartbeat
   */
  public void startHeartbeat() {
    ctrlManager.startHeartbeat();
  }
}
