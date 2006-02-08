#ifndef _ISocketListener_h
#define _ISocketListener_h

TYPEDEF_SMART_PTR(ISocket);

class ISocketListener
{
public:
	virtual void OnPacketReceived(ISocketPtr pSocket, const byte* data, size_t length) = 0;
	virtual void OnDisconnected(ISocketPtr pSocket) = 0;
};

#endif
