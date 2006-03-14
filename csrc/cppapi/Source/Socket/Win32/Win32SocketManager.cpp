#include "stable.h"
#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#include <process.h>
#pragma comment(lib, "ws2_32.lib")

#include "Win32SocketManager.h"

#include "Socket/ISocketManagerListener.h"

#include "Win32Socket.h"

using namespace SGS;
using namespace SGS::Internal;

Win32SocketManager::Win32SocketManager() :
	mpListener(NULL)
{
	WSADATA wsaData;
	int error = WSAStartup (0x0202, &wsaData);
	mInitialized = error == 0 && wsaData.wVersion == 0x0202;
}

Win32SocketManager::~Win32SocketManager()
{
	for (std::list<Win32Socket*>::iterator iter = mSockets.begin(); iter != mSockets.end(); ++iter)
		delete *iter;

	WSACleanup();
}

void Win32SocketManager::SetListener(ISocketManagerListener* pListener)
{
	mpListener = pListener;
}

ISocket* Win32SocketManager::CreateSocket(SocketType socketType)
{
	if (!mInitialized)
		return NULL;

	Win32Socket* pSocket = new Win32Socket(socketType);
	if (!pSocket->IsInitialized())
	{
		delete pSocket;
		return NULL;
	}

	mSockets.push_back(pSocket);
	return pSocket;
}

void Win32SocketManager::Update()
{
	for (std::list<Win32Socket*>::iterator iter = mSockets.begin(); iter != mSockets.end(); /* erase loop */)
	{
		WSANETWORKEVENTS wsaEvents;
		if (WSAEnumNetworkEvents(**iter, **iter, &wsaEvents) == SOCKET_ERROR)
			continue;	// MSED - error handling?

		if (wsaEvents.lNetworkEvents & FD_CONNECT)
		{
			if (wsaEvents.iErrorCode[FD_CONNECT_BIT] == 0)
				mpListener->OnConnected(*iter);
			else
				mpListener->OnConnectionFailed(*iter);
		}

		if (wsaEvents.lNetworkEvents & FD_READ)
		{
			if (wsaEvents.iErrorCode[FD_READ_BIT] == 0)
				(*iter)->OnReadyForRead();
		}

		if (wsaEvents.lNetworkEvents & FD_WRITE)
		{
			if (wsaEvents.iErrorCode[FD_WRITE_BIT] == 0)
				(*iter)->OnReadyForWrite();
		}

		if (wsaEvents.lNetworkEvents & FD_CLOSE)
		{
			(*iter)->OnDisconnected();
			delete *iter;
			iter = mSockets.erase(iter);
		}
		else
			++iter;
	}
}
