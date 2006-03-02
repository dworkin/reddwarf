#include "stable.h"

#include "ByteBufferPool.h"

#include "ByteBuffer.h"

using namespace Darkstar;
using namespace Darkstar::Internal;

ByteBuffer* ByteBufferPool::Allocate()
{
	// MSED - Do we need to implement a true pool of ByteBuffers here?
	return new ByteBuffer();
}
