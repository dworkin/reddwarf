#include "stable.h"

#include "BinaryPktProtocol.h"

#include "ITransportProtocolClient.h"
#include "ITransportProtocolTransmitter.h"

#include "Platform/Platform.h"

#include "Socket/ISocket.h"

#include "Utilities/ByteBuffer.h"
#include "Utilities/ByteBufferPool.h"

#include "ValidationDataProtocol.h"

using namespace SGS;
using namespace SGS::Internal;

namespace
{
	enum OPCODE 
	{	
		SEND_MULTICAST,			// client to server multicast msg
		RCV_MULTICAST,			// serv to client multicast msg
		SEND_BROADCAST,			// client to server broadcast msg
		RCV_BROADCAST, 			// serv to client broadcast msg
		SEND_UNICAST,			// client to server unicast msg
		RCV_UNICAST,			// server to client unicast msg
		SEND_SERVER_MSG,		// client to GLE
		CONNECT_REQ,			// client to server login
		RECONNECT_REQ,			//client to server fail-over login
		DISCONNECT_REQ,			//client to server logout request
		VALIDATION_REQ,			// server to client req for validation cb data
		VALIDATION_RESP,		// client to server validation cb data
		USER_ACCEPTED,			// server to client successful login
		USER_REJECTED,			//server to client failed login
		USER_JOINED,			// server to client notification of user logging in
		USER_LEFT,				//server to client notification of user logging out
		USER_DISCONNECTED,		//this user is being disconnected
		USER_JOINED_CHAN,		// server to client notification of other user joining
		USER_LEFT_CHAN,			//server to client notification of other user leaving channel
		RCV_RECONNECT_KEY,		//server to client reconnect key notification
		REQ_JOIN_CHAN, 			// client to server request to join a channel
		JOINED_CHAN,			// Server to client notification of user joining a channel
		REQ_LEAVE_CHAN,			// client to server req to leave a channel
		LEFT_CHAN,				// server to client notification of user leaving a channel
		SERVER_ID,				// used to send the bit pattern to identify a packet from the GLE
		CHAN_LOCKED				// join/leave channel failed (channel locked)
	};
};

BinaryPktProtocol::BinaryPktProtocol()
{
}

void BinaryPktProtocol::PacketReceived(const byte* data, size_t length)
{
	ByteBuffer buffer(data, length);

	OPCODE op = static_cast<OPCODE>(buffer.Get());

	//if (TRACEON){
	//	System.out.println("Recieved op: " + op);
	//	System.out.println("DataSize: " + buffer.remaining());
	//}
	switch (op)
	{
	case RCV_UNICAST:
	{
		bool isReliable = buffer.GetBool();
		ChannelID channelID(buffer.GetArray());
		UserID from(buffer.GetArray());
		UserID to(buffer.GetArray());

		std::pair<const byte*, size_t> remaining = buffer.GetRemainingAsArray();
		mClient->OnRcvUnicastMsg(isReliable, channelID, from, to, remaining.first, remaining.second);
	}	break;

	case RCV_MULTICAST:
	{
		bool isReliable = buffer.GetBool();
		ChannelID channelID(buffer.GetArray());
		UserID from(buffer.GetArray());

		byte toCount = buffer.Get();
		std::vector<UserID> to;
		to.reserve(toCount);
		for (size_t i = 0; i < toCount; ++i)
			to.push_back(UserID(buffer.GetArray()));

		std::pair<const byte*, size_t> remaining = buffer.GetRemainingAsArray();
		mClient->OnRcvMulticastMsg(isReliable, channelID, from, to, remaining.first, remaining.second);
	}	break;

	case RCV_BROADCAST:
	{
		bool isReliable = buffer.GetBool();
		ChannelID channelID(buffer.GetArray());
		UserID from(buffer.GetArray());

		std::pair<const byte*, size_t> remaining = buffer.GetRemainingAsArray();
		mClient->OnRcvBroadcastMsg(isReliable, channelID, from, remaining.first, remaining.second);
	}	break;
	
	case VALIDATION_REQ:
	{
		std::vector<ICallback*> callbacks = ValidationDataProtocol::UnpackRequestData(&buffer);
		mClient->OnRcvValidationReq(callbacks);
	}	break;

	case USER_ACCEPTED:
	{
		UserID user(buffer.GetArray());
		mClient->OnRcvUserAccepted(user);		
	}	break;

	case USER_REJECTED:
	{
		mClient->OnRcvUserRejected(buffer.GetString());
	}	break;

	case USER_JOINED:
	{
		UserID user(buffer.GetArray());
		mClient->OnRcvUserJoined(user);		
	}	break;

	case USER_LEFT:
	{
		UserID user(buffer.GetArray());
		mClient->OnRcvUserLeft(user);		
	}	break;

	case USER_JOINED_CHAN:
	{
		ChannelID channelID(buffer.GetArray());
		UserID user(buffer.GetArray());
		mClient->OnRcvUserJoinedChan(channelID, user);
	}	break;

	case USER_LEFT_CHAN:
	{
		ChannelID channelID(buffer.GetArray());
		UserID user(buffer.GetArray());
		mClient->OnRcvUserLeftChan(channelID, user);
	}	break;

	case JOINED_CHAN:
	{
		ChannelID channelID(buffer.GetArray());
		mClient->OnRcvJoinedChan(buffer.GetString(), channelID);
	}	break;

	case LEFT_CHAN:
	{
		ChannelID channelID(buffer.GetArray());
		mClient->OnRcvLeftChan(channelID);
	}	break;

	case RCV_RECONNECT_KEY:
	{
		UserID user(buffer.GetArray());
		ReconnectionKey reconnectionKey(buffer.GetArray());
		int64 ttl = buffer.GetInt64();
        mClient->OnRcvReconnectKey(user, reconnectionKey, ttl);
	}	break;

	case DISCONNECT_REQ:
	{
	}	break;

	case USER_DISCONNECTED:
	{
		UserID user(buffer.GetArray());
		mClient->OnRcvUserDisconnected(user);
	}	break;

	case SERVER_ID:
	{
		UserID server(buffer.GetArray());
		mClient->OnRcvServerID(server);
	}	break;

	case CHAN_LOCKED:
	{
		std::wstring channelName = buffer.GetString();
		UserID user(buffer.GetArray());
		mClient->OnRcvChannelLocked(channelName, user);
	}	break;

	default:
		Platform::Log("WARNING:Invalid op recieved: " /*+ op +*/ " ignored.\n");
		break;
	}
}

