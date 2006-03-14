#ifndef _ByteBufferPool_h
#define _ByteBufferPool_h

namespace SGS
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
