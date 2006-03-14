/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

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

using namespace SGS;
using namespace SGS::Internal;

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
