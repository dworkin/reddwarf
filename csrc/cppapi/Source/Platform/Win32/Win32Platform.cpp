#include "stable.h"
#define STRICT
#define NOMINMAX
#include <windows.h>
#include <shlwapi.h>
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "winmm.lib")
#pragma comment(lib, "urlmon.lib")

#include "Platform/Platform.h"

#include "Socket/Win32/Win32SocketManager.h"
#include "Win32FileStream.h"

using namespace SGS;
using namespace SGS::Internal;

namespace
{
	SGS::IStream* OpenFileStream(const wchar_t* path)
	{
		HANDLE hFile = CreateFile(path, GENERIC_READ, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
		if (hFile != INVALID_HANDLE_VALUE)
			return new Win32FileStream(hFile, true, false);
		else
			return NULL;
	}
}

namespace SGS
{
	namespace Platform
	{
		CLIENTAPI int GetSystemTimeMS()
		{
			return timeGetTime();
		}

		CLIENTAPI void Sleep(int timeMS)
		{
			::Sleep(timeMS);
		}

		CLIENTAPI void Log(const char* log)
		{
			OutputDebugStringA(log);
		}

		CLIENTAPI void Log(const wchar_t* log)
		{
			OutputDebugStringW(log);
		}

		CLIENTAPI ISocketManager* CreateSocketManager()
		{
			return new Win32SocketManager();
		}

		CLIENTAPI IStream* OpenStream(const wchar_t* name)
		{
			if (UrlIsFileUrl(name))
			{
				wchar_t szPath[MAX_PATH];
				DWORD cchPath = MAX_PATH;
				if (SUCCEEDED(PathCreateFromUrl(name, szPath, &cchPath, NULL)))
					return OpenFileStream(szPath);
				else
					return NULL;
			}
			else if (PathIsURL(name))
			{
				wchar_t szFileName[MAX_PATH];
				HRESULT hr = URLDownloadToCacheFileW(NULL, name, szFileName, MAX_PATH, 0, NULL);
				if (SUCCEEDED(hr))
					return OpenFileStream(szFileName);
				else
					return NULL;
			}
			else
			{
				return OpenFileStream(name);
			}
		}

		CLIENTAPI uint8 ByteSwap(const uint8 source)
		{
			return source;
		}

		CLIENTAPI uint16 ByteSwap(const uint16 source)
		{
			return htons(source);
		}

		CLIENTAPI uint32 ByteSwap(const uint32 source)
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

		CLIENTAPI uint64 ByteSwap(const uint64 source)
		{
			return hton64(source);
		}

		CLIENTAPI int8 ByteSwap(const int8 source)
		{
			return source;
		}

		CLIENTAPI int16 ByteSwap(const int16 source)
		{
			return htons(source);
		}

		CLIENTAPI int32 ByteSwap(const int32 source)
		{
			return htonl(source);
		}

		CLIENTAPI int64 ByteSwap(const int64 source)
		{
			return hton64(source);
		}

		CLIENTAPI std::wstring UTF8ToWString(const unsigned char* utf8, int length)
		{
			length = MultiByteToWideChar(CP_UTF8, 0, (LPCSTR)utf8, length, NULL, 0);
			if (length <= 0)
				return L"";
			else
			{
				std::wstring wstr;
				wchar_t* pwsz = new wchar_t[length + 1];
				if (MultiByteToWideChar(CP_ACP, 0, (LPCSTR)utf8, length, pwsz, length) > 0)
				{
					pwsz[length] = '\0';
					wstr = pwsz;
				}
				delete [] pwsz;
				return wstr;
			}
		}

		CLIENTAPI std::string WStringToUTF8(const std::wstring& wstr)
		{
			int length = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, NULL, 0, NULL, NULL);
			if (length <= 0)
				return "";
			else
			{
				std::string utf8;
				char* psz = new char[length + 1];
				if (WideCharToMultiByte(CP_ACP, 0, wstr.c_str(), -1, psz, length, NULL, NULL) > 0)
				{
					psz[length] = '\0';
					utf8 = psz;
				}
				delete [] psz;
				return utf8;
			}
		}

		namespace
		{
			std::map<IUserManagerClient*, HMODULE> gUserManagerClientDLLs;
		}

		IUserManagerClient* CreateUserManagerClient(const std::wstring& className)
		{
			size_t shortNameIndex = className.find_last_of(L'.');
			if (shortNameIndex == std::wstring::npos)
				return NULL;

			++shortNameIndex;
			if (shortNameIndex >= className.length())
				return NULL;

			std::wstring shortName = className.substr(shortNameIndex);
			HMODULE hModule = LoadLibrary(shortName.c_str());
			if (hModule == NULL)
				return NULL;

			typedef const wchar_t* (*GetUserManagerClientClassNameFunction)();
			GetUserManagerClientClassNameFunction pfnGetUserManagerClientClassName = reinterpret_cast<GetUserManagerClientClassNameFunction>(GetProcAddress(hModule, "GetUserManagerClientClassName"));
			if (pfnGetUserManagerClientClassName == NULL)
			{
				FreeLibrary(hModule);
				return NULL;
			}

			if (className != pfnGetUserManagerClientClassName())
			{
				FreeLibrary(hModule);
				return NULL;
			}

			typedef IUserManagerClient* (*CreateUserManagerClientFunction)();
			CreateUserManagerClientFunction pfnCreateUserManagerClient = reinterpret_cast<CreateUserManagerClientFunction>(GetProcAddress(hModule, "CreateUserManagerClient"));
			if (pfnCreateUserManagerClient == NULL)
			{
				FreeLibrary(hModule);
				return NULL;
			}

			IUserManagerClient* pUserManagerClient = pfnCreateUserManagerClient();
			if (pUserManagerClient == NULL)
			{
				FreeLibrary(hModule);
				return NULL;
			}

			gUserManagerClientDLLs[pUserManagerClient] = hModule;
			return pUserManagerClient;
		}

		void DestroyUserManagerClient(IUserManagerClient*& pUserManagerClient)
		{
			if (pUserManagerClient == NULL)
				return;

			std::map<IUserManagerClient*, HMODULE>::iterator iter = gUserManagerClientDLLs.find(pUserManagerClient);
			if (iter == gUserManagerClientDLLs.end())
			{
				assert(false && "DestroyUserManagerClient received an unrecognized IUserManagerClient*");
				return;
			}

			HMODULE hModule = iter->second;
			gUserManagerClientDLLs.erase(iter);

			typedef void (*DestroyUserManagerClientFunction)(IUserManagerClient*);
			DestroyUserManagerClientFunction pfnDestroyUserManagerClient = reinterpret_cast<DestroyUserManagerClientFunction>(GetProcAddress(hModule, "DestroyUserManagerClient"));
			if (pfnDestroyUserManagerClient != NULL)
				pfnDestroyUserManagerClient(pUserManagerClient);

			pUserManagerClient = NULL;

			FreeLibrary(hModule);
		}
	}
}