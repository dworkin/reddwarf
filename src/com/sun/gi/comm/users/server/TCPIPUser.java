package com.sun.gi.comm.users.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.SGSUser;

import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.impl.TCPIPTransportListener;
import com.sun.gi.comm.users.protocol.impl.AbstractBinaryPktProtocol;
import com.sun.gi.utils.nio.NIOTCPConnection;
import com.sun.gi.utils.nio.NIOTCPConnectionListener;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.multicast.util.UnimplementedOperationException;

public class TCPIPUser  extends AbstractBinaryPktProtocol implements SGSUser
{
	
	private NIOTCPConnection conn;
	private Router router;
	private UserID userID;
	private boolean firstTime = true;
	private Map<ChannelID,SGSChannel> channelMap = new HashMap<ChannelID,SGSChannel>();

	public TCPIPUser(NIOTCPConnection connection, Router gameRouter){
		conn = connection;
		router = gameRouter;
	}

	// methods from AbstractBinaryPktProtcol
	@Override
	protected void sendBuffers(ByteBuffer[] buffs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void recvReconnectKey(byte[] user, byte[] key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvUserLeft(byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvJoinedChan(byte[] chanID, byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvLeftChan(byte[] chanID, byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvUserJoined(byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvUserRejected(String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvUserAccepted(byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvValidationResp(Callback[] cbs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvValidationReq(Callback[] cbs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvReconnectReq(byte[] user, byte[] key) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvConnectReq() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from, ByteBuffer databuff) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvReqJoinChan(String name, byte[] user) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void rcvJoinedChan(String name, byte[] chanID) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void disconnect() {
		// TODO Auto-generated method stub
		
	}

	public void packetReceived(NIOTCPConnection conn, ByteBuffer inputBuffer) {
		// TODO Auto-generated method stub
		
	}

	public void disconnected(NIOTCPConnection nIOTCPConnection) {
		// TODO Auto-generated method stub
		
	}

	public void joinedChan(SGSChannel channel) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void leftChan(SGSChannel channel) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void msgReceived(byte[] channel, byte[] from, boolean reliable, ByteBuffer data) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void validated() throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void invalidated(String message) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void validationRequested(Callback[] cbs) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void userJoinedSystem(byte[] user) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void userLeftSystem(byte[] user) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void userJoinedChannel(byte[] channelID, byte[] user) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void userLeftChannel(byte[] channel, byte[] user) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void reconnectKeyReceived(byte[] key) throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void setUserID(UserID id) {
		// TODO Auto-generated method stub
		
	}

	public UserID getUserID() {
		// TODO Auto-generated method stub
		return null;
	}

	public void disconnected() {
		// TODO Auto-generated method stub
		
	}

	public void userDisconnected() {
		// TODO Auto-generated method stub
		
	}
	
	//
	
}

	