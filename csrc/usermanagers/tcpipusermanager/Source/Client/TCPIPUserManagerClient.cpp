#include "stable.h"

#include "TCPIPUserManagerClient.h"

#include "Client/IUserManagerClientListener.h"

#include "Discovery/IDiscoveredUserManager.h"

#include "Protocol/BinaryPktProtocol.h"

#include "Socket/ISocketManager.h"
#include "Socket/ISocket.h"

#include "Platform/Platform.h"

#if defined(SN_TARGET_PSP_HW)
int _wtoi(const wchar_t*)
{
	throw std::exception();
	//return 0;
}
#endif

using namespace Darkstar;
using namespace Darkstar::Internal;

EXPORT_USERMANAGERCLIENT(L"com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient", TCPIPUserManagerClient);

TCPIPUserManagerClient::TCPIPUserManagerClient() :
	mProtocol(new BinaryPktProtocol()),
	mSocketManager(Platform::CreateSocketManager()),
	mListener(NULL),
	mTCPSocket(NULL),
	mUDPSocket(NULL)
{
	mProtocol->SetClient(this);
	mProtocol->SetTransmitter(this);
	mSocketManager->SetListener(this);
}

bool TCPIPUserManagerClient::Connect(IDiscoveredUserManager* userManager, IUserManagerClientListener* listener)
{
	mListener = listener;

	std::wstring host = userManager->GetParameter(L"host");
	uint16 port = (uint16)_wtoi(userManager->GetParameter(L"port").c_str());

	mTCPSocket = mSocketManager->CreateSocket(kStream);
	if (mTCPSocket == NULL)
		return false;
	mTCPSocket->SetListener(this);

	mUDPSocket = mSocketManager->CreateSocket(kDatagram);
	if (mUDPSocket == NULL)
		return false;
	mUDPSocket->SetListener(this);

	if (!mTCPSocket->Connect(host, port))
		return false;

	return true;
}

void TCPIPUserManagerClient::Login()
{
	mProtocol->SendLoginRequest();
}

void TCPIPUserManagerClient::ValidationDataResponse(const std::vector<ICallback*>& callbacks)
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

void TCPIPUserManagerClient::LeaveChannel(const ChannelID& channelID)
{
	mProtocol->SendLeaveChannelRequest(channelID);
}

void TCPIPUserManagerClient::Update()
{
	mSocketManager->Update();
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

void TCPIPUserManagerClient::OnRcvValidationReq(const std::vector<ICallback*>& callbacks)
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

void TCPIPUserManagerClient::OnRcvChannelLocked(const std::wstring& channelName, const UserID& userID)
{
	if (mListener)
		mListener->OnChannelLocked(channelName, userID);
}

void TCPIPUserManagerClient::SendBuffers(const BufferDescriptor* buffers, size_t count, bool isReliable)
{
	if (isReliable && mTCPSocket)
		mTCPSocket->Send(buffers, count);
	else if (!isReliable && mUDPSocket)
		mUDPSocket->Send(buffers, count);
}

void TCPIPUserManagerClient::CloseConnection()
{
	if (mTCPSocket)
		mTCPSocket->Disconnect();
	if (mUDPSocket)
		mUDPSocket->Disconnect();
}

void TCPIPUserManagerClient::OnConnected(ISocket* pSocket)
{
	if (mTCPSocket == pSocket)
	{
		std::pair<std::wstring, uint16> localAddress = mTCPSocket->GetLocalAddress();
		std::pair<std::wstring, uint16> peerAddress = mTCPSocket->GetPeerAddress();

		mUDPSocket->Bind(localAddress.first, localAddress.second);
		mUDPSocket->Connect(peerAddress.first, peerAddress.second);

		if (mListener)
			mListener->OnConnected();
	}
}

void TCPIPUserManagerClient::OnConnectionFailed(ISocket* pSocket)
{
	if (mTCPSocket == pSocket)
		if (mListener)
			mListener->OnDisconnected();
}

void TCPIPUserManagerClient::OnPacketReceived(ISocket* /*pSocket*/, const byte* data, size_t length)
{
	if (mProtocol.get())
		mProtocol->PacketReceived(data, length);
}

void TCPIPUserManagerClient::OnDisconnected(ISocket* /*pSocket*/)
{
	if (mListener)
		mListener->OnDisconnected();
}
