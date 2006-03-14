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

#ifndef _TCPIPUserManagerClient_h
#define _TCPIPUserManagerClient_h

#include "Client/IUserManagerClient.h"
#include "Protocol/ITransportProtocolClient.h"
#include "Protocol/ITransportProtocolTransmitter.h"
#include "Socket/ISocketManagerListener.h"
#include "Socket/ISocketListener.h"

namespace SGS
{
	class IUserManagerClientListener;
	class ITransportProtocol;
	class ISocketManager;
	class ISocket;

	namespace Internal
	{
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
			virtual bool Connect(IDiscoveredUserManager* userManager, IUserManagerClientListener* listener);

			virtual void Login();
			virtual void ValidationDataResponse(const std::vector<ICallback*>& callbacks);
			virtual void Logout();

			virtual void JoinChannel(const std::wstring& channelName);
			virtual void SendToServer(const	byte* data,	size_t length, bool	isReliable);
			virtual void SendUnicastMsg(const ChannelID& channelID,	const UserID& userID, const	byte* data,	size_t length, bool	isReliable);
			virtual void SendMulticastMsg(const	ChannelID& channelID, const	std::vector<UserID>& userID, const byte* data, size_t length, bool isReliable);
			virtual void SendBroadcastMsg(const	ChannelID& channelID, const	byte* data,	size_t length, bool	isReliable);

			virtual void ReconnectLogin(const UserID& userID, const	ReconnectionKey& reconnectionKey);

			virtual void LeaveChannel(const ChannelID& channelID);

			virtual void Update();

		private:
			// ITransportProtocolClient Interface
			virtual void OnRcvUnicastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const UserID& to, const byte* data, size_t length);
			virtual void OnRcvMulticastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const std::vector<UserID>& to, const byte* data, size_t length);
			virtual void OnRcvBroadcastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const byte* data, size_t length);
			virtual void OnRcvValidationReq(const std::vector<ICallback*>& callbacks);
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
			virtual void OnRcvChannelLocked(const std::wstring& channelName, const UserID& userID);

			// ITransportProtocolTransmitter Interface
			virtual void SendBuffers(const BufferDescriptor* buffers, size_t count, bool isReliable);
			virtual void CloseConnection();

			// ISocketManagerListener Interface
			virtual void OnConnected(ISocket* pSocket);
			virtual void OnConnectionFailed(ISocket* pSocket);

			// ISocketListener Interface
			virtual void OnPacketReceived(ISocket* pSocket, const byte* data, size_t length);
			virtual void OnDisconnected(ISocket* pSocket);

			std::auto_ptr<ITransportProtocol> mProtocol;

			std::auto_ptr<ISocketManager> mSocketManager;
			ISocket* mTCPSocket;
			ISocket* mUDPSocket;

			IUserManagerClientListener* mListener;
		};
	}
}

#endif
