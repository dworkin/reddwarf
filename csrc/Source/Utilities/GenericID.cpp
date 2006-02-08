#include "stable.h"

#include "GenericID.h"

GenericID::GenericID() :
	Length(0)
{
}

GenericID::GenericID(const GenericID& other) :
	Length(other.Length)
{
	memcpy(Data, other.Data, kGenericID_MaxDataSize);
}

GenericID::GenericID(const std::pair<const byte*, size_t>& data) :
	Length(static_cast<byte>(data.second))
{
	assert(data.second <= kGenericID_MaxDataSize);
	memcpy(Data, data.first, data.second);
}

bool GenericID::operator==(const GenericID& rhs) const
{
	if (Length != rhs.Length)
		return false;
	return memcmp(Data, rhs.Data, Length) == 0;
}

bool GenericID::operator< (const GenericID& rhs) const
{
	if (Length < rhs.Length)
		return true;
	else if (Length > rhs.Length)
		return false;
	else
		return memcmp(Data, rhs.Data, Length) < 0;
}

std::wstring GenericID::ToString() const
{
	wchar_t wsz[kGenericID_MaxDataSize * 2 + 1];
	for (byte i = 0; i < Length; ++i)
		swprintf(&wsz[i * 2], L"%02X", Data[i]);
	return wsz;
}
