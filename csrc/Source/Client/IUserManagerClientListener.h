#ifndef _IUserManagerClientListener_h
#define _IUserManagerClientListener_h

struct ChannelID;
struct UserID;
struct ReconnectionKey;

TYPEDEF_SMART_PTR(ICallback);

class CLIENTAPI IUserManagerClientListener
{
public:
	virtual void OnConnected() = 0;
	virtual void OnDisconnected() = 0;
	virtual void OnNewConnectionKeyIssued(const ReconnectionKey& key, int64 ttl) = 0;
	virtual void OnValidationDataRequest(const std::vector<ICallbackPtr>& callbacks) = 0;
	virtual void OnLoginAccepted(const UserID& userID) = 0;
	virtual void OnLoginRejected(const std::wstring& message) = 0;
	virtual void OnUserAdded(const UserID& userID) = 0;
	virtual void OnUserDropped(const UserID& userID) = 0; 

	virtual void OnJoinedChannel(const std::wstring& name, const ChannelID& channelID) = 0;
	virtual void OnLeftChannel(const ChannelID& channelID) = 0;
	virtual void OnUserJoinedChannel(const ChannelID& channelID, const UserID& userID) = 0;
	virtual void OnUserLeftChannel(const ChannelID& channelID, const UserID& userID) = 0;

	virtual void OnRecvdData(const ChannelID& channelID, const UserID& from, const byte* data, size_t length, bool wasReliable) = 0;
	virtual void OnRecvServerID(const UserID& userID) = 0;
};

#endif
