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

#include "IP4Address.h"
#include "Win32Socket.h"

Win32SocketManager::Win32SocketManager() :
	mhThread(NULL),
	mdwThreadID(0),
	mShuttingDown(false)
{
	WSADATA wsaData;
	int error = WSAStartup (0x0202, &wsaData);
	mInitialized = error == 0 && wsaData.wVersion == 0x0202;

	InitializeCriticalSection(&mSocketsCS);

	if (mInitialized)
		mhThread = reinterpret_cast<HANDLE>(_beginthreadex(NULL, 0, Thread, reinterpret_cast<void*>(this), 0, &mdwThreadID));
}

Win32SocketManager::~Win32SocketManager()
{
	mShuttingDown = true;
	if (mhThread != NULL)
	{
		WaitForSingleObject(mhThread, 1000);
		CloseHandle(mhThread);
	}

	DeleteCriticalSection(&mSocketsCS);

	WSACleanup();
}

void Win32SocketManager::SetListener(ISocketManagerListenerPtr pListener)
{
	mpListener = pListener;
}

ISocketPtr Win32SocketManager::Connect(const std::wstring& host, uint16 port)
{
	if (!mInitialized)
		return ISocketPtr();

	SOCKET s = WSASocket(AF_INET, SOCK_STREAM, 0, NULL, 0, WSA_FLAG_OVERLAPPED);
	if (s == INVALID_SOCKET)
	{
		int error = WSAGetLastError();
		error;
		return ISocketPtr();
	}

	IP4Address address = IP4Address::FromString(host, port);
	int result = connect(s, (const sockaddr*)address, sizeof(address));
	if (result == SOCKET_ERROR)
	{
		int error = WSAGetLastError();
		error;
		return ISocketPtr();
	}

	Win32SocketPtr pSocket(new Win32Socket(s));
	EnterCriticalSection(&mSocketsCS);
	mSockets.push_back(pSocket);
	LeaveCriticalSection(&mSocketsCS);
	if (mpListener)
		mpListener->OnConnected(pSocket);

	return pSocket;
}

#pragma warning(disable: 4127)	// warning C4127: conditional expression is constant

unsigned int Win32SocketManager::Thread(void* pvThis)
{
	Win32SocketManager* pThis = reinterpret_cast<Win32SocketManager*>(pvThis);

	do
	{
		EnterCriticalSection(&pThis->mSocketsCS);
		fd_set readfs;
		FD_ZERO(&readfs);
		for (std::list<Win32SocketPtr>::iterator iter = pThis->mSockets.begin(); iter != pThis->mSockets.end(); ++iter)
			FD_SET(**iter, &readfs);

		fd_set writefs;
		FD_ZERO(&writefs);

		fd_set exceptfs;
		FD_ZERO(&exceptfs);
		LeaveCriticalSection(&pThis->mSocketsCS);

		TIMEVAL timeout = { 0, 250000 };	// timeout is 0.25s
		int result = select(0, &readfs, &writefs, &exceptfs, &timeout);
		if (result == SOCKET_ERROR)
			;// MSED - Add some error handling code for recv failures
		else if (result > 0)
		{
			EnterCriticalSection(&pThis->mSocketsCS);
			for (std::list<Win32SocketPtr>::iterator iter = pThis->mSockets.begin(); iter != pThis->mSockets.end(); ++iter)
				if (FD_ISSET(**iter, &readfs))
					(*iter)->Receive();
			LeaveCriticalSection(&pThis->mSocketsCS);
		}

	} while (!pThis->mShuttingDown);
	return 0;
}
