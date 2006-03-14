#ifndef _ITransportProtocolClient_h
#define _ITransportProtocolClient_h

#include "Client/UserID.h"
#include "Client/ChannelID.h"
#include "Client/ReconnectionKey.h"

namespace SGS
{
	class ICallback;

	class ITransportProtocolClient
	{
	public:
		virtual ~ITransportProtocolClient() { }

		virtual void OnRcvUnicastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const UserID& to, const byte* data, size_t length) = 0;
		virtual void OnRcvMulticastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const std::vector<UserID>& to, const byte* data, size_t length) = 0;
		virtual void OnRcvBroadcastMsg(bool isReliable, const ChannelID& channelID, const UserID& from, const byte* data, size_t length) = 0;
		virtual void OnRcvValidationReq(const std::vector<ICallback*>& callbacks) = 0;
		virtual void OnRcvUserAccepted(const UserID& user) = 0;
		virtual void OnRcvUserRejected(const std::wstring& message) = 0;
		virtual void OnRcvUserJoined(const UserID& user) = 0;
		virtual void OnRcvUserLeft(const UserID& user) = 0;
		virtual void OnRcvUserJoinedChan(const ChannelID& channelID, const UserID& user) = 0;
		virtual void OnRcvUserLeftChan(const ChannelID& channelID, const UserID& user) = 0;
		virtual void OnRcvReconnectKey(const UserID& user, const ReconnectionKey& key, int64 ttl) = 0;
		virtual void OnRcvJoinedChan(const std::wstring& channelName, const ChannelID& channelID) = 0;
		virtual void OnRcvLeftChan(const ChannelID& channelID) = 0;
		virtual void OnRcvUserDisconnected(const UserID& user) = 0;
		virtual void OnRcvServerID(const UserID& user) = 0;
		virtual void OnRcvChannelLocked(const std::wstring& channelName, const UserID& userID) = 0;
	};
}

#endif
