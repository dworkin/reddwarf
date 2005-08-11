package com.sun.gi.comm.routing.impl;

import java.util.List;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.SGSUser;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.TCPIPUser;
import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.utils.types.BYTEARRAY;

public class RouterImpl implements Router {

	private Map<UserID,SGSUser> userMap = new HashMap<UserID,SGSUser>();
	private TransportManager transportManager;	
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();
	private Map<UserID,BYTEARRAY> currentKeys = new HashMap<UserID,BYTEARRAY>();
	private Map<UserID,BYTEARRAY> previousKeys = new HashMap<UserID,BYTEARRAY>();	
	private Map<SGSUser,UserValidator> validators = new HashMap<SGSUser,UserValidator>();
	private UserValidatorFactory validatorFactory;
	private ByteBuffer hdr = ByteBuffer.allocate(256);
	
	
	

	public RouterImpl(TransportManager cmgr, UserValidatorFactory vFactory){
		transportManager = cmgr;
		validatorFactory = vFactory;
	}

	public void registerUser(SGSUser user) throws InstantiationException, IOException {
		//must validate user first
		UserValidator uv = validatorFactory.newValidator();
		validators.put(user,uv);
		doValidator(user, uv);
	}
	
	private void doValidator(SGSUser user, UserValidator validator) throws InstantiationException, IOException {		
		if (validator.authenticated()){
			validators.remove(user);
			registerUser(user,new UserID());
		} else {
			Callback[] cbs = validator.nextDataRequest();
			if (cbs != null) {
				user.sendValidationRequest(cbs);
			} else {
				validators.remove(user);
				user.sendUserRejected("Validation failed!");
			}
		}
			
	}
	
	private void registerUser(SGSUser user, UserID uid) throws InstantiationException, IOException {
		user.setUserID(uid);
		userMap.put(uid,user);
		fireUserJoined(uid);
		user.sendUserAccepted();
	}

	private void fireUserJoined(UserID uid) {
		// TODO
		
	}

	public void deregisterUser(SGSUser user) {
		UserID id = user.getUserID();
		userMap.remove(id);
		fireUserLeft(id);
	}

	private void fireUserLeft(UserID id) {
		// TODO Auto-generated method stub
		
	}

	public SGSChannel openChannel(String channelName) {
		ChannelImpl chan = new ChannelImpl(this,channelName);
		channelMap .put(chan.channelID(),chan);
		return chan;
	}


	public boolean reregisterUser(SGSUser user, byte[] userid, byte[] key) {
		UserID id;
		try {
			id = new UserID(userid);
		} catch (InstantiationException e) {
			e.printStackTrace();
			return false;
		}
		BYTEARRAY currentKey = currentKeys.get(id);
		BYTEARRAY previousKey = previousKeys.get(id);
		if ((currentKey.equals(key))|| (previousKey.equals(key))){
			try {
				registerUser(user,id);
			} catch (InstantiationException e) {				
				e.printStackTrace();
				return false;
			}
			return true;
		}
		return false;
	}

	public void validationResponse(TCPIPUser user, Callback[] cbs) throws InstantiationException, IOException {
		UserValidator uv = validators.get(user);
		uv.dataResponse(cbs);
		doValidator(user,uv);
		
	}

	public void sendBroadcastMessage(ChannelID chanID, UserID userID, ByteBuffer databuff, boolean reliable) {
		SGSChannel chan = channelMap.get(chanID);
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.BroadcastMessage.ordinal());
			hdr.put(reliable?0:1);
			
		}
		chan.broadcastData(userID,databuff.duplicate(),reliable);
		
	}

	public void sendMulticastMessage(ChannelID chanID, UserID userID, byte[][] tolist, ByteBuffer databuff, boolean reliable) {
		// TODO Auto-generated method stub
		
	}

	public void sendUnicastMessage(ChannelID chanID, UserID userID, byte[] to, ByteBuffer databuff) {
		// TODO Auto-generated method stub
		
	}

	

}