void BinaryPktProtocol::SendLoginRequest()
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)CONNECT_REQ);
	sendPacket(pPacket.get(), NULL, 0, true);
}

void BinaryPktProtocol::SendLogoutRequest()
{
	mTransmitter->CloseConnection();		
}


void BinaryPktProtocol::SendUnicastMsg(const ChannelID& channelID, const UserID& to, bool isReliable, const byte* data, size_t length)
{
	//if (TRACEON){
	//	System.out.println("Unicast Sending data of size: " + data.position());
	//}
	//System.out.flush();

	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)SEND_UNICAST);
	pPacket->PutBool(isReliable);
	pPacket->PutArray(channelID.Data, channelID.Length);
	pPacket->PutArray(to.Data, to.Length);
	sendPacket(pPacket.get(), data, length, isReliable);
}

void BinaryPktProtocol::SendMulticastMsg(const ChannelID& channelID, const std::vector<UserID>& to, bool isReliable, const byte* data, size_t length)
{
	//if (TRACEON){
	//	System.out.println("Multicast Sending data of size: " + data.position());
	//}

	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)SEND_MULTICAST);
	pPacket->PutBool(isReliable);
	pPacket->PutArray(channelID.Data, channelID.Length);
	pPacket->Put((byte)to.size());
	for (size_t i = 0; i < to.size(); ++i)
		pPacket->PutArray(to[i].Data, to[i].Length);
	sendPacket(pPacket.get(), data, length, isReliable);
}

void BinaryPktProtocol::SendServerMsg(bool isReliable, const byte* data, size_t length)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)SEND_SERVER_MSG);
	pPacket->PutBool(isReliable);
	sendPacket(pPacket.get(), data, length, isReliable);
}

void BinaryPktProtocol::SendBroadcastMsg(const ChannelID& channelID, bool isReliable, const byte* data, size_t length)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)SEND_BROADCAST);
	pPacket->PutBool(isReliable);
	pPacket->PutArray(channelID.Data, channelID.Length);
	sendPacket(pPacket.get(), data, length, isReliable);
}

void BinaryPktProtocol::SendReconnectRequest(const UserID& from, const ReconnectionKey& reconnectionKey)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)RECONNECT_REQ);
	pPacket->PutArray(from.Data, from.Length);
	pPacket->PutArray(reconnectionKey.Data, reconnectionKey.Length);
	sendPacket(pPacket.get(), NULL, 0, true);
}

void BinaryPktProtocol::SendValidationResponse(const std::vector<ICallback*>& callbacks)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)VALIDATION_RESP);
	ValidationDataProtocol::MakeRequestData(pPacket.get(), const_cast< std::vector<ICallback*>& >(callbacks));
	sendPacket(pPacket.get(), NULL, 0, true);
}

void BinaryPktProtocol::SendJoinChannelRequest(const std::wstring& channelName)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)REQ_JOIN_CHAN);
	pPacket->PutStringWithByteLength(channelName);
	sendPacket(pPacket.get(), NULL, 0, true);
}

void BinaryPktProtocol::SendLeaveChannelRequest(const ChannelID& channelID)
{
	std::auto_ptr<ByteBuffer> pPacket(ByteBufferPool::Allocate());
	pPacket->Put((byte)REQ_LEAVE_CHAN);
	pPacket->PutArray(channelID.Data, channelID.Length);            
	sendPacket(pPacket.get(), NULL, 0, true);
}

void BinaryPktProtocol::SetClient(ITransportProtocolClient* client)
{
	mClient = client;
}

void BinaryPktProtocol::SetTransmitter(ITransportProtocolTransmitter* transmitter)
{
	mTransmitter = transmitter;
}

void BinaryPktProtocol::sendPacket(ByteBuffer* pPacket, const byte* data, size_t length, bool isReliable)
{
	BufferDescriptor buffers[2];
	buffers[0].Data = pPacket->GetData();
	buffers[0].Length = pPacket->GetLength();
	buffers[1].Data = data;
	buffers[1].Length = length;
	mTransmitter->SendBuffers(buffers, length > 0 ? 2 : 1, isReliable);
}
