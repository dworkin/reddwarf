#ifndef _Win32Socket_h
#define _Win32Socket_h

#include "../ISocket.h"
#include "../ISocketManager.h"
#include "IP4Address.h"

namespace SGS
{
	namespace Internal
	{
		class Win32Socket : public ISocket
		{
		public:
			Win32Socket(SocketType socketType);
			virtual ~Win32Socket();

			bool IsInitialized() const;
			operator SOCKET() const;
			operator WSAEVENT() const;

			virtual void SetListener(ISocketListener* pListener);
			virtual std::pair<std::wstring, uint16> GetLocalAddress() const;
			virtual std::pair<std::wstring, uint16> GetPeerAddress() const;
			virtual bool Bind(const std::wstring& hostName, uint16 port);
			virtual bool Connect(const std::wstring& hostName, uint16 port);
			virtual void Disconnect();
			virtual void Send(const BufferDescriptor* buffers, size_t count);

			void OnDisconnected();
			void OnReadyForRead();
			void OnReadyForWrite();

		private:
			bool mInitialized;
			SocketType mSocketType;
			SOCKET mSocket;
			WSAEVENT mhEvent;

			ISocketListener* mpListener;

			struct PacketHeader
			{
				int Length;
			};

			// Send Related
			struct OutgoingPacket
			{
				byte* Data;
				size_t Length;
				size_t AlreadySent;
			};
			std::deque<OutgoingPacket> mOutgoingPackets;
			size_t send(const byte* data, size_t length);

			// Receive Related
			PacketHeader mPacketHeader;
			size_t mPacketHeaderReceived;
			byte* mpPacket;
			size_t mPacketReceived;
			bool recv(byte* data, size_t& alreadyReceived, size_t desiredReceived);
		};
	}
}

#endif
