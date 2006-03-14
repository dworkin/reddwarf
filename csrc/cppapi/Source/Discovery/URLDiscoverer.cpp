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

#include "URLDiscoverer.h"

#include "XMLParser/XMLReader.h"

#include "Platform/Platform.h"
#include "Platform/IStream.h"

#include "DiscoveredGame.h"
#include "DiscoveredUserManager.h"

#if defined(SN_TARGET_PSP_HW)
int _wtoi(const wchar_t*)
{
	throw std::exception();
	//return 0;
}
#endif

using namespace SGS;
using namespace SGS::Internal;

URLDiscoverer::URLDiscoverer(const std::wstring& url) :
	mURL(url)
{
}

URLDiscoverer::~URLDiscoverer()
{
}

std::vector<IDiscoveredGame*> URLDiscoverer::GetGames()
{
	std::vector<IDiscoveredGame*> games;
	DiscoveredGame* pGame = NULL;
	DiscoveredUserManager* pUserManager = NULL;

	IStream* pStream = Platform::OpenStream(mURL.c_str());
	if (pStream != NULL)
	{
		XMLReader reader(pStream);
		while (!reader.IsEOF())
		{
			XMLElement element = reader.ReadElement();
			if (element.kind == kStart)
			{
				if (element.name == L"DISCOVERY") 
				{	// start of discovery document														
					games.clear();
				} 
				else if (element.name == L"GAME")
				{	// start of game record
					pGame = new DiscoveredGame(_wtoi(element.attributes[L"id"].c_str()), element.attributes[L"name"]);
				}
				else if (element.name == L"USERMANAGER")
				{
					pUserManager = new DiscoveredUserManager(element.attributes[L"clientclass"]);
				} 
				else if (element.name == L"PARAMETER")
				{
					pUserManager->AddParameter(element.attributes[L"tag"], element.attributes[L"value"]);
				}
			}
			else if (element.kind == kEnd)
			{
				if (element.name == L"DISCOVERY") 
				{
					// no action needed
				} 
				else if (element.name == L"GAME")
				{	// end of game record
					games.push_back(pGame);
					pGame = NULL;
					pUserManager = NULL;
				} 
				else if (element.name == L"USERMANAGER")
				{
					pGame->AddUserManager(pUserManager);
					pUserManager = NULL;
				}
				else if (element.name == L"PARAMETER")
				{
					// no action needed
				}
			}
		}

		delete pStream;
	}

	return games;
}
