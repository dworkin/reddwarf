#pragma once

#include "../IStream.h"

namespace Darkstar
{
	namespace Internal
	{
		class LibHTTPStream : public IStream
		{
		public:
			LibHTTPStream(int templateID, int connectionID, int requestID, int contentLength);
			virtual ~LibHTTPStream();

			virtual size_t GetLength();
			virtual bool IsEOF();

			virtual uint32 GetCapabilities() const;

			virtual size_t Read(void* pData, size_t bytesToRead);
			virtual size_t ReadOptimized(void*& pData, size_t bytesToRead);

			virtual size_t Write(void* pData, size_t bytesToWrite);

			virtual void Seek(SeekOrigin seekOrigin, int seekOffset);
			virtual size_t Tell();

		private:
			int mTemplateID;
			int mConnectionID;
			int mRequestID;
			int mContentLength;
			int mBytesRead;
		};
	}
}
