#ifndef _IClientChannel_h
#define _IClientChannel_h

namespace Darkstar
{
	struct UserID;
	class IClientChannelListener;

	class CLIENTAPI IClientChannel
	{
	public:
		virtual ~IClientChannel() { }

		virtual std::wstring GetName() const = 0;
		virtual void SetListener(IClientChannelListener* clientChannelListener) = 0;
		virtual void SendUnicastData(const UserID& to, const byte* data, size_t length, bool isReliable) = 0;
		virtual void SendMulticastData(const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable) = 0;
		virtual void SendBroadcastData(const byte* data, size_t length, bool isReliable) = 0;
		virtual void Close() = 0;
	};
}

#endif
