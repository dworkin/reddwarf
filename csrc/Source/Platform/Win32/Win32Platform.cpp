#include "stable.h"
#define STRICT
#define NOMINMAX
#include <windows.h>
#pragma comment(lib, "winmm.lib")

TYPEDEF_SMART_PTR(ISocketManager);

#include "Socket/Win32/Win32SocketManager.h"

namespace Platform
{
	int GetSystemTimeMS()
	{
		return timeGetTime();
	}

	void Sleep(int timeMS)
	{
		::Sleep(timeMS);
	}

	void Log(const char* log)
	{
		OutputDebugStringA(log);
	}

	void Log(const wchar_t* log)
	{
		OutputDebugStringW(log);
	}

	ISocketManagerPtr CreateSocketManager()
	{
		return boost::shared_ptr<ISocketManager>(new Win32SocketManager());
	}

	uint8 ByteSwap(const uint8 source)
	{
		return source;
	}

	uint16 ByteSwap(const uint16 source)
	{
		return htons(source);
	}

	uint32 ByteSwap(const uint32 source)
	{
		return htonl(source);
	}

	namespace
	{
		__int64 hton64(__int64 value) 
		{ 
			BYTE* arr = reinterpret_cast<BYTE*>(&value);
			for (int x = 0; x < 4; x++) 
			{ 
				BYTE temp = arr[x]; 
				arr[x] = arr[7-x]; 
				arr[7-x] = temp; 
			} 
			return value; 
		} 
	}

	uint64 ByteSwap(const uint64 source)
	{
		return hton64(source);
	}

	int8 ByteSwap(const int8 source)
	{
		return source;
	}

	int16 ByteSwap(const int16 source)
	{
		return htons(source);
	}

	int32 ByteSwap(const int32 source)
	{
		return htonl(source);
	}

	int64 ByteSwap(const int64 source)
	{
		return hton64(source);
	}

	std::wstring ToUnicode(const std::string& ascii)
	{
		int length = MultiByteToWideChar(CP_ACP, 0, ascii.c_str(), -1, NULL, 0);
		if (length <= 0)
			return L"";
		else
		{
			std::wstring unicode;
			wchar_t* pwsz = new wchar_t[length + 1];
			if (MultiByteToWideChar(CP_ACP, 0, ascii.c_str(), -1, pwsz, length) > 0)
				unicode = pwsz;
			delete [] pwsz;
			return unicode;
		}
	}
	std::string ToASCII(const std::wstring& unicode)
	{
		int length = WideCharToMultiByte(CP_ACP, 0, unicode.c_str(), -1, NULL, 0, NULL, NULL);
		if (length <= 0)
			return "";
		else
		{
			std::string ascii;
			char* psz = new char[length + 1];
			if (WideCharToMultiByte(CP_ACP, 0, unicode.c_str(), -1, psz, length, NULL, NULL) > 0)
				ascii = psz;
			delete [] psz;
			return ascii;
		}
	}
}
