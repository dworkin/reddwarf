#include "stable.h"
#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>
#pragma comment(lib, "ws2_32.lib")

#include "IP4Address.h"

using namespace SGS;
using namespace SGS::Internal;

IP4Address IP4Address::FromString(const wchar_t* address, uint16 defaultPort)
{
	IP4Address ip4Address;

	int addressLength = sizeof(ip4Address.mAddress);
	int result = WSAStringToAddressW(const_cast<wchar_t*>(address), AF_INET, NULL, (sockaddr*)&ip4Address.mAddress, &addressLength);
	if (result == 0)
	{
		if (ip4Address.mAddress.sin_port == 0)
			ip4Address.mAddress.sin_port = htons(defaultPort);
	}
	else
		memset(&ip4Address.mAddress, 0, sizeof(sockaddr_in));

	return ip4Address;
}

IP4Address IP4Address::FromString(const std::wstring& address, uint16 defaultPort)
{
	return FromString(address.c_str(), defaultPort);
}

IP4Address IP4Address::FromAddress(const sockaddr_in& address)
{
	IP4Address ip4Address;
	ip4Address.mAddress = address;
	return ip4Address;
}

IP4Address::operator const sockaddr*() const
{
	return (const sockaddr*)&mAddress;
}

IP4Address::operator const sockaddr_in&() const
{
	return mAddress;
}
