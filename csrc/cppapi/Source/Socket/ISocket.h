#ifndef _ISocket_h
#define _ISocket_h

namespace SGS
{
	class ISocketListener;

	struct BufferDescriptor
	{
		const byte* Data;
		size_t Length;
	};

	class ISocket
	{
	public:
		virtual ~ISocket() { }

		virtual void SetListener(ISocketListener* pListener) = 0;
		virtual std::pair<std::wstring, uint16> GetLocalAddress() const = 0;
		virtual std::pair<std::wstring, uint16> GetPeerAddress() const = 0;
		virtual bool Bind(const std::wstring& hostName, uint16 port) = 0;
		virtual bool Connect(const std::wstring& hostName, uint16 port) = 0;
		virtual void Disconnect() = 0;
		virtual void Send(const BufferDescriptor* buffers, size_t count) = 0;
	};
}

#endif
