#ifndef _ISocketManagerListener_h
#define _ISocketManagerListener_h

TYPEDEF_SMART_PTR(ISocket);

class ISocketManagerListener
{
public:
	virtual void OnConnected(ISocketPtr pSocket) = 0;
	virtual void OnConnectionFailed(ISocketPtr pSocket) = 0;
};

#endif
