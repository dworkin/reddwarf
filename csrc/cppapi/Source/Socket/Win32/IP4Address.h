#ifndef _IP4Address_h
#define _IP4Address_h

namespace SGS
{
	namespace Internal
	{
		class IP4Address
		{
		public:
			static IP4Address FromString(const wchar_t* address, uint16 defaultPort = 0);
			static IP4Address FromString(const std::wstring& address, uint16 defaultPort = 0);
			static IP4Address FromAddress(const sockaddr_in& address);

			operator const sockaddr*() const;
			operator const sockaddr_in&() const;

		private:
			sockaddr_in mAddress;
		};
	}
}

#endif
