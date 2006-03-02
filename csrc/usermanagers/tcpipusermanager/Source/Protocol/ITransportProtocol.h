#ifndef _ITransportProtocol_h
#define _ITransportProtocol_h

#include "Client/UserID.h"
#include "Client/ChannelID.h"
#include "Client/ReconnectionKey.h"

namespace Darkstar
{
	class ICallback;
	class ITransportProtocolClient;
	class ITransportProtocolTransmitter;

	class ITransportProtocol
	{
	public:
		virtual void PacketReceived(const byte* data, size_t length) = 0;
		virtual void SendLoginRequest() = 0;
		virtual void SendLogoutRequest() = 0;

		virtual void SendUnicastMsg(const ChannelID& channelID, const UserID& to, bool isReliable, const byte* data, size_t length) = 0;
		virtual void SendMulticastMsg(const ChannelID& channelID, const std::vector<UserID>& to, bool isReliable, const byte* data, size_t length) = 0;
		virtual void SendServerMsg(bool isReliable, const byte* data, size_t length) = 0;
		virtual void SendBroadcastMsg(const ChannelID& channelID, bool isReliable, const byte* data, size_t length) = 0;

		virtual void SendReconnectRequest(const UserID& from, const ReconnectionKey& reconnectionKey) = 0;
		virtual void SendValidationResponse(const std::vector<ICallback*>& callbacks) = 0;
		virtual void SendJoinChannelRequest(const std::wstring& channelName) = 0;
		virtual void SendLeaveChannelRequest(const ChannelID& channelID) = 0;

		virtual void SetClient(ITransportProtocolClient* pClient) = 0;
		virtual void SetTransmitter(ITransportProtocolTransmitter* pTransmitter) = 0;
	};
}

#endif
