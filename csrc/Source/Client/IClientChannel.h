#ifndef _IClientChannel_h
#define _IClientChannel_h

struct UserID;
TYPEDEF_SMART_PTR(IClientChannelListener);

class CLIENTAPI IClientChannel
{
public:
	virtual std::wstring GetName() const = 0;

	virtual void SetListener(IClientChannelListenerPtr clientChannelListener) = 0;
	
	virtual void SendUnicastData(const UserID& to, const byte* data, size_t length, bool isReliable) = 0;
	
	virtual void SendMulticastData(const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable) = 0;
	
	virtual void SendBroadcastData(const byte* data, size_t length, bool isReliable) = 0;
};

#endif
