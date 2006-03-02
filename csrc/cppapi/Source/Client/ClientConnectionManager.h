#ifndef _ClientConnectionManager_h
#define _ClientConnectionManager_h

#include "UserID.h"
#include "ChannelID.h"
#include "ReconnectionKey.h"

#include "IClientConnectionManager.h"
#include "IUserManagerClient.h"
#include "IUserManagerClientListener.h"

namespace Darkstar
{
	class IDiscoverer;
	class IDiscoveredGame;
	class IUserManagerPolicy;

	namespace Internal
	{
		class ClientChannel;
	}

	class CLIENTAPI ClientConnectionManager : 
		public IClientConnectionManager,
		public IUserManagerClientListener
	{
	public:
		ClientConnectionManager(const std::wstring& gameName, IDiscoverer* discoverer, IUserManagerPolicy* policy);
		virtual ~ClientConnectionManager();

		// IClientConnectionManager Implementation
		virtual void SetListener(IClientConnectionManagerListener* clientConnectionManagerListener);
	  
		virtual std::vector<std::wstring> GetUserManagerClassNames();

		virtual bool Connect(const std::wstring& userManagerClassName);
		virtual bool Connect(const std::wstring& userManagerClassName, int connectAttempts, int msBetweenAttempts);
		virtual void Disconnect();

		virtual void SendValidationResponse(const std::vector<ICallback*>& callbacks);
		virtual void SendToServer(const byte* data, size_t length, bool isReliable);

		virtual void OpenChannel(const std::wstring& channelName);
		virtual bool IsServerID(const UserID& userID);
		virtual void CloseChannel(const ChannelID& channelID);

		virtual void Update();

		// IUserManagerClientListener Implementation
		virtual void OnConnected();
		virtual void OnDisconnected();
		virtual void OnNewConnectionKeyIssued(const ReconnectionKey& key, int64 ttl);
		virtual void OnValidationDataRequest(const std::vector<ICallback*>& callbacks);
		virtual void OnLoginAccepted(const UserID& userID);
		virtual void OnLoginRejected(const std::wstring& message);
		virtual void OnUserAdded(const UserID& userID);
		virtual void OnUserDropped(const UserID& userID); 

		virtual void OnChannelLocked(const std::wstring& name, const UserID& userID);
		virtual void OnJoinedChannel(const std::wstring& name, const ChannelID& channelID);
		virtual void OnLeftChannel(const ChannelID& channelID);
		virtual void OnUserJoinedChannel(const ChannelID& channelID, const UserID& userID);
		virtual void OnUserLeftChannel(const ChannelID& channelID, const UserID& userID);

		virtual void OnRecvdData(const ChannelID& channelID, const UserID& from, const byte* data, size_t length, bool wasReliable);
		virtual void OnRecvServerID(const UserID& userID);

		void SendUnicastData(const ChannelID& channelID, const UserID& to, const byte* data, size_t length, bool isReliable);
		void SendMulticastData(const ChannelID& channelID, const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable);
		void SendBroadcastData(const ChannelID& channelID, const byte* data, size_t length, bool isReliable);

	private:
		IDiscoveredGame* discoverGame(const std::wstring& gameName);
		bool connect();

	private:
		UserID mMyID;
		UserID mServerID;

		IDiscoverer* mDiscoverer;
		IUserManagerPolicy* mPolicy;

		IClientConnectionManagerListener* mListener;

		std::wstring mUserManagerClassName;
		IUserManagerClient* mUserManager;

		std::wstring mGameName;
		ReconnectionKey mReconnectionKey;

		bool mReconnecting;
		bool mConnected;

		std::map<ChannelID, Internal::ClientChannel*> mChannelMap;

		int mKeyTimeoutMS;
		int mConnAttempts;
		int mConnAttemptCounter;
		int mConnWaitMS;
		bool mExiting;
	};
}

#endif
