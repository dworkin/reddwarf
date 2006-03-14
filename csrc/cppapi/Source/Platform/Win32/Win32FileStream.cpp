#include "stable.h"
#define STRICT
#define NOMINMAX
#include <windows.h>

#include "Win32FileStream.h"

using namespace SGS;
using namespace SGS::Internal;

namespace
{
	int ToMoveMethod(SeekOrigin seekOrigin)
	{
		switch (seekOrigin)
		{
		case kSO_Begin: return FILE_BEGIN;
		case kSO_Current: return FILE_CURRENT;
		case kSO_End: return FILE_END;
		default:
			return FILE_BEGIN;
		}
	}
}

Win32FileStream::Win32FileStream(HANDLE hFile, bool canRead, bool canWrite) :
	mhFile(hFile),
	mCanRead(canRead),
	mCanWrite(canWrite)
{
}

Win32FileStream::~Win32FileStream()
{
	CloseHandle(mhFile);
	mhFile = INVALID_HANDLE_VALUE;
}

size_t Win32FileStream::GetLength()
{
	return GetFileSize(mhFile, NULL);
}

bool Win32FileStream::IsEOF()
{
	return Tell() >= GetLength();
}

uint32 Win32FileStream::GetCapabilities() const
{
	return
		(mCanRead ? kSC_Read : 0) |
		(mCanWrite ? kSC_Write : 0) |
		kSC_Seek;
}

size_t Win32FileStream::Read(void* pData, size_t bytesToRead)
{
	DWORD dwBytesRead;
	BOOL bResult = ReadFile(mhFile, pData, (DWORD)bytesToRead, &dwBytesRead, NULL);
	if (bResult)
		return dwBytesRead;
	else
		return 0;
}

size_t Win32FileStream::ReadOptimized(void*& /*pData*/, size_t /*bytesToRead*/)
{
	//ASSERT(CanReadOptimized());
	//throwEngineError("Win32FileStream does not implement ReadOptimized.");
	return 0;
}

size_t Win32FileStream::Write(void* pData, size_t bytesToWrite)
{
	DWORD dwBytesWritten;
	BOOL bResult = WriteFile(mhFile, pData, (DWORD)bytesToWrite, &dwBytesWritten, NULL);
	if (bResult)
		return dwBytesWritten;
	else
		return 0;
}

void Win32FileStream::Seek(SeekOrigin seekOrigin, int seekOffset)
{
	SetFilePointer(mhFile, seekOffset, NULL, ToMoveMethod(seekOrigin));
}

size_t Win32FileStream::Tell()
{
	return SetFilePointer(mhFile, 0, NULL, FILE_CURRENT);
}
