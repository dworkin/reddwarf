#ifndef _IClientConnectionManagerListener_h
#define _IClientConnectionManagerListener_h

namespace SGS
{
	struct UserID;
	class ICallback;
	class IClientChannel;

	class CLIENTAPI IClientConnectionManagerListener
	{
	public:
		virtual ~IClientConnectionManagerListener() { }

		virtual void OnValidationRequest(const std::vector<ICallback*>& callbacks) = 0;

		virtual void OnConnected(const UserID& myID) = 0;
		virtual void OnConnectionRefused(const std::wstring& message) = 0;

		virtual void OnFailOverInProgress() = 0;
		virtual void OnReconnected() = 0;
		virtual void OnDisconnected() = 0;
	  
		virtual void OnUserJoined(const UserID& myID) = 0;
		virtual void OnUserLeft(const UserID& myID) = 0;

		virtual void OnChannelLocked(const std::wstring& name, const UserID& userID) = 0;
		virtual void OnJoinedChannel(IClientChannel* clientChannel) = 0;
	};
}

#endif
