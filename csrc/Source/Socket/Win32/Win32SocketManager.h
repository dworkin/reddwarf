#ifndef _Win32SocketManager_h
#define _Win32SocketManager_h

#include "../ISocketManager.h"

TYPEDEF_SMART_PTR(Win32Socket);

class Win32SocketManager : public ISocketManager
{
public:
	Win32SocketManager();
	~Win32SocketManager();

	virtual void SetListener(ISocketManagerListenerPtr pListener);
	virtual ISocketPtr Connect(const std::wstring& host, uint16 port);

private:
	static unsigned int __stdcall Thread(void* pvThis);

	bool mInitialized;
	ISocketManagerListenerPtr mpListener;

	HANDLE mhThread;
	unsigned int mdwThreadID;

	CRITICAL_SECTION mSocketsCS;
	std::list<Win32SocketPtr> mSockets;
	volatile bool mShuttingDown;
};

#endif
