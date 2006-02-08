#ifndef _stable_h
#define _stable_h

#pragma warning(disable: 4251)	// warning C4251: 'identifier' : class 'type' needs to have dll-interface to be used by clients of class 'type2'
#pragma warning(disable: 4275)	// warning C4275: non – DLL-interface classkey 'identifier' used as base for DLL-interface classkey 'identifier'


//
// Standard C++ Headers
//
#pragma warning(push)
#pragma warning(disable: 4702)	// warning C4702: unreachable code
#include <map>
#include <list>
#include <vector>
#include <string>
#include <cassert>
#pragma warning(pop)

//
// Boost Headers
//
#include <boost/shared_ptr.hpp>

//
// CRT Memory Debug Header (only under Visual Studio 6.0 and greater when building Win32)
//
#if defined(_WIN32) && _MSC_VER >= 1200
#ifdef _DEBUG
#	include <crtdbg.h>
#	define new new(_CLIENT_BLOCK, __FILE__, __LINE__)
#endif // _DEBUG
#endif

//
// Boost Helpers
//
struct null_deleter
{
	void operator()(void const *) const
	{
	}
};

//
// Other Stuff
//
#include "types.h"

//
// DLL Export Setup
//
#ifdef _USRDLL
#	ifdef CLIENTAPI_EXPORTS
#		define CLIENTAPI __declspec(dllexport)
#	else
#		define CLIENTAPI __declspec(dllimport)
#	endif
#else
#	define CLIENTAPI
#endif

// HACKS - MSED
#define IP4Address DS_IP4Address	// MSED only until we put Darkstar in a namespace

#endif
