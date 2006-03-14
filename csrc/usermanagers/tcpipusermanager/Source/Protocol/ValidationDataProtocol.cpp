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
