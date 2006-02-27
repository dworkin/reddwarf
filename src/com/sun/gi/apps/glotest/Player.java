package com.sun.gi.apps.glotest;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.security.auth.Subject;

public class Player implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.glotest");

    private UserID myUserID;

    public Player(UserID uid) {
	myUserID = uid;
    }

    // SimUserDataListener methods

    public void userJoinedChannel(ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " left channel " + cid);
    }

    public void userDataReceived(UserID uid, ByteBuffer data) {
	log.info("Player: User " + uid + " got data");
    }

    public void dataArrivedFromChannel(ChannelID cid, UserID uid,
	    ByteBuffer data) {
	log.info("Player: User " + uid + " evesdrop data on channel " + cid);
    }
}
