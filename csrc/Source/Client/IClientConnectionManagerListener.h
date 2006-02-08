#ifndef _IClientConnectionManagerListener_h
#define _IClientConnectionManagerListener_h

struct UserID;
TYPEDEF_SMART_PTR(ICallback);
TYPEDEF_SMART_PTR(IClientChannel);

class CLIENTAPI IClientConnectionManagerListener
{
public:
	virtual void OnValidationRequest(const std::vector<ICallbackPtr>& callbacks) = 0;

	virtual void OnConnected(const UserID& myID) = 0;
	virtual void OnConnectionRefused(const std::wstring& message) = 0;

	virtual void OnFailOverInProgress() = 0;
	virtual void OnReconnected() = 0;
	virtual void OnDisconnected() = 0;
  
	virtual void OnUserJoined(const UserID& myID) = 0;
	virtual void OnUserLeft(const UserID& myID) = 0;

	virtual void OnJoinedChannel(IClientChannelPtr clientChannel) = 0;
};

#endif
