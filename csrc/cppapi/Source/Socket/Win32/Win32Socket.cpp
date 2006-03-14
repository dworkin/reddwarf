/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

#include "stable.h"
#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#pragma comment(lib, "ws2_32.lib")

#include "Win32Socket.h"

#include "Win32SocketManager.h"
#include "IP4Address.h"

#include "Socket/ISocketListener.h"
#include "Socket/ISocketManagerListener.h"

using namespace SGS;
using namespace SGS::Internal;

const size_t kMaxDatagramPacketSize = 2048;

Win32Socket::Win32Socket(SocketType socketType) :
	mInitialized(false),
	mSocketType(socketType),
	mSocket(INVALID_SOCKET),
	mhEvent(WSA_INVALID_EVENT),

	mpListener(NULL),

	mPacketHeaderReceived(0),
	mpPacket(NULL),
	mPacketReceived(0)	
{
	mSocket = socket(AF_INET, socketType == kStream ? SOCK_STREAM : SOCK_DGRAM, 0);
	if (mSocket == INVALID_SOCKET)
	{
		// MSED - Log WSAGetLastError()
		return;
	}

	unsigned long enableNonBlocking = 1;
	int result = ioctlsocket(mSocket, FIONBIO, &enableNonBlocking);
	if (result == SOCKET_ERROR)
	{
		// MSED - Log WSAGetLastError()
		return;
	}

	mhEvent = WSACreateEvent();
	if (mhEvent == WSA_INVALID_EVENT)
	{
		// MSED - Log WSAGetLastError()
		return;
	}

	result = WSAEventSelect(mSocket, mhEvent, FD_CONNECT | FD_READ | FD_WRITE | FD_CLOSE);
	if (result == SOCKET_ERROR)
	{
		// MSED - Log WSAGetLastError()
		return;
	}

	if (mSocketType == kDatagram)
		mpPacket = new byte[kMaxDatagramPacketSize];
    
	mInitialized = true;
}

Win32Socket::~Win32Socket()
{
	if (mSocketType == kDatagram)
		delete [] mpPacket;

	if (mhEvent != WSA_INVALID_EVENT)
		WSACloseEvent(mhEvent);
	
	if (mSocket != INVALID_SOCKET)
		closesocket(mSocket);

	while (!mOutgoingPackets.empty())
	{
		OutgoingPacket& packet = mOutgoingPackets.front();
		delete [] packet.Data;
		mOutgoingPackets.pop_front();
	}
}

bool Win32Socket::IsInitialized() const
{
	return mInitialized;
}

Win32Socket::operator SOCKET() const
{
	return mSocket;
}

Win32Socket::operator WSAEVENT() const
{
	return mhEvent;
}

void Win32Socket::SetListener(ISocketListener* pListener)
{
	mpListener = pListener;
}

std::pair<std::wstring, uint16> Win32Socket::GetLocalAddress() const
{
	sockaddr_in name;
	int nameSize = sizeof(name);
	getsockname(mSocket, (sockaddr*)&name, &nameSize);

	wchar_t szHostName[MAX_PATH];
	DWORD cchHostName = MAX_PATH;
	WSAAddressToStringW((sockaddr*)&name, sizeof(name), NULL, szHostName, &cchHostName);

	return std::make_pair(szHostName, 0);
}

std::pair<std::wstring, uint16> Win32Socket::GetPeerAddress() const
{
	sockaddr_in name;
	int nameSize = sizeof(name);
	getpeername(mSocket, (sockaddr*)&name, &nameSize);

	wchar_t szHostName[MAX_PATH];
	DWORD cchHostName = MAX_PATH;
	WSAAddressToStringW((sockaddr*)&name, sizeof(name), NULL, szHostName, &cchHostName);

	return std::make_pair(szHostName, 0);
}

bool Win32Socket::Bind(const std::wstring& hostName, uint16 port)
{
	IP4Address address = IP4Address::FromString(hostName, port);

	int result = bind(mSocket, (const sockaddr*)address, sizeof(address));
	if (result == SOCKET_ERROR)
	{
		if (WSAGetLastError() != WSAEWOULDBLOCK)
		{
			// MSED - Log WSAGetLastError()
			return false;
		}
	}

	return true;
}

