#ifndef _BinaryPktProtocol_h
#define _BinaryPktProtocol_h

#include "ITransportProtocol.h"

namespace Darkstar
{
	class ITransportProtocolClient;
	class ITransportProtocolTransmitter;

	namespace Internal
	{
		class ByteBuffer;

		class BinaryPktProtocol : public ITransportProtocol
		{
		public:
			BinaryPktProtocol();

			virtual void PacketReceived(const byte* data, size_t length);
			virtual void SendLoginRequest();
			virtual void SendLogoutRequest();

			virtual void SendUnicastMsg(const ChannelID& channelID, const UserID& to, bool isReliable, const byte* data, size_t length);
			virtual void SendMulticastMsg(const ChannelID& channelID, const std::vector<UserID>& to, bool isReliable, const byte* data, size_t length);
			virtual void SendServerMsg(bool isReliable, const byte* data, size_t length);
			virtual void SendBroadcastMsg(const ChannelID& channelID, bool isReliable, const byte* data, size_t length);

			virtual void SendReconnectRequest(const UserID& from, const ReconnectionKey& reconnectionKey);
			virtual void SendValidationResponse(const std::vector<ICallback*>& callbacks);
			virtual void SendJoinChannelRequest(const std::wstring& channelName);
			virtual void SendLeaveChannelRequest(const ChannelID& channelID);

			virtual void SetClient(ITransportProtocolClient* client);
			virtual void SetTransmitter(ITransportProtocolTransmitter* transmitter);

		private:
			void sendPacket(ByteBuffer* pPacket, const byte* data, size_t length, bool isReliable);
			ITransportProtocolClient* mClient;
			ITransportProtocolTransmitter* mTransmitter;
		};
	}
}

#endif
