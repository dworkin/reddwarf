#ifndef _stable_h
#define _stable_h

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
#include <vector>
#include <string>
#include <cassert>
#include <algorithm>
#pragma warning(pop)

//
// CRT Memory Debug Header (only under Visual Studio 6.0 and greater when building Win32)
//
#if defined(_WIN32) && defined(_MSC_VER) && _MSC_VER >= 1200
#ifdef _DEBUG
#	include <crtdbg.h>
#	define new new(_CLIENT_BLOCK, __FILE__, __LINE__)
#endif // _DEBUG
#endif

//
// Other Stuff
//
#include "Darkstar.h"

#endif
