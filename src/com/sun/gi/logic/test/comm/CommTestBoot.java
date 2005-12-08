package com.sun.gi.logic.test.comm;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import java.util.List;
import java.util.ArrayList;

import javax.security.auth.Subject;

public class CommTestBoot implements SimBoot, SimUserListener,
		SimUserDataListener {
	private static final long serialVersionUID = -560245896319031239L; // turn
																		// off
																		// version
																		// checking

	UserID myUserID = null;

	List<UserID> users = new ArrayList<UserID>();

	GLOReference thisobj;

	/**
	 * boot
	 * 
	 * @param task
	 *            SimTask
	 */
	public void boot(SimTask task, boolean firstBoot) {
		System.out.println("Booting comm test, appid = " + task.getAppID());
		thisobj = task.findSO("BOOT");
		task.addUserListener(thisobj);

	}

	/**
	 * userLeft
	 * 
	 * @param uid
	 *            UserID
	 */
	public void userLeft(SimTask task, UserID uid) {
		System.out.println("User left server: " + uid);
		users.remove(uid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.logic.SimTask,
	 *      com.sun.gi.comm.routing.UserID, javax.security.auth.Subject)
	 */
	public void userJoined(SimTask task, UserID uid, Subject subject) {
		System.out.print("User Joined server: " + uid + " ( ");
		for (Object cred : subject.getPublicCredentials()) {
			System.out.print(cred + " ");
		}
		System.out.println(")");
		users.add(uid);
		task.addUserDataListener(uid, thisobj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.logic.SimTask,
	 *      com.sun.gi.comm.routing.UserID, com.sun.gi.comm.routing.UserID,
	 *      com.sun.corba.se.impl.ior.ByteBuffer)
	 */
	public void userDataReceived(SimTask task, UserID from, ByteBuffer data) {
		System.out.println("Data from user " + from + ": "
				+ new String(data.array(),data.arrayOffset(),data.limit()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID,
	 *      com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		System.out.println("User " + cid + " joined channel " + uid);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel(com.sun.gi.comm.routing.ChannelID,
	 *      com.sun.gi.comm.routing.UserID)
	 */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		System.out.println("User " + cid + " left channel " + uid);

	}
}
