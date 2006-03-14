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

#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <crtdbg.h>

// Core
#include "stable.h"

// Interface
#include "Utilities\Callback.h"

#include "Discovery\IDiscoverer.h"
#include "Discovery\IDiscoveredGame.h"
#include "Discovery\IDiscoveredUserManager.h"

#include "Client\IClientConnectionManager.h"
#include "Client\IClientConnectionManagerListener.h"
#include "Client\IClientChannel.h"
#include "Client\IClientChannelListener.h"

// Implementation
#include "Discovery\URLDiscoverer.h"

#include "Client\ClientConnectionManager.h"

#include "Client\DefaultUserManagerPolicy.h"

using namespace SGS;

class ConsoleListener : public IClientConnectionManagerListener, public IClientChannelListener
{
public:
	ConsoleListener(IClientConnectionManager* pCCM) : mpCCM(pCCM) { }

private:
	IClientConnectionManager* mpCCM;
	IClientChannel* mpChannel;

#pragma warning(disable: 4100)

	// IClientConnectionManagerListener
	virtual void OnValidationRequest(const std::vector<ICallback*>& callbacks)
	{
		for (size_t i = 0; i < callbacks.size(); ++i)
		{
			NameCallback* pNCB = dynamic_cast<NameCallback*>(callbacks[i]);
			if (pNCB)
			{
				pNCB->SetName(L"guest2");
			}

			PasswordCallback* pPCB = dynamic_cast<PasswordCallback*>(callbacks[i]);
			if (pPCB)
			{
				pPCB->SetPassword(L"guest2");
			}

			TextInputCallback* pTICB = dynamic_cast<TextInputCallback*>(callbacks[i]);
			if (pTICB) 
			{
			}
		}

		mpCCM->SendValidationResponse(callbacks);
	}

	virtual void OnConnected(const UserID& myID)
	{
		OutputDebugStringW((std::wstring(L"Received User ID ") + myID.ToString() + L"\n").c_str());

		mpCCM->OpenChannel(L"__DCC_Chan");
	}

	virtual void OnConnectionRefused(const std::wstring& message)
	{
		OutputDebugStringW((std::wstring(L"Connection Refused ") + message + L"\n").c_str());
	}

	virtual void OnFailOverInProgress()
	{
		OutputDebugStringW(L"OnFailOverInProgress\n");
	}

	virtual void OnReconnected()
	{
		OutputDebugStringW(L"OnReconnected\n");
	}

	virtual void OnDisconnected()
	{
		OutputDebugStringW(L"OnDisconnected\n");
	}

	virtual void OnUserJoined(const UserID& user)
	{
		OutputDebugStringW((std::wstring(L"OnUserJoined ") + user.ToString() + L"\n").c_str());
	}

	virtual void OnUserLeft(const UserID& user)
	{
		OutputDebugStringW((std::wstring(L"OnUserLeft ") + user.ToString() + L"\n").c_str());
	}

	virtual void OnJoinedChannel(IClientChannel* clientChannel)
	{
		mpChannel = clientChannel;
		OutputDebugStringW((std::wstring(L"OnJoinedChannel ") + clientChannel->GetName() + L"\n").c_str());
		clientChannel->SetListener(this);
	}

	// IClientChannelListener
	virtual void OnPlayerJoined(const UserID& playerID)
	{
		OutputDebugStringW((std::wstring(L"OnPlayerJoined ") + playerID.ToString() + L"\n").c_str());
	}

	virtual void OnPlayerLeft(const UserID& playerID)
	{
		OutputDebugStringW((std::wstring(L"OnPlayerLeft ") + playerID.ToString() + L"\n").c_str());
	}

	virtual void OnDataArrived(const UserID& fromID, const byte* data, size_t length, bool wasReliable)
	{
		OutputDebugStringW((std::wstring(L"OnDataArrived ") + fromID.ToString() + L"\n").c_str());

		mpChannel->SendUnicastData(fromID, data, length, wasReliable);
		mpCCM->SendToServer(data, length, wasReliable);
	}

	virtual void OnChannelClosed()
	{
		OutputDebugStringW((std::wstring(L"OnChannelClosed") + L"\n").c_str());
	}

};

int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int)
{
#ifdef _DEBUG
	_CrtSetDbgFlag(_CRTDBG_LEAK_CHECK_DF | _CrtSetDbgFlag(_CRTDBG_REPORT_FLAG));
#endif

	IDiscoverer* pDiscoverer(new URLDiscoverer(L"C:\\Mind Control\\Sun\\FakeDiscovery.xml"));

	IClientConnectionManager* pCCM = new ClientConnectionManager(L"ChatTest", pDiscoverer, new DefaultUserManagerPolicy());
	IClientConnectionManagerListener* pCCMListener(new ConsoleListener(pCCM));
	pCCM->SetListener(pCCMListener);
	std::vector<std::wstring> classNames = pCCM->GetUserManagerClassNames();
	for (size_t i = 0; i < classNames.size(); ++i)
	{
		if (pCCM->Connect(classNames[i]))
		{
			Sleep(100000);
			pCCM->Disconnect();
		}
	}
	pCCM->SetListener(NULL);

	//std::vector<IDiscoveredGame*> games = pDiscoverer->GetGames();
	//for (size_t i = 0; i < games.size(); ++i)
	//{
	//	MessageBox(NULL, games[i]->GetName().c_str(), L"Discovered Game", MB_OK);
	//	std::vector<IDiscoveredUserManager*> userManagers = games[i]->GetUserManagers();
	//	for (size_t j = 0; j < userManagers.size(); ++j)
	//		MessageBox(NULL, userManagers[j]->GetClientClass().c_str(), games[i]->GetName().c_str(), MB_OK);
	//}

	return 0;
}
