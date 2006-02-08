#include "stable.h"
#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#pragma comment(lib, "ws2_32.lib")

#include "Win32Socket.h"

#include "Socket/ISocketListener.h"

Win32Socket::Win32Socket(SOCKET s) :
	mSocket(s),
	mPacketHeaderReceived(0),
	mpPacket(NULL),
	mPacketReceived(0)
{
}

Win32Socket::operator SOCKET()
{
	return mSocket;
}

void Win32Socket::SetListener(ISocketListenerPtr pListener)
{
	mListener = pListener;
}

void Win32Socket::Disconnect()
{
	if (mSocket == INVALID_SOCKET)
		return;

	shutdown(mSocket, SD_BOTH);
	closesocket(mSocket);
	mSocket = INVALID_SOCKET;

	if (mListener)
	{
		boost::shared_ptr<ISocket> pThis(this, null_deleter());
		mListener->OnDisconnected(pThis);
	}
}

void Win32Socket::Send(const BufferDescriptor* buffers, size_t count)
{
	if (mSocket == INVALID_SOCKET)
		return;

	PacketHeader packetHeader = { 0 };
	for (size_t i = 0; i < count; ++i)
		packetHeader.Length += (int)buffers[i].Length;
	packetHeader.Length = htonl(packetHeader.Length);
	if (!send(reinterpret_cast<const byte*>(&packetHeader), sizeof(PacketHeader)))
		return;
	
	for (size_t i = 0; i < count; ++i)
		if (!send(buffers[i].Data, buffers[i].Length))
			return;
}

bool Win32Socket::send(const byte* data, size_t length)
{
	int bytesSentTotal = 0;
	do
	{
		int bytesSent = ::send(mSocket, (const char*)(data + bytesSentTotal), (int)(length - bytesSentTotal), 0);
		if (bytesSent == SOCKET_ERROR)
		{
			// MSED- Should switch to non-blocking sends and move them to the Win32SocketManager thread
			if (WSAGetLastError() == WSAEWOULDBLOCK)
				continue;
			else
			{
				if (mListener)
				{
					boost::shared_ptr<ISocket> pThis(this, null_deleter());
					mListener->OnDisconnected(pThis);
				}
				return false;
			}
		}
		bytesSentTotal += bytesSent;
	} while (bytesSentTotal < (int)length);

	return true;
}

void Win32Socket::Receive()
{
	for (;;)
	{
		if (mPacketHeaderReceived < sizeof(PacketHeader))
		{
			if (!recv(reinterpret_cast<byte*>(&mPacketHeader), mPacketHeaderReceived, sizeof(PacketHeader)))
				return;
		}
		else if (mPacketHeaderReceived == sizeof(PacketHeader))
		{
			if (mpPacket == NULL)
			{
				mPacketHeader.Length = ntohl(mPacketHeader.Length);
				mpPacket = new byte[mPacketHeader.Length];
				mPacketReceived = 0;
			}

			if (!recv(mpPacket, mPacketReceived, mPacketHeader.Length))
				return;

			if (mPacketReceived == (size_t)mPacketHeader.Length)
			{
				// deliver the packet
				if (mListener)
				{
					boost::shared_ptr<ISocket> pThis(this, null_deleter());
					mListener->OnPacketReceived(pThis, mpPacket, mPacketReceived);
				}

				// reset to receive header
				mPacketHeaderReceived = 0;
				delete [] mpPacket;
				mpPacket = NULL;
				mPacketReceived = 0;
			}
		}
	}
}

bool Win32Socket::recv(byte* data, size_t& alreadyReceived, size_t desiredReceived)
{
	do
	{
		int bytesReceived = ::recv(mSocket, (char*)(data + alreadyReceived), (int)(desiredReceived - alreadyReceived), 0);
		if (bytesReceived == SOCKET_ERROR)
		{
			if (WSAGetLastError() == WSAEWOULDBLOCK)
				return false;
			else
			{
				if (mListener)
				{
					boost::shared_ptr<ISocket> pThis(this, null_deleter());
					mListener->OnDisconnected(pThis);
				}

				return false;
			}
		}
		else if (bytesReceived == 0)
		{
			if (mListener)
			{
				boost::shared_ptr<ISocket> pThis(this, null_deleter());
				mListener->OnDisconnected(pThis);
			}

			return false;
		}
		else
			alreadyReceived += bytesReceived;
	} while (alreadyReceived < desiredReceived);

	return true;
}
