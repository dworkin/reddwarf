package com.sun.gi.apps.chattest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;

public class ChatTestBoot
	implements SimBoot, SimUserListener, SimUserDataListener
		    {

    private static final long serialVersionUID = 1L;

    UserID myUserID = null;

    List<UserID> users = new ArrayList<UserID>();
    ChannelID echoID;
    ChannelID noJoinID;

    GLOReference thisobj;

    public void boot(GLOReference thisGLO, boolean firstBoot) {
    SimTask task = SimTask.getCurrent();	
	System.err.println("Booting comm test, appid = " + task.getAppID());
	thisobj = task.findGLO("BOOT");
	task.addUserListener(thisobj);
	echoID = task.openChannel("echo");

	noJoinID = task.openChannel("noJoin");
	task.lock(noJoinID, true);

    }

    public void userLeft(UserID uid) {
	System.err.println("User left server: " + uid);
	users.remove(uid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserListener#userJoined
     */
    public void userJoined(UserID uid, Subject subject) {
	System.err.print("User Joined server: " + uid + " ( ");
	for (Object cred : subject.getPublicCredentials()) {
	    System.err.print(cred + " ");
	}
	System.err.println(")");
	users.add(uid);
	SimTask.getCurrent().addUserDataListener(uid, thisobj);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userDataReceived
     */
    public void userDataReceived(UserID from, ByteBuffer data) {
	System.err.println("Data from user " + from + ": "
		+ new String(data.array(),data.arrayOffset(),data.limit()));

	// Sten - new router feature test
	//task.leave(from, echoID);
	//task.join(from, noJoinID);
	//task.lock(noJoinID, false);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel
     */
    public void userJoinedChannel(ChannelID cid, UserID uid) {
	System.err.println("User " + cid + " joined channel " + uid);
	SimTask.getCurrent().setEvesdroppingEnabled(uid,echoID,true);
	SimTask.getCurrent().setEvesdroppingEnabled(uid,noJoinID,true);

	// test for forcabley closing a channel
	//task.closeChannel(cid);

	// test lock from leaving.
	/*if (cid.equals(noJoinID)) {
	  System.err.println("Locking noJoin");
	  task.lock(noJoinID, true);
	  }*/
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel
     */
    public void userLeftChannel(ChannelID cid, UserID uid) {
	System.err.println("User " + cid + " left channel " + uid);

    }

    /* (non-Javadoc)
     * @see com.sun.gi.logic.SimChannelDataListener#dataArrived
     */
    public void dataArrived(ChannelID cid,
	    UserID from, ByteBuffer buff) {

	
    }

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#dataArrivedFromChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void dataArrivedFromChannel(ChannelID cid, UserID from, ByteBuffer buff) {
		SimTask task = SimTask.getCurrent();
		task.sendData(cid,new UserID[] {from},buff,true);
		
	}

	
	
}
