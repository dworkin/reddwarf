package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;


import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.impl.TCPIPTransportListener;
import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolServer;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.utils.nio.NIOTCPConnection;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.multicast.util.UnimplementedOperationException;

public class TCPIPUser  implements SGSUser,TransportProtocolServer { 
{
	private Router router;
	private UserID userID;
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();
	private TransportProtocol transport;
	private UserValidator validator;
	 
	public TCPIPUser(Router router, TransportProtocolTransmitter xmitter, UserValidator validator){
		this.validator = validator;
		this.router = router;
		transport = new BinaryPktProtocol();
		transport.setTransmitter(xmitter);
		transport.setServer(this);
	}

	public void joinedChan(SGSChannel channel) throws IOException {
		channelMap.put(channel.channelID(),channel);
		transport.deliverJoinedChannel(channel.channelID().toByteArray());		
	}

	public void leftChan(SGSChannel channel) throws IOException {
		channelMap.remove(channel.channelID());
		transport.deliverLeftChannel(channel.channelID().toByteArray());
		
	}

	public void msgReceived(byte[] channel, byte[] from, boolean reliable, ByteBuffer data) throws IOException {
		transport.deliverUnicastMsg(channel,from,userID.toByteArray(),reliable,data);
		
	}


	public void userJoinedSystem(byte[] user) throws IOException {
		transport.deliverUserJoined(user);
		
	}

	public void userLeftSystem(byte[] user) throws IOException {
		transport.deliverUserLeft(user);
		
	}

	public void userJoinedChannel(byte[] channelID, byte[] user) throws IOException {
		transport.deliverUserJoinedChannel(channelID,user);
		
	}

	public void userLeftChannel(byte[] channel, byte[] user) throws IOException {
		transport.deliverUserLeftChannel(channel,user);
		
	}

	public void reconnectKeyReceived(byte[] key) throws IOException {
		transport.deliverReconnectKey(userID.toByteArray(),key);
		
	}

	
	public UserID getUserID() {
		return userID;
	}
	
	public void deregistered() {
		// TODO Auto-generated method stub
		
	}

	
	// TransportProtoclServer callbacks
	
	public void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff) {
		SGSChannel chan = channelMap.get(new ChannelID(chanID));
	    // should never be NULL, if it is we want an exception to figure out why
		chan.unicastData(new UserID(from), new UserID(to), databuff,reliable);
	}

	public void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff) {
		SGSChannel chan = channelMap.get(new ChannelID(chanID));
		UserID[]ids = new UserID[tolist.length];
		for(int i=0;i<ids.length;i++){
			ids[i] = new UserID(tolist[i]); 
		}
		//should never be NULL, if it is we want an exception to figure out why
		chan.multicastData(new UserID(from),ids,databuff,reliable);
	}

	public void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from, ByteBuffer databuff) {
		SGSChannel chan = channelMap.get(new ChannelID(chanID));
	    // should never be NULL, if it is we want an exception to figure out why
		chan.broadcastData(new UserID(from), databuff,reliable);
	}

	

	public void rcvConnectReq() {
		if (validator == null){
			router.registerUser(this);
		} else {
			validator.reset();
			if (validator.authenticated()){ //no tests 
				router.registerUser(this);
			} else	 {
				Callback[] cbs = validator.nextDataRequest();
				transport.deliverValidationRequest(cbs);
			}
		}
		
	}

	public void rcvValidationResp(Callback[] cbs) {
		validator.dataResponse(cbs);
		if (validator.authenticated()){
			router.registerUser(this);
		} else {
			cbs = validator.nextDataRequest();
			if (cbs == null) { //no more authentication
				transport.deliverUserRejected("Validation failure");
			} else {
				transport.deliverValidationRequest(cbs);
			}
		}
	}
	
	public void rcvReconnectReq(byte[] user, byte[] key) {
		userID = new UserID(user);
		if (router.validateReconnectKey(userID,key){
			router.registerUser(this);
		} else {
			transport.deliverUserRejected("Reconnect key failure");
		}
		
	}

	public void rcvReqJoinChan(byte[] chanID, byte[] user) {
		// TODO Auto-generated method stub
		
	}

	
	

	
	//
	
}

	