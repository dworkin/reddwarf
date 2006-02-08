#include "stable.h"

#include "ByteBufferPool.h"

#include "ByteBuffer.h"

ByteBufferPtr ByteBufferPool::Allocate()
{
	// MSED - Do we need to implement a true pool of ByteBuffers here?
	return ByteBufferPtr(new ByteBuffer());
}
