#include "stable.h"

#include "ClientAlreadyConnectedException.h"

using namespace SGS;

ClientAlreadyConnectedException::ClientAlreadyConnectedException(const std::string& /*message*/) :
	std::exception()
{
	// MSED - message parameter being lost
}
