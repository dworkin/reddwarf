#ifndef _Platform_h
#define _Platform_h

TYPEDEF_SMART_PTR(ISocketManager);

namespace Platform
{
	int GetSystemTimeMS();
	void Sleep(int timeMS);

	void Log(const char* log);
	void Log(const wchar_t* log);

	ISocketManagerPtr CreateSocketManager();

	uint8 ByteSwap(const uint8 source);
	uint16 ByteSwap(const uint16 source);
	uint32 ByteSwap(const uint32 source);
	uint64 ByteSwap(const uint64 source);
	int8 ByteSwap(const int8 source);
	int16 ByteSwap(const int16 source);
	int32 ByteSwap(const int32 source);
	int64 ByteSwap(const int64 source);

	std::wstring ToUnicode(const std::string& ascii);
	std::string ToASCII(const std::wstring& unicode);
}

#endif
