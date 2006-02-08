#ifndef _ISocket_h
#define _ISocket_h

TYPEDEF_SMART_PTR(ISocketListener);

struct BufferDescriptor
{
	const byte* Data;
	size_t Length;
};

class ISocket
{
public:
	virtual void SetListener(ISocketListenerPtr pListener) = 0;
	virtual void Disconnect() = 0;
	virtual void Send(const BufferDescriptor* buffers, size_t count) = 0;
};

#endif
