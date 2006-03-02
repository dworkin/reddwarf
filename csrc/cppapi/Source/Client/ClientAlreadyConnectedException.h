#ifndef _ClientAlreadyConnectedException_h
#define _ClientAlreadyConnectedException_h

namespace Darkstar
{
	class CLIENTAPI ClientAlreadyConnectedException : public std::exception
	{
	public:
		ClientAlreadyConnectedException(const std::string& message);
	};
}

#endif
