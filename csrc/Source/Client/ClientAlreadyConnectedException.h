#ifndef _ClientAlreadyConnectedException_h
#define _ClientAlreadyConnectedException_h

class CLIENTAPI ClientAlreadyConnectedException : public std::runtime_error
{
public:
	ClientAlreadyConnectedException(const std::string& message);
};

#endif
