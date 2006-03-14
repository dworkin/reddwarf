#ifndef	_IUserManagerClient_h
#define	_IUserManagerClient_h

namespace SGS
{
	struct ChannelID;
	struct UserID;
	struct ReconnectionKey;

	class ICallback;
	class IDiscoveredUserManager;
	class IUserManagerClientListener;

	class CLIENTAPI IUserManagerClient
	{
	public:
		virtual ~IUserManagerClient() { }

		virtual bool Connect(IDiscoveredUserManager* userManager, IUserManagerClientListener* listener) = 0;

		virtual void Login() = 0;
		virtual void ValidationDataResponse(const std::vector<ICallback*>& callbacks) = 0;
		virtual void Logout() = 0;

		virtual void JoinChannel(const std::wstring& channelName) = 0;
		virtual void SendToServer(const	byte* data,	size_t length, bool	isReliable) = 0;
		virtual void SendUnicastMsg(const ChannelID& channelID,	const UserID& userID, const	byte* data,	size_t length, bool	isReliable) = 0;
		virtual void SendMulticastMsg(const	ChannelID& channelID, const	std::vector<UserID>& userID, const byte* data, size_t length, bool isReliable) = 0;
		virtual void SendBroadcastMsg(const	ChannelID& channelID, const	byte* data,	size_t length, bool	isReliable) = 0;

		virtual void ReconnectLogin(const UserID& userID, const	ReconnectionKey& reconnectionKey) = 0;

		virtual void LeaveChannel(const ChannelID& channelID) = 0;

		virtual void Update() = 0;
 	};
}

#endif
