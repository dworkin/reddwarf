#ifndef _Win32SocketManager_h
#define _Win32SocketManager_h

#include "../ISocketManager.h"

namespace SGS
{
	namespace Internal
	{
		class Win32Socket;

		class Win32SocketManager : public ISocketManager
		{
		public:
			Win32SocketManager();
			virtual ~Win32SocketManager();

			virtual void SetListener(ISocketManagerListener* pListener);
			virtual ISocket* CreateSocket(SocketType socketType);
			virtual void Update();

		private:
			bool mInitialized;
			ISocketManagerListener* mpListener;
			std::list<Win32Socket*> mSockets;
		};
	}
}

#endif
