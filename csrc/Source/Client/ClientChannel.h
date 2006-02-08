#ifndef _ClientChannel_h
#define _ClientChannel_h

TYPEDEF_SMART_PTR(ClientConnectionManager);
TYPEDEF_SMART_PTR(IClientChannelListener);

#include "IClientChannel.h"
#include "ChannelID.h"

class ClientChannel : public IClientChannel
{
public:
	ClientChannel(ClientConnectionManagerPtr manager, const std::wstring& channelName, const ChannelID& channelID);

	virtual std::wstring GetName() const;
	
	virtual void SetListener(IClientChannelListenerPtr listeners);

	virtual void SendUnicastData(const UserID& to, const byte* data, size_t length, bool isReliable);
	
	virtual void SendMulticastData(const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable);
	
	virtual void SendBroadcastData(const byte* data, size_t length, bool isReliable);

	void OnChannelClosed();
	void OnUserJoined(const UserID& userID);
	void OnUserLeft(const UserID& userID);
	void OnDataReceived(const UserID& from, const byte* data, size_t length, bool wasReliable);

private:
	ChannelID mID;
	std::wstring mName;
	ClientConnectionManagerPtr mManager;
	IClientChannelListenerPtr mListener;
};

#endif
