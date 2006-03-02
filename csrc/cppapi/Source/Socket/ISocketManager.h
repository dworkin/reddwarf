#ifndef _ISocketManager_h
#define _ISocketManager_h

namespace Darkstar
{
	class ISocket;
	class ISocketManagerListener;

	enum SocketType
	{
		kStream,
		kDatagram
	};

	class CLIENTAPI ISocketManager
	{
	public:
		virtual ~ISocketManager() { }

		virtual void SetListener(ISocketManagerListener* pListener) = 0;
		virtual ISocket* CreateSocket(SocketType socketType) = 0;
		virtual void Update() = 0;
	};
}

#endif
