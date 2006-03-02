#ifndef _IClientChannelListener_h
#define _IClientChannelListener_h

namespace Darkstar
{
	struct UserID;
	class IClientChannel;

	class CLIENTAPI IClientChannelListener
	{
	public:
		virtual ~IClientChannelListener() { }

		virtual void OnPlayerJoined(IClientChannel*, const UserID& playerID) = 0;
		virtual void OnPlayerLeft(IClientChannel*, const UserID& playerID) = 0;
		virtual void OnDataArrived(IClientChannel*, const UserID& fromID, const byte* data, size_t length, bool wasReliable) = 0;
		virtual void OnChannelClosed(IClientChannel*) = 0;
	};
}

#endif
