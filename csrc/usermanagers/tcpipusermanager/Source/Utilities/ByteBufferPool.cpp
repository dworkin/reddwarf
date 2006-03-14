#include "stable.h"

#include "ByteBufferPool.h"

#include "ByteBuffer.h"

using namespace SGS;
using namespace SGS::Internal;

ByteBuffer* ByteBufferPool::Allocate()
{
	// MSED - Do we need to implement a true pool of ByteBuffers here?
	return new ByteBuffer();
}
