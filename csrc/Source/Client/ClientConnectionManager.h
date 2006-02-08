#ifndef _ClientConnectionManager_h
#define _ClientConnectionManager_h

#include "UserID.h"
#include "ChannelID.h"
#include "ReconnectionKey.h"

#include "IClientConnectionManager.h"
#include "IUserManagerClientListener.h"

TYPEDEF_SMART_PTR(IDiscoverer);
TYPEDEF_SMART_PTR(IDiscoveredGame);
TYPEDEF_SMART_PTR(IUserManagerClient);
TYPEDEF_SMART_PTR(IUserManagerPolicy);

TYPEDEF_SMART_PTR(ClientChannel);

class CLIENTAPI ClientConnectionManager : 
	public IClientConnectionManager,
	public IUserManagerClientListener
{
public:
	ClientConnectionManager(const std::wstring& gameName, IDiscovererPtr discoverer);
	ClientConnectionManager(const std::wstring& gameName, IDiscovererPtr discoverer, IUserManagerPolicyPtr policy);

	// IClientConnectionManager Implementation
	virtual void SetListener(IClientConnectionManagerListenerPtr clientConnectionManagerListener);
  
	virtual std::vector<std::wstring> GetUserManagerClassNames();

	virtual bool Connect(const std::wstring& userManagerClassName);
	virtual bool Connect(const std::wstring& userManagerClassName, int connectAttempts, int msBetweenAttempts);
	virtual void Disconnect();

	virtual void SendValidationResponse(const std::vector<ICallbackPtr>& callbacks);
	virtual void SendToServer(const byte* data, size_t length, bool isReliable);

	virtual void OpenChannel(const std::wstring& channelName);
	virtual bool IsServerID(const UserID& userID);

	// IUserManagerClientListener Implementation
	virtual void OnConnected();
	virtual void OnDisconnected();
	virtual void OnNewConnectionKeyIssued(const ReconnectionKey& key, int64 ttl);
	virtual void OnValidationDataRequest(const std::vector<ICallbackPtr>& callbacks);
	virtual void OnLoginAccepted(const UserID& userID);
	virtual void OnLoginRejected(const std::wstring& message);
	virtual void OnUserAdded(const UserID& userID);
	virtual void OnUserDropped(const UserID& userID); 

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
	IDiscoveredGamePtr discoverGame(const std::wstring& gameName);
	bool connect(IUserManagerClientPtr userManager);

private:
	UserID mMyID;
	UserID mServerID;

	IDiscovererPtr mDiscoverer;
	IUserManagerClientPtr mUserManager;
	IUserManagerPolicyPtr mPolicy;

	IClientConnectionManagerListenerPtr mListener;

	std::wstring mGameName;
	ReconnectionKey mReconnectionKey;

	bool mReconnecting;
	bool mConnected;

	std::map<ChannelID, ClientChannelPtr> mChannelMap;

	int mKeyTimeoutMS;
	int mConnAttempts;
	int mConnAttemptCounter;
	int mConnWaitMS;
	bool mExiting;
};

#endif
