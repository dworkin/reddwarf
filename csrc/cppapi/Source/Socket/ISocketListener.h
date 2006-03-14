#ifndef _ISocketListener_h
#define _ISocketListener_h

namespace SGS
{
	class ISocket;

	class ISocketListener
	{
	public:
		virtual void OnPacketReceived(ISocket* pSocket, const byte* data, size_t length) = 0;
		virtual void OnDisconnected(ISocket* pSocket) = 0;
	};
}

#endif
