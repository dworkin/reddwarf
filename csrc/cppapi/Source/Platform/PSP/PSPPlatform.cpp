#include "stable.h"
#include <kernel.h>
#include <stdio.h>
#include <wchar.h>
#include <libccc.h>
#include <libhttp.h>

#include "Platform/Platform.h"

#include "LibHTTPStream.h"

namespace Darkstar
{
	namespace Platform
	{
		CLIENTAPI int GetSystemTimeMS()
		{
			return sceKernelGetSystemTimeLow();
		}

		CLIENTAPI void Sleep(int timeMS)
		{
			sceKernelDelayThread(timeMS * 1000);
		}

		CLIENTAPI void Log(const char* log)
		{
			printf(log);
		}

		CLIENTAPI void Log(const wchar_t* log)
		{
			wprintf(log);
		}

		CLIENTAPI ISocketManager* CreateSocketManager()
		{
			return NULL;
		}

		CLIENTAPI IStream* OpenStream(const wchar_t* name)
		{
			std::string url = WStringToUTF8(name);

			int templateId = sceHttpCreateTemplate("Darkstar/1.0 (PSP)", SCE_HTTP_VERSION_1_1, SCE_HTTP_ENABLE /* use system proxy settings */);
			if (templateId >= 0)
			{
				int connectionId = sceHttpCreateConnectionWithURL(templateId, url.c_str(), SCE_HTTP_DISABLE /* no keepalive */);
				if (connectionId >= 0)
				{
					int requestId = sceHttpCreateRequestWithURL(connectionId, SCE_HTTP_METHOD_GET, url.c_str(), 0);
					if (requestId >= 0)
					{
						int result = sceHttpSendRequest(requestId, NULL, 0);
						if (result >= 0)
						{
							int statusCode;
							result = sceHttpGetStatusCode(requestId, &statusCode);
							if (result >= 0 && statusCode == 200)
							{
								SceULong64 contentLength;
								result = sceHttpGetContentLength(requestId, &contentLength);
								if (result >= 0)
									return new Darkstar::Internal::LibHTTPStream(templateId, connectionId, requestId, contentLength);
							}
						}

						sceHttpDeleteRequest(requestId);
					}
					
					sceHttpDeleteConnection(connectionId);
				}

				sceHttpDeleteTemplate(templateId);
			}

			return NULL;
		}

		CLIENTAPI uint8 ByteSwap(const uint8 source)
		{
			return source;
		}

		CLIENTAPI uint16 ByteSwap(const uint16 source)
		{
			return __builtin_allegrex_wsbh(source);	// MSED - Confirm functionality
		}

		CLIENTAPI uint32 ByteSwap(const uint32 source)
		{
			return __builtin_allegrex_wsbw(source);	// MSED - Confirm functionality
		}

		namespace
		{
			int64 hton64(int64 value) 
			{ 
				byte* arr = reinterpret_cast<byte*>(&value);
				for (int x = 0; x < 4; x++) 
				{ 
					byte temp = arr[x]; 
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
			return __builtin_allegrex_wsbh(source);	// MSED - Confirm functionality
		}

		CLIENTAPI int32 ByteSwap(const int32 source)
		{
			return __builtin_allegrex_wsbw(source);	// MSED - Confirm functionality
		}

		CLIENTAPI int64 ByteSwap(const int64 source)
		{
			return hton64(source);
		}

		CLIENTAPI std::wstring UTF8ToWString(const unsigned char* utf8, int length)
		{
			sceCccUTF16 tempBuffer[1024];
			sceCccUTF8toUTF16(tempBuffer, 1024, utf8);
			return (wchar_t*)tempBuffer;
		}

		CLIENTAPI std::string WStringToUTF8(const std::wstring& wstr)
		{
			sceCccUTF8 tempBuffer[1024];
			sceCccUTF16toUTF8(tempBuffer, 1024, (sceCccUTF16*)wstr.c_str());
			return (char*)tempBuffer;
		}

		//namespace
		//{
		//	std::map<IUserManagerClient*, HMODULE> gUserManagerClientDLLs;
		//}

		IUserManagerClient* CreateUserManagerClient(const std::wstring& className)
		{
			return NULL;
		}
		//	size_t shortNameIndex = className.find_last_of(L'.');
		//	if (shortNameIndex == std::wstring::npos)
		//		return NULL;

		//	++shortNameIndex;
		//	if (shortNameIndex >= className.length())
		//		return NULL;

		//	std::wstring shortName = className.substr(shortNameIndex);
		//	HMODULE hModule = LoadLibrary(shortName.c_str());
		//	if (hModule == NULL)
		//		return NULL;

		//	typedef const wchar_t* (*GetUserManagerClientClassNameFunction)();
		//	GetUserManagerClientClassNameFunction pfnGetUserManagerClientClassName = reinterpret_cast<GetUserManagerClientClassNameFunction>(GetProcAddress(hModule, "GetUserManagerClientClassName"));
		//	if (pfnGetUserManagerClientClassName == NULL)
		//	{
		//		FreeLibrary(hModule);
		//		return NULL;
		//	}

		//	if (className != pfnGetUserManagerClientClassName())
		//	{
		//		FreeLibrary(hModule);
		//		return NULL;
		//	}

		//	typedef IUserManagerClient* (*CreateUserManagerClientFunction)();
		//	CreateUserManagerClientFunction pfnCreateUserManagerClient = reinterpret_cast<CreateUserManagerClientFunction>(GetProcAddress(hModule, "CreateUserManagerClient"));
		//	if (pfnCreateUserManagerClient == NULL)
		//	{
		//		FreeLibrary(hModule);
		//		return NULL;
		//	}

		//	IUserManagerClient* pUserManagerClient = pfnCreateUserManagerClient();
		//	if (pUserManagerClient == NULL)
		//	{
		//		FreeLibrary(hModule);
		//		return NULL;
		//	}

		//	gUserManagerClientDLLs[pUserManagerClient] = hModule;
		//	return pUserManagerClient;
		//}

		void DestroyUserManagerClient(IUserManagerClient*& pUserManagerClient)
		{
		}

		//	std::map<IUserManagerClient*, HMODULE>::iterator iter = gUserManagerClientDLLs.find(pUserManagerClient);
		//	if (iter == gUserManagerClientDLLs.end())
		//	{
		//		assert(false && "DestroyUserManagerClient received an unrecognized IUserManagerClient*");
		//		return;
		//	}

		//	HMODULE hModule = iter->second;
		//	gUserManagerClientDLLs.erase(iter);

		//	typedef void (*DestroyUserManagerClientFunction)(IUserManagerClient*);
		//	DestroyUserManagerClientFunction pfnDestroyUserManagerClient = reinterpret_cast<DestroyUserManagerClientFunction>(GetProcAddress(hModule, "DestroyUserManagerClient"));
		//	if (pfnDestroyUserManagerClient != NULL)
		//		pfnDestroyUserManagerClient(pUserManagerClient);

		//	pUserManagerClient = NULL;

		//	FreeLibrary(hModule);
		//}
	}
}