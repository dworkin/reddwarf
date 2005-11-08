package com.sun.gi.comm.routing.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.logging.SGSERRORCODES;
import com.sun.gi.utils.types.BYTEARRAY;

public class RouterImpl implements Router {

	private Map<UserID,SGSUser> userMap = new HashMap<UserID,SGSUser>();
	private TransportManager transportManager;	
	private TransportChannel routerControlChannel;
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();
	private Map<String,SGSChannel> channelNameMap = new HashMap<String,SGSChannel>();
	private Map<UserID,BYTEARRAY> currentKeys = new HashMap<UserID,BYTEARRAY>();
	private Map<UserID,BYTEARRAY> previousKeys = new HashMap<UserID,BYTEARRAY>();	
	private ByteBuffer hdr = ByteBuffer.allocate(256);
	private RouterListener listener;
	
	private enum OPCODE {UserJoined,UserLeft,UserJoinedChannel,UserLeftChannel};
	

	public RouterImpl(TransportManager cmgr) throws IOException{
		transportManager = cmgr;
		
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
					case UserJoinedChannel:
						break;
					case UserLeftChannel:
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
				user.userLeftSystem(uidbytes);
			} catch (IOException e) {
				System.out.println("Exception sending UserLeft to user id="+user.getUserID());
				e.printStackTrace();
			}
		}

		
	}

	private void reportUserJoined(byte[] uidbytes) {
		UserID sentID=null;
		try {
			sentID = new UserID(uidbytes);
		} catch (InstantiationException e1) {			
			e1.printStackTrace();
			return;
		}
		for(SGSUser user : userMap.values()){
			try {		
				if (!user.getUserID().equals(sentID)){
					user.userJoinedSystem(uidbytes);
				}
			} catch (IOException e) {
				System.out.println("Exception sending UserJOined to user id="+user.getUserID());
				e.printStackTrace();
			}
		}
		
	}

	public void registerUser(SGSUser user) 
		throws InstantiationException, IOException {		
		userMap.put(user.getUserID(),user);		
		fireUserJoined(user.getUserID());
		reportUserJoined(user.getUserID().toByteArray());
		// send already connected users to new joiner
		byte[] userIDbytes = user.getUserID().toByteArray();
		for(UserID oldUserID: userMap.keySet()){			
			if (oldUserID != user.getUserID()){
				user.userJoinedSystem(oldUserID.toByteArray());					
			}
		}		
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
		user.deregistered();
		fireUserLeft(id);
		for(SGSUser localUser : userMap.values()){
			if (localUser != user){
				try {
					localUser.userLeftSystem(id.toByteArray());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
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
			try {
				sgschan = new ChannelImpl(this,tchan);
				channelMap.put(sgschan.channelID(),sgschan);
				channelNameMap.put(channelName,sgschan);				
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
	

	public boolean validateReconnectKey(UserID uid, byte[] key ){
		BYTEARRAY currentKey = currentKeys.get(uid);
		if (currentKey.equals(key)){
			return true;
		} 
		BYTEARRAY pastKey = previousKeys.get(uid);
		if (pastKey.equals(key)){
			return true;
		} 
		return false;
	}
	
	public void setRouterListener(RouterListener l){
		listener = l;
	}

	public void serverMessage(boolean reliable, UserID userID, ByteBuffer databuff) {
		listener.serverMessage(userID,databuff,reliable);
		
	}

	

}
