#ifndef _TCPIPUserManagerClient_h
#define _TCPIPUserManagerClient_h

#include "IUserManagerClient.h"
#include "Protocol/ITransportProtocolClient.h"
#include "Protocol/ITransportProtocolTransmitter.h"
#include "Socket/ISocketManagerListener.h"
#include "Socket/ISocketListener.h"

TYPEDEF_SMART_PTR(IUserManagerClientListener);
TYPEDEF_SMART_PTR(ITransportProtocol);
TYPEDEF_SMART_PTR(ISocketManager);
TYPEDEF_SMART_PTR(ISocket);

class TCPIPUserManagerClient : 
	public IUserManagerClient, 
	public ITransportProtocolClient, 
	public ITransportProtocolTransmitter,
	public ISocketManagerListener,
	public ISocketListener
{
public:
	TCPIPUserManagerClient();

	// IUserManagerClient Interface
	virtual std::wstring GetClassName() const;

	virtual bool Connect(IDiscoveredUserManagerPtr userManager, IUserManagerClientListenerPtr listener);

	virtual void Login();
	virtual void ValidationDataResponse(const std::vector<ICallbackPtr>& callbacks);
	virtual void Logout();

	virtual void JoinChannel(const std::wstring& channelName);
	virtual void SendToServer(const	byte* data,	size_t length, bool	isReliable);
	virtual void SendUnicastMsg(const ChannelID& channelID,	const UserID& userID, const	byte* data,	size_t length, bool	isReliable);
	virtual void SendMulticastMsg(const	ChannelID& channelID, const	std::vector<UserID>& userID, const byte* data, size_t length, bool isReliable);
	virtual void SendBroadcastMsg(const	ChannelID& channelID, const	byte* data,	size_t length, bool	isReliable);

	virtual void ReconnectLogin(const UserID& userID, const	ReconnectionKey& reconnectionKey);

private:
	// ITransportProtocolClient Interface
	virtual void OnRcvUnicastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const UserID& to, const byte* data, size_t length);
	virtual void OnRcvMulticastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const std::vector<UserID>& to, const byte* data, size_t length);
	virtual void OnRcvBroadcastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const byte* data, size_t length);
	virtual void OnRcvValidationReq(const std::vector<ICallbackPtr>& callbacks);
	virtual void OnRcvUserAccepted(const UserID& user);
	virtual void OnRcvUserRejected(const std::wstring& user);
	virtual void OnRcvUserJoined(const UserID& user);
	virtual void OnRcvUserLeft(const UserID& user);
	virtual void OnRcvUserJoinedChan(const ChannelID& channelID, const UserID& user);
	virtual void OnRcvUserLeftChan(const ChannelID& channelID, const UserID& user);
	virtual void OnRcvReconnectKey(const UserID& user, const ReconnectionKey& key, int64 ttl);
	virtual void OnRcvJoinedChan(const std::wstring& channelName, const ChannelID& channelID);
	virtual void OnRcvLeftChan(const ChannelID& channelID);
	virtual void OnRcvUserDisconnected(const UserID& user);
	virtual void OnRcvServerID(const UserID& user);

	// ITransportProtocolTransmitter Interface
	virtual void SendBuffers(const BufferDescriptor* buffers, size_t count);
	virtual void CloseConnection();

	// ISocketManagerListener Interface
	virtual void OnConnected(ISocketPtr pSocket);
	virtual void OnConnectionFailed(ISocketPtr pSocket);

	// ISocketListener Interface
	virtual void OnPacketReceived(ISocketPtr pSocket, const byte* data, size_t length);
	virtual void OnDisconnected(ISocketPtr pSocket);

	ITransportProtocolPtr mProtocol;
	IUserManagerClientListenerPtr mListener;
	ISocketManagerPtr mSocketManager;
	ISocketPtr mSocket;
};

#endif
