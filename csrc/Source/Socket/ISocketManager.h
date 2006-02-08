#ifndef _ISocketManager_h
#define _ISocketManager_h

TYPEDEF_SMART_PTR(ISocket);
TYPEDEF_SMART_PTR(ISocketManagerListener);

class ISocketManager
{
public:
	virtual void SetListener(ISocketManagerListenerPtr pListener) = 0;
	virtual ISocketPtr Connect(const std::wstring& host, uint16 port) = 0;
};

#endif
