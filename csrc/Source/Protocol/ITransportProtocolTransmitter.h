#ifndef _ITransportProtocolTransmitter_h
#define _ITransportProtocolTransmitter_h

struct BufferDescriptor;

class ITransportProtocolTransmitter
{
public:
	virtual void SendBuffers(const BufferDescriptor* buffers, size_t count) = 0;
	virtual void CloseConnection() = 0;
};

#endif
