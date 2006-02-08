#ifndef _IClientChannelListener_h
#define _IClientChannelListener_h

struct UserID;

class CLIENTAPI IClientChannelListener
{
public:
	virtual void OnPlayerJoined(const UserID& playerID) = 0;

	virtual void OnPlayerLeft(const UserID& playerID) = 0;

	virtual void OnDataArrived(const UserID& fromID, const byte* data, size_t length, bool wasReliable) = 0;

	virtual void OnChannelClosed() = 0;
};

#endif
