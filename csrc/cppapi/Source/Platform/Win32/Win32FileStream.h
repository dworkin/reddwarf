#pragma once

#include "../IStream.h"

namespace Darkstar
{
	namespace Internal
	{
		class Win32FileStream : public IStream
		{
		public:
			Win32FileStream(HANDLE hFile, bool canRead, bool canWrite);
			virtual ~Win32FileStream();

			virtual size_t GetLength();
			virtual bool IsEOF();

	        virtual uint32 GetCapabilities() const;

			virtual size_t Read(void* pData, size_t bytesToRead);
			virtual size_t ReadOptimized(void*& pData, size_t bytesToRead);
			virtual size_t Write(void* pData, size_t bytesToWrite);
			virtual void Seek(SeekOrigin seekOrigin, int seekOffset);
			virtual size_t Tell();
				
		private:
			HANDLE mhFile;
			bool mCanRead;
			bool mCanWrite;
		};
	}
}
