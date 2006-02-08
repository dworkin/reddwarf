#ifndef _GenericID_h
#define _GenericID_h

static const int kGenericID_MaxDataSize = 16;

struct CLIENTAPI GenericID
{
	byte Length;
	byte Data[kGenericID_MaxDataSize];

	GenericID();
	GenericID(const GenericID& other);
	GenericID(const std::pair<const byte*, size_t>& data);

	bool operator==(const GenericID& rhs) const;
	bool operator< (const GenericID& rhs) const;

	std::wstring ToString() const;
};

#endif
