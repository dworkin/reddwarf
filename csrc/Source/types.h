#ifndef _types_h
#define _types_h

//
// Basic Types
//
typedef signed __int8		int8;
typedef unsigned __int8		uint8;
typedef signed __int16		int16;
typedef unsigned __int16	uint16;
typedef signed __int32		int32;
typedef unsigned __int32	uint32;
typedef signed __int64		int64;
typedef unsigned __int64	uint64;

typedef uint8				byte;

static const UINT8_MAX = 255;
static const UINT8_MIN = 0;

static const BYTE_MAX = UINT8_MAX;
static const BYTE_MIN = UINT8_MIN;

//
// Smart Pointers
//
#define TYPEDEF_SMART_PTR(klass)				\
	class klass;								\
	typedef boost::shared_ptr<klass> klass##Ptr


#endif
