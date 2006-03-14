#ifndef _ITransportProtocolTransmitter_h
#define _ITransportProtocolTransmitter_h

namespace SGS
{
	struct BufferDescriptor;

	class ITransportProtocolTransmitter
	{
	public:
		virtual ~ITransportProtocolTransmitter() { }

		virtual void SendBuffers(const BufferDescriptor* buffers, size_t count, bool isReliable) = 0;
		virtual void CloseConnection() = 0;
	};
}

#endif
