#ifndef _ClientAlreadyConnectedException_h
#define _ClientAlreadyConnectedException_h

namespace SGS
{
	class CLIENTAPI ClientAlreadyConnectedException : public std::exception
	{
	public:
		ClientAlreadyConnectedException(const std::string& message);
	};
}

#endif
