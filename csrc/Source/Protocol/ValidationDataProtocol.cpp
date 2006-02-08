#include "stable.h"

#include "ValidationDataProtocol.h"

#include "Utilities/ByteBuffer.h"
#include "Utilities/Callback.h"

#include "Platform/Platform.h"

namespace
{
	enum CallbackType
	{
		CB_TYPE_NAME		= 1,
		CB_TYPE_PASSWORD	= 2,
		CB_TYPE_TEXT_INPUT	= 3
	};
}

TYPEDEF_SMART_PTR(NameCallback);
TYPEDEF_SMART_PTR(PasswordCallback);
TYPEDEF_SMART_PTR(TextInputCallback);

void ValidationDataProtocol::MakeRequestData(ByteBufferPtr pBuffer, const std::vector<ICallbackPtr>& currentCallbacks)
{
	pBuffer->PutInt32((int32)currentCallbacks.size());
	for (size_t i = 0; i < currentCallbacks.size(); ++i) 
	{
		ICallbackPtr pCB = currentCallbacks[i];

		NameCallbackPtr pNCB = boost::dynamic_pointer_cast<NameCallback>(pCB);
		if (pNCB)
		{
			pBuffer->Put((byte)CB_TYPE_NAME);
			pBuffer->PutString(pNCB->GetPrompt());
			pBuffer->PutString(pNCB->GetDefaultName());
			pBuffer->PutString(pNCB->GetName());
		}

		PasswordCallbackPtr pPCB = boost::dynamic_pointer_cast<PasswordCallback>(pCB);
		if (pPCB)
		{
			pBuffer->Put((byte)CB_TYPE_PASSWORD);
			pBuffer->PutString(pPCB->GetPrompt());
			pBuffer->PutBool(pPCB->IsEchoOn());
			pBuffer->PutString(pPCB->GetPassword());
		}

		TextInputCallbackPtr pTICB = boost::dynamic_pointer_cast<TextInputCallback>(pCB);
		if (pTICB) 
		{
			pBuffer->Put((byte)CB_TYPE_TEXT_INPUT);
			pBuffer->PutString(pTICB->GetPrompt());
			pBuffer->PutString(pTICB->GetDefaultText());
			pBuffer->PutString(pTICB->GetText());
		}
	}
}

std::vector<ICallbackPtr> ValidationDataProtocol::UnpackRequestData(ByteBuffer* pBuffer)
{
	int32 callbackCount = pBuffer->GetInt32();

	std::vector<ICallbackPtr> callbackList;
	callbackList.reserve(callbackCount);

	for (int32 i = 0; i < callbackCount; ++i)
	{
		byte cbType = pBuffer->Get();
		switch (cbType) 
		{
			case CB_TYPE_NAME:
			{
				NameCallbackPtr pNCB(new NameCallback());
				pNCB->SetPrompt(pBuffer->GetString());
				pNCB->SetDefaultName(pBuffer->GetString());
				pNCB->SetName(pBuffer->GetString());
				callbackList.push_back(pNCB);
			}	break;
			case CB_TYPE_PASSWORD:
			{
				PasswordCallbackPtr pPCB(new PasswordCallback());
				pPCB->SetPrompt(pBuffer->GetString());
				pPCB->SetIsEchoOn(pBuffer->GetBool());
				pPCB->SetPassword(pBuffer->GetString());
				callbackList.push_back(pPCB);
			}	break;
			case CB_TYPE_TEXT_INPUT:
			{
				TextInputCallbackPtr pTICB(new TextInputCallback());
				pTICB->SetPrompt(pBuffer->GetString());
				pTICB->SetDefaultText(pBuffer->GetString());
				pTICB->SetText(pBuffer->GetString());
				callbackList.push_back(pTICB);
			}	break;
			default:
				Platform::Log("Error: Illegal login callback type: \n"/* + cbType*/);
		}

	}

	return callbackList;
}
