#include "stable.h"

#include "TCPIPUserManagerClient.h"

#include "IUserManagerClientListener.h"

#include "Discovery/DiscoveredUserManager.h"

#include "Protocol/BinaryPktProtocol.h"

#include "Socket/ISocketManager.h"
#include "Socket/ISocket.h"

#include "Platform/Platform.h"

TCPIPUserManagerClient::TCPIPUserManagerClient() :
	mProtocol(new BinaryPktProtocol()),
	mSocketManager(Platform::CreateSocketManager())
{
	boost::shared_ptr<TCPIPUserManagerClient> pThis(this, null_deleter());
	mProtocol->SetClient(pThis);
	mProtocol->SetTransmitter(pThis);
	mSocketManager->SetListener(pThis);
}

std::wstring TCPIPUserManagerClient::GetClassName() const
{
	return L"com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient";
}

bool TCPIPUserManagerClient::Connect(IDiscoveredUserManagerPtr userManager, IUserManagerClientListenerPtr listener)
{
	mListener = listener;

	std::wstring host = userManager->GetParameter(L"host");
	uint16 port = (uint16)_wtoi(userManager->GetParameter(L"port").c_str());
	//System.out.println("Attempting to connect to a TCPIP User Manager on host " + host + " port " + port);
	mSocketManager->Connect(host, port);
	if (mSocket != NULL)
	{
		boost::shared_ptr<TCPIPUserManagerClient> pThis(this, null_deleter());
		mSocket->SetListener(pThis);
	}
	return mSocket != NULL;
}

void TCPIPUserManagerClient::Login()
{
	mProtocol->SendLoginRequest();
}

void TCPIPUserManagerClient::ValidationDataResponse(const std::vector<ICallbackPtr>& callbacks)
{
	mProtocol->SendValidationResponse(callbacks);
}

void TCPIPUserManagerClient::Logout()
{
	mProtocol->SendLogoutRequest();
}

void TCPIPUserManagerClient::JoinChannel(const std::wstring& channelName)
{
	mProtocol->SendJoinChannelRequest(channelName);
}

void TCPIPUserManagerClient::SendToServer(const	byte* data,	size_t length, bool	isReliable)
{
	mProtocol->SendServerMsg(isReliable, data, length);
}

void TCPIPUserManagerClient::SendUnicastMsg(const ChannelID& channelID,	const UserID& userID, const	byte* data,	size_t length, bool	isReliable)
{
	mProtocol->SendUnicastMsg(channelID, userID, isReliable, data, length);
}

void TCPIPUserManagerClient::SendMulticastMsg(const	ChannelID& channelID, const	std::vector<UserID>& userID, const byte* data, size_t length, bool isReliable)
{
	mProtocol->SendMulticastMsg(channelID, userID, isReliable, data, length);
}

void TCPIPUserManagerClient::SendBroadcastMsg(const	ChannelID& channelID, const	byte* data,	size_t length, bool	isReliable)
{
	mProtocol->SendBroadcastMsg(channelID, isReliable, data, length);
}

void TCPIPUserManagerClient::ReconnectLogin(const UserID& userID, const	ReconnectionKey& reconnectionKey)
{
	mProtocol->SendReconnectRequest(userID, reconnectionKey);
}

void TCPIPUserManagerClient::OnRcvUnicastMsg(bool wasReliable, const ChannelID& channelID, const UserID& from, const UserID& /*to*/, const byte* data, size_t length)
{
	if (mListener)
		mListener->OnRecvdData(channelID, from, data, length, wasReliable);
}

void TCPIPUserManagerClient::OnRcvMulticastMsg(bool wasReliable, const ChannelID& channelID, const UserID& from, const std::vector<UserID>& /*to*/, const byte* data, size_t length)
{
	if (mListener)
		mListener->OnRecvdData(channelID, from, data, length, wasReliable);
}

void TCPIPUserManagerClient::OnRcvBroadcastMsg(bool wasReliable, const ChannelID& channelID, const UserID& from, const byte* data, size_t length)
{
	if (mListener)
		mListener->OnRecvdData(channelID, from, data, length, wasReliable);
}

void TCPIPUserManagerClient::OnRcvValidationReq(const std::vector<ICallbackPtr>& callbacks)
{
	if (mListener)
		mListener->OnValidationDataRequest(callbacks);
}

void TCPIPUserManagerClient::OnRcvUserAccepted(const UserID& user)
{
	if (mListener)
		mListener->OnLoginAccepted(user);
}

void TCPIPUserManagerClient::OnRcvUserRejected(const std::wstring& message)
{
	if (mListener)
		mListener->OnLoginRejected(message);
}

void TCPIPUserManagerClient::OnRcvUserJoined(const UserID& user)
{
	if (mListener)
		mListener->OnUserAdded(user);
}

void TCPIPUserManagerClient::OnRcvUserLeft(const UserID& user)
{
	if (mListener)
		mListener->OnUserDropped(user);
}

void TCPIPUserManagerClient::OnRcvUserJoinedChan(const ChannelID& channelID, const UserID& user)
{
	if (mListener)
		mListener->OnUserJoinedChannel(channelID, user);
}

void TCPIPUserManagerClient::OnRcvUserLeftChan(const ChannelID& channelID, const UserID& user)
{
	if (mListener)
		mListener->OnUserLeftChannel(channelID, user);
}

void TCPIPUserManagerClient::OnRcvReconnectKey(const UserID& /*user*/, const ReconnectionKey& key, int64 ttl)
{
	if (mListener)
		mListener->OnNewConnectionKeyIssued(key, ttl);
}

void TCPIPUserManagerClient::OnRcvJoinedChan(const std::wstring& channelName, const ChannelID& channelID)
{
	if (mListener)
		mListener->OnJoinedChannel(channelName, channelID);
}

void TCPIPUserManagerClient::OnRcvLeftChan(const ChannelID& channelID)
{
	if (mListener)
		mListener->OnLeftChannel(channelID);
}

void TCPIPUserManagerClient::OnRcvUserDisconnected(const UserID& /*user*/)
{
	if (mListener)
		mListener->OnDisconnected();
}

void TCPIPUserManagerClient::OnRcvServerID(const UserID& user)
{
	if (mListener)
		mListener->OnRecvServerID(user);
}

void TCPIPUserManagerClient::SendBuffers(const BufferDescriptor* buffers, size_t count)
{
	if (mSocket)
		mSocket->Send(buffers, count);
}

void TCPIPUserManagerClient::CloseConnection()
{
	if (mSocket)
		mSocket->Disconnect();
}

void TCPIPUserManagerClient::OnConnected(ISocketPtr pSocket)
{
	mSocket = pSocket;
	if (mListener)
		mListener->OnConnected();
}

void TCPIPUserManagerClient::OnConnectionFailed(ISocketPtr /*pSocket*/)
{
	if (mListener)
		mListener->OnDisconnected();
}

void TCPIPUserManagerClient::OnPacketReceived(ISocketPtr /*pSocket*/, const byte* data, size_t length)
{
	if (mProtocol)
		mProtocol->PacketReceived(data, length);

}

void TCPIPUserManagerClient::OnDisconnected(ISocketPtr /*pSocket*/)
{
	if (mListener)
		mListener->OnDisconnected();
}
