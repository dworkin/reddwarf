#include "stable.h"
#include <libhttp.h>

#include "LibHTTPStream.h"

using namespace Darkstar;
using namespace Darkstar::Internal;

LibHTTPStream::LibHTTPStream(int templateID, int connectionID, int requestID, int contentLength) :
	mTemplateID(templateID),
	mConnectionID(connectionID),
	mRequestID(requestID),
	mContentLength(contentLength),
	mBytesRead(0)
{
}

LibHTTPStream::~LibHTTPStream()
{
	sceHttpDeleteRequest(mRequestID);
	sceHttpDeleteConnection(mConnectionID);
	sceHttpDeleteTemplate(mTemplateID);
}

size_t LibHTTPStream::GetLength()
{
	return mContentLength;
}

bool LibHTTPStream::IsEOF()
{
	return Tell() >= GetLength();
}

uint32 LibHTTPStream::GetCapabilities() const
{
	return kSC_Read;
}

size_t LibHTTPStream::Read(void* pData, size_t bytesToRead)
{
	int result = sceHttpReadData(mRequestID, pData, bytesToRead);
	if (result >= 0)
		return result;
	else
		return 0;
}

size_t LibHTTPStream::ReadOptimized(void*& /*pData*/, size_t /*bytesToRead*/)
{
	return 0;
}

size_t LibHTTPStream::Write(void* /*pData*/, size_t /*bytesToWrite*/)
{
	return 0;
}

void LibHTTPStream::Seek(SeekOrigin /*seekOrigin*/, int /*seekOffset*/)
{
}

size_t LibHTTPStream::Tell()
{
	return mBytesRead;
}
