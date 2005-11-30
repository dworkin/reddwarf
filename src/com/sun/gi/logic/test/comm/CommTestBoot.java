package com.sun.gi.logic.test.comm;

import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import java.util.List;
import java.util.ArrayList;

import javax.security.auth.Subject;

public class CommTestBoot implements SimBoot, SimUserListener{
  private static final long serialVersionUID = -560245896319031239L;  // turn off version checking
  UserID myUserID = null;
  List<UserID> users = new ArrayList<UserID>();
  /**
   * boot
   *
   * @param task SimTask
   */
  public void boot(SimTask task, boolean firstBoot) {
    System.out.println("Booting comm test, appid = "+task.getAppID());    
    GLOReference thisobj = task.findSO("BOOT");
    task.addUserListener(thisobj);
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

  

/* (non-Javadoc)
 * @see com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.logic.SimTask, com.sun.gi.comm.routing.UserID, javax.security.auth.Subject)
 */
  public void userJoined(SimTask task, UserID uid, Subject subject) {
	System.out.print("User Joined server: "+uid+" ( ");
	for(Object cred : subject.getPublicCredentials()){
		System.out.print(cred+" ");
		
	}
	System.out.println(")");
    users.remove(uid);	
  }
}
