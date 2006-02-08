#ifndef _BinaryPktProtocol_h
#define _BinaryPktProtocol_h

#include "ITransportProtocol.h"

TYPEDEF_SMART_PTR(ITransportProtocolClient);
TYPEDEF_SMART_PTR(ITransportProtocolTransmitter);
TYPEDEF_SMART_PTR(ByteBuffer);

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
	virtual void SendValidationResponse(const std::vector<ICallbackPtr>& callbacks);
	virtual void SendJoinChannelRequest(const std::wstring& channelName);
	virtual void SendLeaveChannelRequest(const ChannelID& channelID);

	virtual void SetClient(ITransportProtocolClientPtr client);
	virtual void SetTransmitter(ITransportProtocolTransmitterPtr transmitter);

private:
	void sendPacket(ByteBufferPtr pPacket, const byte* data, size_t length);
	ITransportProtocolClientPtr mClient;
	ITransportProtocolTransmitterPtr mTransmitter;
};

#endif
