package com.sun.gi.logic.test.comm;

import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.SOReference;
import java.util.List;
import java.util.ArrayList;

public class CommTestBoot implements SimBoot, SimUserListener{
  private static final long serialVersionUID = -560245896319031239L;  // turn off version checking
  UserID myUserID = null;
  List users = new ArrayList();
  /**
   * boot
   *
   * @param task SimTask
   */
  public void boot(SimTask task, boolean firstBoot) {
    System.out.println("Booting comm test, appid = "+task.getAppID());
    if (myUserID == null){ // only assign when booted for first time
      myUserID = task.createUser();
    }
    SOReference thisobj = task.findSO("BOOT");
    task.addUserListener(thisobj);
  }

  /**
   * userJoined
   *
   * @param uid UserID
   */
  public void userJoined(SimTask task, UserID uid,byte[] data) {
    System.out.println("User joined server: "+uid);
    users.add(uid);
  }

  /**
   * userLeft
   *
   * @param uid UserID
   */
  public void userLeft(SimTask task, UserID uid) {
     System.out.println("User left server: "+uid);
     users.remove(uid);
  }




  /**
   * userDataReceived
   *
   * @param task SimTask
   * @param uid UserID
   * @param data byte[]
   */
  public void userDataReceived(SimTask task, UserID uid, byte[] data) {
    String txt = new String(data);
    if (!uid.equals(myUserID)) {
      System.out.println("Data Arrived from ("+uid+") " + txt);
    }
    UserID[] ua = new UserID[users.size()];
    users.toArray(ua);
    task.sendData(ua,myUserID,("Echo: "+txt).getBytes());
  }
}