bool Win32Socket::Connect(const std::wstring& hostName, uint16 port)
{
	if (mSocket == INVALID_SOCKET)
		return false;

	IP4Address address = IP4Address::FromString(hostName, port);
	int result = connect(mSocket, (const sockaddr*)address, sizeof(address));
	if (result == SOCKET_ERROR)
	{
		if (WSAGetLastError() != WSAEWOULDBLOCK)
		{
			// MSED - Log WSAGetLastError()
			return false;
		}
	}

	return true;
}

void Win32Socket::Disconnect()
{
	if (mSocket == INVALID_SOCKET)
		return;

	shutdown(mSocket, SD_BOTH);
}

void Win32Socket::Send(const BufferDescriptor* buffers, size_t count)
{
	if (mSocket == INVALID_SOCKET)
		return;

	// Figure out how big this packet is
	size_t dataSize = 0;
	for (size_t i = 0; i < count; ++i)
		dataSize += (int)buffers[i].Length;

	// Make a record of the outgoing packet
	OutgoingPacket packet;
	packet.AlreadySent = 0;
	packet.Length = sizeof(PacketHeader) + dataSize;
	packet.Data = new byte[packet.Length];

	// Copy the data into the packet
	((PacketHeader*)packet.Data)->Length = htonl((u_long)dataSize);
	byte* pData = packet.Data + sizeof(PacketHeader);
	for (size_t i = 0; i < count; ++i)
	{
		memcpy(pData, buffers[i].Data, buffers[i].Length);
		pData += buffers[i].Length;
	}

	mOutgoingPackets.push_back(packet);

	// Try to send it right away
	OnReadyForWrite();
}

void Win32Socket::OnDisconnected()
{
	if (mpListener)
		mpListener->OnDisconnected(this);
}

void Win32Socket::OnReadyForRead()
{
	for (;;)
	{
		if (mSocketType == kStream)
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
					if (mpListener)
						mpListener->OnPacketReceived(this, mpPacket, mPacketReceived);

					// reset to receive header
					mPacketHeaderReceived = 0;
					delete [] mpPacket;
					mpPacket = NULL;
					mPacketReceived = 0;
				}
			}
		}
		else
		{
			size_t bytesReceived = 0;
			if (recv(mpPacket, bytesReceived, kMaxDatagramPacketSize))
			{
				PacketHeader* pHeader = (PacketHeader*)mpPacket;
				size_t reportedPacketSize = ntohl(pHeader->Length);
				size_t packetSize = bytesReceived - sizeof(PacketHeader);
				if (reportedPacketSize == packetSize)
					if (mpListener)
						mpListener->OnPacketReceived(this, mpPacket + sizeof(PacketHeader), packetSize);
			}
			else
				return;
		}
	}
}

void Win32Socket::OnReadyForWrite()
{
	while (!mOutgoingPackets.empty())
	{
		OutgoingPacket& packet = mOutgoingPackets.front();
		
		packet.AlreadySent += send(packet.Data + packet.AlreadySent, packet.Length - packet.AlreadySent);
		if (packet.AlreadySent < packet.Length)
			break;

		delete [] packet.Data;
		mOutgoingPackets.pop_front();
	}
}

size_t Win32Socket::send(const byte* data, size_t length)
{
	int bytesSentTotal = 0;
	do
	{
		int bytesSent = ::send(mSocket, (const char*)(data + bytesSentTotal), (int)(length - bytesSentTotal), 0);
		if (bytesSent == SOCKET_ERROR)
		{
			if (WSAGetLastError() == WSAEWOULDBLOCK)
				return bytesSentTotal;	// normal termination
			else
				return bytesSentTotal;	// real error
		}
		else if (bytesSent == 0)
		{
			return bytesSentTotal;	// connection was closed
		}

		bytesSentTotal += bytesSent;
	} while (bytesSentTotal < (int)length);

	return bytesSentTotal;
}

bool Win32Socket::recv(byte* data, size_t& alreadyReceived, size_t desiredReceived)
{
	do
	{
		int bytesReceived = ::recv(mSocket, (char*)(data + alreadyReceived), (int)(desiredReceived - alreadyReceived), 0);
		if (bytesReceived == SOCKET_ERROR)
		{
			if (WSAGetLastError() == WSAEWOULDBLOCK)
				return false;	// normal termination
			else
				return false;	// real error
		}
		else if (bytesReceived == 0)
		{
			return false;	// connection was closed
		}

		alreadyReceived += bytesReceived;
	} while (alreadyReceived < desiredReceived && mSocketType == kStream);

	return true;
}
