#include "stable.h"

#include "ByteBuffer.h"

#include "Platform/Platform.h"

namespace
{
	const size_t kDefaultBufferSize = 2048;
}

ByteBuffer::ByteBuffer() :
	mpBuffer(new byte[kDefaultBufferSize]),
	mBufferOwner(true),
	mBufferCapacity(kDefaultBufferSize),
	mBufferLength(0),
	mpReadHead(mpBuffer),
	mpWriteHead(mpBuffer)
{
}

ByteBuffer::ByteBuffer(size_t capacity) :
	mpBuffer(new byte[capacity]),
	mBufferOwner(true),
	mBufferCapacity(capacity),
	mBufferLength(0),
	mpReadHead(mpBuffer),
	mpWriteHead(mpBuffer)
{
}

ByteBuffer::ByteBuffer(const byte* data, size_t length) :
	mpBuffer(const_cast<byte*>(data)),
	mBufferOwner(false),
	mBufferCapacity(length),
	mBufferLength(length),
	mpReadHead(mpBuffer),
	mpWriteHead(mpBuffer + mBufferCapacity)
{
}

ByteBuffer::~ByteBuffer()
{
	if (mBufferOwner)
		delete [] mpBuffer;
}

byte* ByteBuffer::GetData() const
{
	return mpBuffer;
}

size_t ByteBuffer::GetLength() const
{
	return mBufferLength;
}

size_t ByteBuffer::GetCapacity() const
{
	return mBufferCapacity;
}

byte ByteBuffer::Get()
{
	return get<byte>();
}

int32 ByteBuffer::GetInt32()
{
	return get<int32>();
}

int64 ByteBuffer::GetInt64()
{
	return get<int64>();
}

bool ByteBuffer::GetBool()
{
	return get<byte>() ? 1 : 0;
}

std::pair<const byte*, size_t> ByteBuffer::GetArray()
{
	byte length = get<byte>();
	assert(canGet(length));
	byte* data = mpReadHead;
	mpReadHead += length;
	return std::make_pair(data, length);
}

std::wstring ByteBuffer::GetString()
{
	size_t length = get<int32>();
	assert(canGet(length));
	byte* data = mpReadHead;
	mpReadHead += length;

	std::string asciiString(reinterpret_cast<const char*>(data), length);
	return Platform::ToUnicode(asciiString);
}

std::pair<const byte*, size_t> ByteBuffer::GetRemainingAsArray()
{
	size_t length = mBufferLength - (size_t)(mpReadHead - mpBuffer);
	assert(canGet(length));
	byte* data = mpReadHead;
	mpReadHead += length;
	return std::make_pair(data, length);
}

template <class T> T ByteBuffer::get()
{
	assert(canGet(sizeof(T)));
	T data = Platform::ByteSwap(*reinterpret_cast<T*>(mpReadHead));
	mpReadHead += sizeof(T);
	return data;
}

bool ByteBuffer::canGet(size_t length) const
{
	return mpReadHead + length <= mpBuffer + GetLength();
}

void ByteBuffer::Put(const byte data)
{
	put(data);
}

void ByteBuffer::PutInt32(const int32 data)
{
	put(data);
}

void ByteBuffer::PutInt64(const int64 data)
{
	put(data);
}

void ByteBuffer::PutBool(const bool data)
{
	put(data ? (byte)1 : (byte)0);
}

void ByteBuffer::PutArray(const byte* data, size_t length)
{
	assert(length <= UINT8_MAX);
	put((byte)length);
	for (size_t i = 0; i < length; ++i)
		put(data[i]);
}

void ByteBuffer::PutString(const std::wstring& data)
{
	put((int32)data.length());
	std::string asciiString = Platform::ToASCII(data);
	for (size_t i = 0; i < asciiString.length(); ++i)
		put((byte)asciiString[i]);
}

void ByteBuffer::PutStringWithByteLength(const std::wstring& data)
{
	put((byte)data.length());
	std::string asciiString = Platform::ToASCII(data);
	for (size_t i = 0; i < asciiString.length(); ++i)
		put((byte)asciiString[i]);
}

template <class T> void ByteBuffer::put(const T data)
{
	assert(canPut(sizeof(T)));
	*reinterpret_cast<T*>(mpWriteHead) = Platform::ByteSwap(data);
	mpWriteHead += sizeof(T);
	mBufferLength = std::max((size_t)(mpWriteHead - mpBuffer), mBufferLength);
}

bool ByteBuffer::canPut(size_t length) const
{
	return mpWriteHead + length <= mpBuffer + GetCapacity();
}
