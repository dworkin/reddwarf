#pragma once

class IP4Address
{
public:
	static IP4Address FromString(const wchar_t* address, uint16 defaultPort = 0);
	static IP4Address FromString(const std::wstring& address, uint16 defaultPort = 0);

	operator const sockaddr*() const;
	operator const sockaddr_in&() const;

private:
	sockaddr_in mAddress;
};
