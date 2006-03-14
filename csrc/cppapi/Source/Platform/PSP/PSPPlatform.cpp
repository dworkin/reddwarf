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
#include <kernel.h>
#include <stdio.h>
#include <wchar.h>
#include <libccc.h>
#include <libhttp.h>

#include "Platform/Platform.h"

#include "LibHTTPStream.h"

namespace SGS
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

			int templateId = sceHttpCreateTemplate("SGS/1.0 (PSP)", SCE_HTTP_VERSION_1_1, SCE_HTTP_ENABLE /* use system proxy settings */);
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
									return new SGS::Internal::LibHTTPStream(templateId, connectionId, requestId, contentLength);
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