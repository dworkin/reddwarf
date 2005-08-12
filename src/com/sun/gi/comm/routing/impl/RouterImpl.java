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
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.logging.SGSERRORCODES;
import com.sun.gi.utils.types.BYTEARRAY;

public class RouterImpl implements Router {
	static private final UserID serverID = new ChannelID(-1,-1);

	private Map<UserID,SGSUser> userMap = new HashMap<UserID,SGSUser>();
	private TransportManager transportManager;	
	private TransportChannel routerControlChannel;
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();
	private Map<String,SGSChannel> channelNameMap = new HashMap<String,SGSChannel>();
	private Map<UserID,BYTEARRAY> currentKeys = new HashMap<UserID,BYTEARRAY>();
	private Map<UserID,BYTEARRAY> previousKeys = new HashMap<UserID,BYTEARRAY>();	
	private Map<SGSUser,UserValidator> validators = new HashMap<SGSUser,UserValidator>();
	private UserValidatorFactory validatorFactory;
	private ByteBuffer hdr = ByteBuffer.allocate(256);
	
	private enum OPCODE {UserJoined,UserLeft};
	

	public RouterImpl(TransportManager cmgr, UserValidatorFactory vFactory) throws IOException{
		transportManager = cmgr;
		validatorFactory = vFactory;
		routerControlChannel = transportManager.openChannel("__SGS_ROUTER_CONTROL");
		routerControlChannel.addListener(new TransportChannelListener() {

			public void dataArrived(ByteBuffer buff) {
				OPCODE opcode = OPCODE.values()[(int)buff.get()];
				switch (opcode){
					case UserJoined:
						int idlen = buff.getInt();
						byte[] idbytes = new byte[idlen];
						buff.get(idbytes);
						reportUserJoined(idbytes);
						break;
					case UserLeft:
						idlen = buff.getInt();
						idbytes = new byte[idlen];
						buff.get(idbytes);
						reportUserLeft(idbytes);
						break;
				}
				
			}

			

			public void channelClosed() {
				SGSERRORCODES.FatalErrors.RouterFailure.fail("Router control channel failed.");				
			}
			
		});
	}
	
	private void reportUserLeft(byte[] uidbytes) {
		for(SGSUser user : userMap.values()){
			try {
				user.sendUserLeft(uidbytes);
			} catch (IOException e) {
				System.out.println("Exception sending UserLeft to user id="+user.getUserID());
				e.printStackTrace();
			}
		}

		
	}

	private void reportUserJoined(byte[] uidbytes) {
		for(SGSUser user : userMap.values()){
			try {
				user.sendUserJoined(uidbytes);
			} catch (IOException e) {
				System.out.println("Exception sending UserJOined to user id="+user.getUserID());
				e.printStackTrace();
			}
		}
		
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
		byte[] uidbytes = uid.toByteArray();
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UserJoined.ordinal());
		
			hdr.putInt(uidbytes.length);
			hdr.put(uidbytes);
			try {
				routerControlChannel.sendData(hdr);
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}
		
	}

	public void deregisterUser(SGSUser user) {
		UserID id = user.getUserID();
		userMap.remove(id);
		fireUserLeft(id);
	}

	private void fireUserLeft(UserID uid) {
		byte[] uidbytes = uid.toByteArray();
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UserLeft.ordinal());		
			hdr.putInt(uidbytes.length);
			hdr.put(uidbytes);
			try {
				routerControlChannel.sendData(hdr);
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}
		
	}

	public SGSChannel openChannel(String channelName) {
		SGSChannel sgschan = channelNameMap.get(channelName);
		if (sgschan == null){
			TransportChannel tchan;
			try {
				tchan = transportManager.openChannel(channelName);
			} catch (IOException e) {				
				e.printStackTrace();
				return null;
			}
			SGSChannel chan;
			try {
				chan = new ChannelImpl(tchan);
				channelMap.put(chan.channelID(),chan);
				channelNameMap.put(channelName,chan);
			} catch (IOException e) {
				e.printStackTrace();
			}		
		}
		return sgschan;
	}
	
	protected void closeChannel(ChannelImpl channel){
		channelNameMap.remove(channel.getName());
		channelMap.remove(channel.channelID());
	}


	public boolean reregisterUser(SGSUser user, byte[] userid, byte[] key)
			throws InstantiationException, IOException {
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
				registerUser(user,id);
		
			return true;
		}
		return false;
	}

	public void validationResponse(TCPIPUser user, Callback[] cbs) throws InstantiationException, IOException {
		UserValidator uv = validators.get(user);
		uv.dataResponse(cbs);
		doValidator(user,uv);
		
	}

	
	

}
