#ifndef _ClientChannel_h
#define _ClientChannel_h

#include "IClientChannel.h"
#include "ChannelID.h"

namespace Darkstar
{
	class IClientChannelListener;
	class ClientConnectionManager;

	namespace Internal
	{
		class ClientChannel : public IClientChannel
		{
		public:
			ClientChannel(ClientConnectionManager* manager, const std::wstring& channelName, const ChannelID& channelID);

			// IClientChannel Implemenation
			virtual std::wstring GetName() const;
			virtual void SetListener(IClientChannelListener* listeners);
			virtual void SendUnicastData(const UserID& to, const byte* data, size_t length, bool isReliable);
			virtual void SendMulticastData(const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable);
			virtual void SendBroadcastData(const byte* data, size_t length, bool isReliable);
			virtual void Close();

			void OnChannelClosed();
			void OnUserJoined(const UserID& userID);
			void OnUserLeft(const UserID& userID);
			void OnDataReceived(const UserID& from, const byte* data, size_t length, bool wasReliable);

		private:
			ChannelID mID;
			std::wstring mName;
			ClientConnectionManager* mManager;
			IClientChannelListener* mListener;
		};
	}
}

#endif
