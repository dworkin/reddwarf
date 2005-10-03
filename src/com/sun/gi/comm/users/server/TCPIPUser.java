package com.sun.gi.comm.users.server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.SGSUser;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.transport.impl.AbstractBinaryPktTransport;
import com.sun.gi.comm.users.impl.TCPIPTransportListener;
import com.sun.gi.utils.nio.NIOTCPConnection;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.multicast.util.UnimplementedOperationException;

public class TCPIPUser  extends AbstractBinaryPktTransport implements NIOTCPConnectionListener, 
	SGSUser{
	
	private NIOTCPConnection conn;
	private Router router;
	private UserID userID;
	private boolean firstTime = true;
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();

	public TCPIPUser(NIOTCPConnection connection, Router gameRouter){
		conn = connection;
		router = gameRouter;
	}
	
	

	// NIOTCPConnetionListener

	public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
		if (firstTime) {
			if (isLoginPkt(inputBuffer)){ // to defeat DOS attacks
				firstTime = false;
			} else {
				conn.disconnect();
			}
		} else {
			packetReceived(inputBuffer); // pass to parent to decode
		}	
	}

	public void disconnected(NIOTCPConnection nIOTCPConnection) {
		router.deregisterUser(this);
		
	}



	@Override
	protected void sendBuffers(ByteBuffer[] buffs) {
		conn.send(buffs);
	}



	@Override
	protected void recvReconnectKey(byte[] user, byte[] key) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvUserLeft(byte[] user) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvJoinedChan(byte[] chanID, byte[] user) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvLeftChan(byte[] chanID, byte[] user) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvUserJoined(byte[] user) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvUserRejected(String message) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvUserAccepted(byte[] user) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvValidationResp(Callback[] cbs) {
		router.validationResponse(this,cbs);
		
	}



	@Override
	protected void rcvValidationReq(Callback[] cbs) {
		throw new UnimplementedOperationException();
		
	}



	@Override
	protected void rcvReconnectReq(byte[] user, byte[] key) {
		router.reregisterUser(this,user,key);
		
	}



	@Override
	protected void rcvConnectReq() {
		router.registerUser(this);
		
	}

	private SGSChannel getChannel(byte[] id){
		ChannelID cid = router.makeChannelID(id);
		return channelMap.get(cid);
	}

	@Override
	protected void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from, ByteBuffer databuff) {
		SGSChannel chan = getChannel(chanID);
		chan.broadcastData(userID,databuff,reliable);
		
	}



	@Override
	protected void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff) {
		SGSChannel chan = getChannel(chanID);
		chan.multicastData(userID, tolist, databuff,reliable);
	}



	@Override
	protected void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff) {
		SGSChannel chan = getChannel(chanID);
		chan.unicastData(userID,to,databuff,reliable);
		
	}



	@Override
	protected void rcvReqJoinChan(String name,byte[] user) {
		SGSChannel channel = router.openChannel(name);
		channelMap.put(channel.channelID(),channel);
		channel.join(this);
		
	}



	@Override
	protected void rcvJoinedChan(String name, byte[] chanID) {
		throw new UnimplementedOperationException();
		
	}



	