#include "stable.h"

#include "GenericID.h"

using namespace Darkstar;

GenericID::GenericID() :
	Length(0)
{
}

GenericID::GenericID(const GenericID& other) :
	Length(other.Length)
{
	memcpy(Data, other.Data, kMaxDataSize);
}

GenericID::GenericID(const std::pair<const byte*, size_t>& data) :
	Length(static_cast<byte>(data.second))
{
	assert(data.second <= kMaxDataSize);
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
	wchar_t wsz[kMaxDataSize * 2 + 1];
	for (byte i = 0; i < Length; ++i)
	{
#if defined (_WIN32)
#if _MSC_VER >= 1400
		swprintf(&wsz[i * 2], kMaxDataSize * 2, L"%02X", Data[i]);
#else
		swprintf(&wsz[i * 2], L"%02X", Data[i]);
#endif
#elif defined(SN_TARGET_PSP_HW)
#endif
	}
	return wsz;
}
