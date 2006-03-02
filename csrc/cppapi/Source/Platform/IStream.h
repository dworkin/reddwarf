#pragma once

namespace Darkstar
{
	enum SeekOrigin
	{
		kSO_Begin,
		kSO_Current,
		kSO_End
	};


    enum StreamCapabilities
    {
        kSC_Read            = 1 << 0, 
        kSC_Write           = 1 << 1,
        kSC_Seek            = 1 << 2, 
        kSC_ReadOptimized   = 1 << 3,
    }; 

	struct IStream
	{
		virtual ~IStream() { }

		virtual size_t GetLength() = 0;
		virtual bool IsEOF() = 0;

        virtual uint32 GetCapabilities() const = 0; 

        // Non-virtual helpers 
        bool CanRead() const                { return (GetCapabilities() & kSC_Read) != 0; }
        bool CanReadOptimized() const       { return (GetCapabilities() & kSC_ReadOptimized) != 0; }
        bool CanWrite() const               { return (GetCapabilities() & kSC_Write) != 0; }
        bool CanSeek() const                { return (GetCapabilities() & kSC_Seek) != 0; }

        // Returns bytes read
		virtual size_t Read(void* pData, size_t bytesToRead) = 0;
		virtual size_t ReadOptimized(void*& pData, size_t bytesToRead) = 0;

        // Returns bytes written 
		virtual size_t Write(void* pData, size_t bytesToWrite) = 0;

		virtual void Seek(SeekOrigin seekOrigin, int seekOffset) = 0;
		virtual size_t Tell() = 0;
	};
}
