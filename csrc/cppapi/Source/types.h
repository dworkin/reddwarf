#ifndef _types_h
#define _types_h

//
// Basic Types
//
#if defined(_WIN32)
	typedef signed __int8		int8;
	typedef unsigned __int8		uint8;
	typedef signed __int16		int16;
	typedef unsigned __int16	uint16;
	typedef signed __int32		int32;
	typedef unsigned __int32	uint32;
	typedef signed __int64		int64;
	typedef unsigned __int64	uint64;
#elif defined(SN_TARGET_PSP_HW)
	#include <psptypes.h>
	typedef char8   int8;
	typedef u_char8 uint8;
	typedef u_int16 uint16; 
	typedef u_int32 uint32; 
	typedef u_int64 uint64; 
	typedef u_int32 uint;
	typedef u_int32 ulong; 
#endif

typedef uint8 byte;

static const uint8 UINT8_MAX = 255;
static const uint8 UINT8_MIN = 0;

static const byte BYTE_MAX = UINT8_MAX;
static const byte BYTE_MIN = UINT8_MIN;

#endif
