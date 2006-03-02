#ifndef _Platform_h
#define _Platform_h

namespace Darkstar
{
	class ISocketManager;
	struct IStream;
	class IUserManagerClient;

	namespace Platform
	{
		CLIENTAPI int GetSystemTimeMS();
		CLIENTAPI void Sleep(int timeMS);

		CLIENTAPI void Log(const char* log);
		CLIENTAPI void Log(const wchar_t* log);

		CLIENTAPI ISocketManager* CreateSocketManager();
		CLIENTAPI IStream* OpenStream(const wchar_t* name);

		CLIENTAPI uint8 ByteSwap(const uint8 source);
		CLIENTAPI uint16 ByteSwap(const uint16 source);
		CLIENTAPI uint32 ByteSwap(const uint32 source);
		CLIENTAPI uint64 ByteSwap(const uint64 source);
		CLIENTAPI int8 ByteSwap(const int8 source);
		CLIENTAPI int16 ByteSwap(const int16 source);
		CLIENTAPI int32 ByteSwap(const int32 source);
		CLIENTAPI int64 ByteSwap(const int64 source);

		CLIENTAPI std::wstring UTF8ToWString(const unsigned char* utf8, int length = -1);
		CLIENTAPI std::string WStringToUTF8(const std::wstring& wstr);

		IUserManagerClient* CreateUserManagerClient(const std::wstring& className);
		void DestroyUserManagerClient(IUserManagerClient*& userManagerClient);
	}
}

#endif
