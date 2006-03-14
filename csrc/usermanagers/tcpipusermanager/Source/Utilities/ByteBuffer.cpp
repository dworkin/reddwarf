/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

#include "stable.h"

#include "ByteBuffer.h"

#include "Platform/Platform.h"

using namespace SGS;
using namespace SGS::Internal;

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

	return Platform::UTF8ToWString(data, (int)length);
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
	std::string utf8String = Platform::WStringToUTF8(data);
	for (size_t i = 0; i < utf8String.length(); ++i)
		put((byte)utf8String[i]);
}

void ByteBuffer::PutStringWithByteLength(const std::wstring& data)
{
	put((byte)data.length());
	std::string utf8String = Platform::WStringToUTF8(data);
	for (size_t i = 0; i < utf8String.length(); ++i)
		put((byte)utf8String[i]);
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
