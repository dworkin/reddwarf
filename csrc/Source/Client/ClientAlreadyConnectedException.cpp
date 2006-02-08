#include "stable.h"

#include "ClientAlreadyConnectedException.h"

ClientAlreadyConnectedException::ClientAlreadyConnectedException(const std::string& message) :
	std::runtime_error(message)
{
}
