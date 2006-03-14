#ifndef _IClientConnectionManager_h
#define _IClientConnectionManager_h

namespace SGS
{
	struct UserID;
	class ICallback;
	class IClientConnectionManagerListener;

	class CLIENTAPI IClientConnectionManager
	{
	public:
		virtual ~IClientConnectionManager() { }

		virtual void SetListener(IClientConnectionManagerListener* clientConnectionManagerListener) = 0;
	  
		virtual std::vector<std::wstring> GetUserManagerClassNames() = 0;

		virtual bool Connect(const std::wstring& userManagerClassName) = 0;
		virtual bool Connect(const std::wstring& userManagerClassName, int connectAttempts, int msBetweenAttempts) = 0;
		virtual void Disconnect() = 0;

		virtual void SendValidationResponse(const std::vector<ICallback*>& callbacks) = 0;
		virtual void SendToServer(const byte* data, size_t length, bool isReliable) = 0;

		virtual void OpenChannel(const std::wstring& channelName) = 0;
		virtual bool IsServerID(const UserID& userID) = 0;

		virtual void Update() = 0;
	};
}

#endif
