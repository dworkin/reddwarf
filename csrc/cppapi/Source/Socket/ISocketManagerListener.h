#ifndef _ISocketManagerListener_h
#define _ISocketManagerListener_h

namespace Darkstar
{
	class ISocket;

	class ISocketManagerListener
	{
	public:
		virtual ~ISocketManagerListener() { }

		virtual void OnConnected(ISocket* pSocket) = 0;
		virtual void OnConnectionFailed(ISocket* pSocket) = 0;
	};
}

#endif
