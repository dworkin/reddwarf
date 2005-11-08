package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolServer;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.comm.users.validation.UserValidator;

public class SGSUserImpl implements SGSUser, TransportProtocolServer {
	private Router router;
	private Subject subject;
	private UserID userID;

	private Map<ChannelID, SGSChannel> channelMap = new HashMap<ChannelID, SGSChannel>();

	private TransportProtocol transport;

	private UserValidator[] validators;
	private int validatorCounter; 

	public SGSUserImpl(Router router, TransportProtocolTransmitter xmitter,
			UserValidator[] validators) {
		this.validators = validators;
		this.router = router;
		transport = new BinaryPktProtocol();
		transport.setTransmitter(xmitter);
		transport.setServer(this);
		try {
			userID = new UserID();
		} catch (InstantiationException e) {
			
			e.printStackTrace();
		}
	}

	public void joinedChan(SGSChannel channel) throws IOException {
		channelMap.put(channel.channelID(), channel);
		transport.deliverJoinedChannel(channel.channelID().toByteArray());
	}

	public void leftChan(SGSChannel channel) throws IOException {
		channelMap.remove(channel.channelID());
		transport.deliverLeftChannel(channel.channelID().toByteArray());

	}

	public void msgReceived(byte[] channel, byte[] from, boolean reliable,
			ByteBuffer data) throws IOException {
		transport.deliverUnicastMsg(channel, from, userID.toByteArray(),
				reliable, data);

	}

	public void userJoinedSystem(byte[] user) throws IOException {
		transport.deliverUserJoined(user);

	}

	public void userLeftSystem(byte[] user) throws IOException {
		transport.deliverUserLeft(user);

	}

	public void userJoinedChannel(byte[] channelID, byte[] user)
			throws IOException {
		transport.deliverUserJoinedChannel(channelID, user);

	}

	public void userLeftChannel(byte[] channel, byte[] user) throws IOException {
		transport.deliverUserLeftChannel(channel, user);

	}

	public void reconnectKeyReceived(byte[] key) throws IOException {
		transport.deliverReconnectKey(userID.toByteArray(), key);

	}

	public UserID getUserID() {
		return userID;
	}

	public void deregistered() {
		// TODO Auto-generated method stub

	}

	// TransportProtoclServer callbacks

	public void rcvUnicastMsg(boolean reliable, byte[] chanID, 
			byte[] to, ByteBuffer databuff) {
		SGSChannel chan;
		try {
			chan = channelMap.get(new ChannelID(chanID));
			// should never be NULL, if it is we want an exception to figure out
			// why
			chan.unicastData(userID, new UserID(to), databuff,
					reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void rcvMulticastMsg(boolean reliable, byte[] chanID,
			byte[][] tolist, ByteBuffer databuff) {
		SGSChannel chan;
		try {
			chan = channelMap.get(new ChannelID(chanID));

			UserID[] ids = new UserID[tolist.length];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = new UserID(tolist[i]);
			}
			// should never be NULL, if it is we want an exception to figure out
			// why
			chan.multicastData(userID, ids, databuff, reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void rcvBroadcastMsg(boolean reliable, byte[] chanID, 
			ByteBuffer databuff) {
		try {
			SGSChannel chan = channelMap.get(new ChannelID(chanID));
			// should never be NULL, if it is we want an exception to figure out
			// why
			chan.broadcastData(userID, databuff, reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void rcvConnectReq() {
		try {
			if (validators == null) {
				router.registerUser(this);
				transport.deliverUserAccepted(userID.toByteArray());
			} else {				
				startValidation();
			}
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * 
	 */
	private void startValidation() {
		validatorCounter = 0;
		subject = new Subject();		
		doValidationReq();
	}

	/**
	 * @return
	 */
	private void doValidationReq() {
		Callback[] cb = validators[validatorCounter].nextDataRequest();
		while ((cb == null)&&(validatorCounter<validators.length)){
			if (!validators[validatorCounter].authenticated()){ // rejected
				try {
					transport.deliverUserRejected("Validation failed");
					return;
				} catch (IOException e) {					
					e.printStackTrace();
				}
				// TODO need to disconnet the connection
			} else { // go on
				validatorCounter++;
				if (validatorCounter<validators.length){
					validators[validatorCounter].reset(subject);
					cb = validators[validatorCounter].nextDataRequest();
				} else {
					cb = null;
				}
			}
		}
		if (cb == null){ // we have done them all and are authenticated
			try {				
				transport.deliverUserAccepted(userID.toByteArray());
				router.registerUser(this);
			} catch (InstantiationException e) {				
				e.printStackTrace();
			} catch (IOException e) {				
				e.printStackTrace();
			}			
		} else { // next CBs to request
			try {
				transport.deliverValidationRequest(cb);
			} catch (UnsupportedCallbackException e) {				
				e.printStackTrace();
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}
	}

	public void rcvValidationResp(Callback[] cbs) {
		validators[validatorCounter].dataResponse(cbs);
		doValidationReq();
	}

	public void rcvReconnectReq(byte[] user, byte[] key) {
		try {
			userID = new UserID(user);
			if (router.validateReconnectKey(userID, key)) {
				router.registerUser(this);
			} else {
				transport.deliverUserRejected("Reconnect key failure");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}
	

	public void rcvServerMsg(boolean reliable, ByteBuffer databuff) {
		router.serverMessage(reliable,userID,databuff);
		
	}
	

	public void rcvReqLeaveChan(byte[] chanID) {
		try {
			SGSChannel chan = channelMap.get(new ChannelID(chanID));
			chan.leave(this);
		} catch (InstantiationException e) {			
			e.printStackTrace();
		}
		
	}

	public void rcvReqJoinChan(String channame) {
		router.openChannel(channame);		
	}

	/**
	 * This method is used to pass packet data arrivign on the raw connection
	 * to be processed by the user.
	 * @param inputBuffer
	 */
	protected void packetReceived(ByteBuffer inputBuffer) {
		transport.packetReceived(inputBuffer);		
	}

	/**
	 * This method is called by the raw connection to indicate that the
	 * conenction has been lost.
	 */
	protected void disconnected() {
		// TODO currently this just immediately dereigsters user.  
		// This must be modified for fail-over when we support multiple stacks
		router.deregisterUser(this);		
	}

}
