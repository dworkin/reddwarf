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
