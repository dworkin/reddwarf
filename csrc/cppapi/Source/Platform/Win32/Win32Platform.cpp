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