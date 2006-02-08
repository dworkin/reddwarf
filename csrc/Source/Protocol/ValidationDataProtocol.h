#ifndef _ValidationDataProtocol_h
#define _ValidationDataProtocol_h

TYPEDEF_SMART_PTR(ICallback);
TYPEDEF_SMART_PTR(ByteBuffer);

class ValidationDataProtocol
{
public:
	static void MakeRequestData(ByteBufferPtr pBuffer, const std::vector<ICallbackPtr>& currentCallbacks);
	static std::vector<ICallbackPtr> UnpackRequestData(ByteBuffer* pBuffer);
};

#endif
