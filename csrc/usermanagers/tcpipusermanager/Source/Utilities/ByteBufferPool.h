#ifndef _ByteBufferPool_h
#define _ByteBufferPool_h

namespace Darkstar
{
	namespace Internal
	{
		class ByteBuffer;

		class ByteBufferPool
		{
		public:
			static ByteBuffer* Allocate();
		};
	}
}

#endif
