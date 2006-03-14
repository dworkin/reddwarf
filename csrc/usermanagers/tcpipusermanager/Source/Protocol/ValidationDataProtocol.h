#ifndef _ValidationDataProtocol_h
#define _ValidationDataProtocol_h

namespace SGS
{
	class ICallback;

	namespace Internal
	{
		class ByteBuffer;

		class ValidationDataProtocol
		{
		public:
			static void MakeRequestData(ByteBuffer* pBuffer, std::vector<ICallback*>& currentCallbacks);
			static std::vector<ICallback*> UnpackRequestData(ByteBuffer* pBuffer);
		};
	}
}

#endif
