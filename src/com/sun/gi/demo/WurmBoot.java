package com.sun.gi.demo;

import java.io.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.logic.*;

public class WurmBoot
    implements SimBoot, SimUserListener {
  private static final long serialVersionUID = -560245896319031239L; // turn off version checking
  private boolean firstTime = true;
  public UserID myUserID;
  private List currentUsers = new ArrayList();
  private Map idToSORef = new HashMap();
  private Map uidToSORef = new HashMap();
  private long wurmIDs = 1;

  public WurmBoot() {

  }

  /**
   * userDataReceived
   *
   * @param task SimTask
   * @param uid UserID
   * @param data byte[]
   */
  public void userDataReceived(SimTask task, UserID uid, byte[] data) {

  }

  /**
   * userJoined
   *
   * @param task SimTask
   * @param uid UserID
   */
  public void userJoined(SimTask task, UserID uid, byte[] data) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bais);
      String username = (String) ois.readUTF();
      String password = (String) ois.readUTF();
      ois.close();
      bais.close();
      System.out.println("User " + username + " Password " + password +
                         " joined WURM.");
      byte[] buff = null;
      SOReference userRef = (SOReference)idToSORef.get(username);
      WurmPlayer player;
      if (userRef == null) { // new player
        userRef = task.createSO(new WurmPlayer(
            task.makeReference(task.getRefID(this)),username, uid,
            getWURMid()),"_player_"+username);
        idToSORef.put(username,userRef);
        uidToSORef.put(uid,userRef);
        buff = WurmProtocol.
          makeServerMessage(0,"User " + username + " has created a new player.");
        player = (WurmPlayer)userRef.peek(task);
      } else {
        player = (WurmPlayer)userRef.peek(task);
        UserID oldid = player.getUserID();
        uidToSORef.remove(oldid);
        uidToSORef.put(uid,userRef);
        player.setUserID(uid);
        currentUsers.remove(oldid);
        buff = WurmProtocol.
          makeServerMessage(0,"User " + username + " has logged back in.");
      }
      UserID[] users = new UserID[currentUsers.size()];
      currentUsers.toArray(users);
      task.addUserDataListener(uid,userRef);
      task.sendData(users, myUserID, buff);
      buff = WurmProtocol.makeLoginResult(true,
                                          "You have sucessfully logged into "+
                                          " the SMI/MOJANG RPG demo.",
                                          player.getX(),player.getY(),
                                          player.getZ(),player.getYRot(),
                                          System.currentTimeMillis());
      UserID[] joiner = {uid};
      task.sendData(joiner,myUserID,buff);
      buff = WurmProtocol.makeAddCreaturePacket(player.getWurmID(),
                                                "/models/human/human.ms3d",
                                                player.getName(),player.getX(),
                                                player.getY(),player.getZ(),
                                                player.getYRot());
      task.sendData(users,myUserID,buff);
      for(int i = 0;i<users.length;i++){
        SOReference otherPlayerRef = (SOReference)uidToSORef.get(users[i]);
        if (otherPlayerRef != null) { // player is no longer on
          WurmPlayer otherPlayer = (WurmPlayer) otherPlayerRef.peek(task);
          if (otherPlayer.getWurmID() != player.getWurmID()) { // not our ghost
            System.out.println("Sending " + otherPlayer.getName()+"("+
                              otherPlayer.getWurmID()+")" + " to " +
                               player.getName()+"("+player.getWurmID()+")");
            buff = WurmProtocol.makeAddCreaturePacket(otherPlayer.getWurmID(),
                "/models/human/human.ms3d",
                otherPlayer.getName(), player.getX(),
                otherPlayer.getY(), player.getZ(),
                otherPlayer.getYRot());
            task.sendData(joiner, myUserID, buff);
          }
        }
      }
      currentUsers.add(uid);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * getWURMid
   *
   * @return long
   */
  private long getWURMid() {
    return wurmIDs++;
  }

  /**
   *
   * userLeft
   *
   * @param task SimTask
   * @param uid UserID
   */
  public void userLeft(SimTask task, UserID uid) {
    System.out.println("User left WURM.");
    currentUsers.remove(uid);
    byte[] buff = WurmProtocol.
         makeServerMessage(0,"User " + uid + " has logged out.");
     UserID[] users = new UserID[currentUsers.size()];
     currentUsers.toArray(users);
     task.sendData(users, myUserID, buff);
     SOReference playerSO = (SOReference)uidToSORef.get(uid);
     if (playerSO != null) {
       WurmPlayer player = (WurmPlayer) playerSO.peek(task);
       buff = WurmProtocol.makeRemoveCreaturePacket(player.getWurmID());
       task.sendData(users,uid,buff);
     }
  }

  /**
   * boot
   *
   * @param task SimTask
   */
  public void boot(SimTask task) {
    if (firstTime) { // do WURM world start stuff
      System.out.println("WURM First Boot.");
      firstTime = false;
      myUserID = task.createUser();
    }
    SOReference thisobj = task.findSO("BOOT");
    task.addUserListener(thisobj);
    System.out.println("WURM Booted.");
  }

  /**
   * currentUsers
   *
   * @return UserID[]
   */
  public UserID[] currentUsers() {
    UserID[] ids = new UserID[currentUsers.size()];
    currentUsers.toArray(ids);
    return ids;
  }
}
