#ifndef _GenericID_h
#define _GenericID_h

namespace SGS
{
	struct CLIENTAPI GenericID
	{
		static const int kMaxDataSize = 16;

		byte Length;
		byte Data[kMaxDataSize];

		GenericID();
		GenericID(const GenericID& other);
		GenericID(const std::pair<const byte*, size_t>& data);

		bool operator==(const GenericID& rhs) const;
		bool operator< (const GenericID& rhs) const;

		std::wstring ToString() const;
	};
}

#endif
