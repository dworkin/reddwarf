#include "stable.h"

#include "ValidationDataProtocol.h"

#include "Utilities/ByteBuffer.h"
#include "Utilities/Callback.h"

#include "Platform/Platform.h"

using namespace SGS;
using namespace SGS::Internal;

namespace
{
	enum CallbackType
	{
		CB_TYPE_NAME		= 1,
		CB_TYPE_PASSWORD	= 2,
		CB_TYPE_TEXT_INPUT	= 3
	};
}

class NameCallback;
class PasswordCallback;
class TextInputCallback;

void ValidationDataProtocol::MakeRequestData(ByteBuffer* pBuffer, std::vector<ICallback*>& currentCallbacks)
{
	pBuffer->PutInt32((int32)currentCallbacks.size());
	for (size_t i = 0; i < currentCallbacks.size(); ++i) 
	{
		ICallback* pCB = currentCallbacks[i];

		NameCallback* pNCB = dynamic_cast<NameCallback*>(pCB);
		if (pNCB)
		{
			pBuffer->Put((byte)CB_TYPE_NAME);
			pBuffer->PutString(pNCB->GetPrompt());
			pBuffer->PutString(pNCB->GetDefaultName());
			pBuffer->PutString(pNCB->GetName());
		}

		PasswordCallback* pPCB = dynamic_cast<PasswordCallback*>(pCB);
		if (pPCB)
		{
			pBuffer->Put((byte)CB_TYPE_PASSWORD);
			pBuffer->PutString(pPCB->GetPrompt());
			pBuffer->PutBool(pPCB->IsEchoOn());
			pBuffer->PutString(pPCB->GetPassword());
		}

		TextInputCallback* pTICB = dynamic_cast<TextInputCallback*>(pCB);
		if (pTICB) 
		{
			pBuffer->Put((byte)CB_TYPE_TEXT_INPUT);
			pBuffer->PutString(pTICB->GetPrompt());
			pBuffer->PutString(pTICB->GetDefaultText());
			pBuffer->PutString(pTICB->GetText());
		}

		delete pCB;
	}

	currentCallbacks.clear();
}

std::vector<ICallback*> ValidationDataProtocol::UnpackRequestData(ByteBuffer* pBuffer)
{
	int32 callbackCount = pBuffer->GetInt32();

	std::vector<ICallback*> callbackList;
	callbackList.reserve(callbackCount);

	for (int32 i = 0; i < callbackCount; ++i)
	{
		byte cbType = pBuffer->Get();
		switch (cbType) 
		{
			case CB_TYPE_NAME:
			{
				NameCallback* pNCB(new NameCallback());
				pNCB->SetPrompt(pBuffer->GetString());
				pNCB->SetDefaultName(pBuffer->GetString());
				pNCB->SetName(pBuffer->GetString());
				callbackList.push_back(pNCB);
			}	break;
			case CB_TYPE_PASSWORD:
			{
				PasswordCallback* pPCB(new PasswordCallback());
				pPCB->SetPrompt(pBuffer->GetString());
				pPCB->SetIsEchoOn(pBuffer->GetBool());
				pPCB->SetPassword(pBuffer->GetString());
				callbackList.push_back(pPCB);
			}	break;
			case CB_TYPE_TEXT_INPUT:
			{
				TextInputCallback* pTICB(new TextInputCallback());
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
