#ifndef _Win32Socket_h
#define _Win32Socket_h

#include "../ISocket.h"

struct PacketHeader
{
	int Length;
};

class Win32Socket : public ISocket
{
public:
	Win32Socket(SOCKET s);

	operator SOCKET();
	void Receive();

	virtual void SetListener(ISocketListenerPtr pListener);
	virtual void Disconnect();
	virtual void Send(const BufferDescriptor* buffers, size_t count);

private:
	bool send(const byte* data, size_t length);
	bool recv(byte* data, size_t& alreadyReceived, size_t desiredReceived);

	ISocketListenerPtr mListener;
	SOCKET mSocket;

	PacketHeader mPacketHeader;
	size_t mPacketHeaderReceived;
	byte* mpPacket;
	size_t mPacketReceived;
};

TYPEDEF_SMART_PTR(Win32Socket);

#endif
