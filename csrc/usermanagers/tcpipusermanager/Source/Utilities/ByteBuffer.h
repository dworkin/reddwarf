#ifndef _ByteBuffer_h
#define _ByteBuffer_h

namespace SGS
{
	namespace Internal
	{
		class ByteBuffer
		{
		public:
			ByteBuffer();
			ByteBuffer(size_t capacity);
			ByteBuffer(const byte* data, size_t length);
			~ByteBuffer();

			byte* GetData() const;
			size_t GetLength() const;
			size_t GetCapacity() const;

			byte Get();
			int32 GetInt32();
			int64 GetInt64();
			bool GetBool();
			std::pair<const byte*, size_t> GetArray();
			std::wstring GetString();

			std::pair<const byte*, size_t> GetRemainingAsArray();

			void Put(const byte data);
			void PutInt32(const int32 data);
			void PutInt64(const int64 data);
			void PutBool(const bool data);
			void PutArray(const byte* data, size_t length);
			void PutString(const std::wstring& data);
			void PutStringWithByteLength(const std::wstring& data);

		private:
			template <class T> T get();
			bool canGet(size_t length) const;

			template <class T> void put(const T data);
			bool canPut(size_t length) const;

			static const size_t kDefaultBufferSize = 2048;

			byte* mpBuffer;
			bool mBufferOwner;	
			size_t mBufferCapacity;
			size_t mBufferLength;

			byte* mpReadHead;
			byte* mpWriteHead;
		};
	}
}

#endif
