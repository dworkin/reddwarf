#ifndef _DarkStar_h
#define _DarkStar_h

#pragma warning(disable: 4251)	// warning C4251: 'identifier' : class 'type' needs to have dll-interface to be used by clients of class 'type2'
#pragma warning(disable: 4275)	// warning C4275: non – DLL-interface classkey 'identifier' used as base for DLL-interface classkey 'identifier'

//
// Compiler Specific settings
//
#if _MSC_VER >= 1400
#  define _CRT_SECURE_NO_DEPRECATE
#endif

//
// Standard C++ Headers
//
#pragma warning(push)
#pragma warning(disable: 4702)	// warning C4702: unreachable code
#include <map>
#include <list>
#include <deque>
#include <vector>
#include <string>
#include <cassert>
#include <algorithm>
#pragma warning(pop)

//
// CRT Memory Debug Header (only under Visual Studio 6.0 and greater when building Win32)
//
#if defined(_WIN32) && defined(_MSC_VER) && _MSC_VER >= 1200
#	ifdef _DEBUG
#		include <crtdbg.h>
#		define new new(_CLIENT_BLOCK, __FILE__, __LINE__)
#	endif // _DEBUG
#endif

//
// Other Stuff
//
#include "types.h"

//
// DLL Export Setup
//
#ifdef CLIENTAPI_EXPORTS
#	if defined(_WIN32)
#		define CLIENTAPI __declspec(dllexport)
#	elif defined(SN_TARGET_PSP_HW) || defined (SN_TARGET_PSP_PRX)
#		define CLIENTAPI
#	endif
#else
#	if defined(_WIN32)
#		define CLIENTAPI __declspec(dllimport)
#	elif defined(SN_TARGET_PSP_HW) || defined (SN_TARGET_PSP_PRX)
#		define CLIENTAPI
#	endif

#	include "Utilities\Callback.h"

#	include "Discovery\IDiscoverer.h"
#	include "Discovery\IDiscoveredGame.h"
#	include "Discovery\IDiscoveredUserManager.h"

#	include "Client\IClientConnectionManager.h"
#	include "Client\IClientConnectionManagerListener.h"
#	include "Client\IClientChannel.h"
#	include "Client\IClientChannelListener.h"
#	include "Client\IUserManagerPolicy.h"

#if defined(_WIN32)
#	define EXPORT_USERMANAGERCLIENT_MARKUP extern "C" __declspec(dllexport)
#elif defined(SN_TARGET_PSP_HW)
#	define EXPORT_USERMANAGERCLIENT_MARKUP
#endif

#define EXPORT_USERMANAGERCLIENT(klassName, klass)										\
	EXPORT_USERMANAGERCLIENT_MARKUP const wchar_t* GetUserManagerClientClassName()		\
	{																					\
		return klassName;																\
	}																					\
	EXPORT_USERMANAGERCLIENT_MARKUP IUserManagerClient* CreateUserManagerClient()		\
	{																					\
		return new klass();																\
	}																					\
	EXPORT_USERMANAGERCLIENT_MARKUP void DestroyUserManagerClient(IUserManagerClient*p)\
	{																					\
		delete p;																		\
	}

#endif


#endif
