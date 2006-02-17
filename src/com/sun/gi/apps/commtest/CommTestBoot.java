package com.sun.gi.apps.commtest;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLOReference;
import java.util.List;
import java.util.ArrayList;

import javax.security.auth.Subject;

public class CommTestBoot
	implements SimBoot, SimUserListener,
		   SimUserDataListener, SimChannelListener {

    private static final long serialVersionUID = 1L;

    UserID myUserID = null;

    List<UserID> users = new ArrayList<UserID>();
    ChannelID echoID;
    ChannelID noJoinID;

    GLOReference thisobj;

    public void boot(SimTask task, boolean firstBoot) {
	System.err.println("Booting comm test, appid = " + task.getAppID());
	thisobj = task.findGLO("BOOT");
	task.addUserListener(thisobj);
	ChannelID cid = task.openChannel("echo");
	task.addChannelListener(cid,thisobj);

	noJoinID = task.openChannel("noJoin");
	task.lock(noJoinID, true);
	task.addChannelListener(noJoinID, thisobj);

	echoID = cid;
    }

    public void userLeft(SimTask task, UserID uid) {
	System.err.println("User left server: " + uid);
	users.remove(uid);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserListener#userJoined
     */
    public void userJoined(SimTask task, UserID uid, Subject subject) {
	System.err.print("User Joined server: " + uid + " ( ");
	for (Object cred : subject.getPublicCredentials()) {
	    System.err.print(cred + " ");
	}
	System.err.println(")");
	users.add(uid);
	task.addUserDataListener(uid, thisobj);

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.logic.SimUserDataListener#userDataReceived
     */
    public void userDataReceived(SimTask task, UserID from, ByteBuffer data) {
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
    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	System.err.println("User " + cid + " joined channel " + uid);

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
    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	System.err.println("User " + cid + " left channel " + uid);

    }

    /* (non-Javadoc)
     * @see com.sun.gi.logic.SimChannelListener#dataArrived
     */
    public void dataArrived(SimTask task, ChannelID cid,
	    UserID from, ByteBuffer buff) {

	System.err.println("Echoing: " +
	    new String(buff.array(),buff.arrayOffset(), buff.limit()));

	task.sendData(cid,new UserID[] {from},buff,true);
    }
}
