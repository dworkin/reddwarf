#ifndef _ByteBufferPool_h
#define _ByteBufferPool_h

TYPEDEF_SMART_PTR(ByteBuffer);

class ByteBufferPool
{
public:
	static ByteBufferPtr Allocate();
};

#endif
