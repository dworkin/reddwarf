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

package com.sun.gi.comm.discovery.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;

public class DiscoveryXMLHandler extends DefaultHandler {
    DiscoveredGame[] games;
    List<DiscoveredGameImpl> gameList = new ArrayList<DiscoveredGameImpl>();
    List<DiscoveredUserManagerImpl> userManagerList =
        new ArrayList<DiscoveredUserManagerImpl>();

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException
    {
        // System.err.println("local name ="+localName);
        // System.err.println("qname ="+qName);

        if (qName.equalsIgnoreCase("DISCOVERY")) {
            // start of discovery document
            gameList.clear();
        } else if (qName.equalsIgnoreCase("GAME")) {
            // start of game record
            String gameName = attributes.getValue("name");
            int id = Integer.parseInt(attributes.getValue("id"));
            gameList.add(new DiscoveredGameImpl(id, gameName));
            userManagerList.clear();
        } else if (qName.equalsIgnoreCase("USERMANAGER")) {
            String clientClassname = attributes.getValue("clientclass");
            userManagerList.add(new DiscoveredUserManagerImpl(clientClassname));
        } else if (qName.equalsIgnoreCase("PARAMETER")) {
            String tag = attributes.getValue("tag");
            String value = attributes.getValue("value");
            userManagerList.get(userManagerList.size() - 1).addParameter(tag,
                    value);
        }
    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (qName.equalsIgnoreCase("DISCOVERY")) {
            // start of discovery document
            games = new DiscoveredGame[gameList.size()];
            gameList.toArray(games);
            gameList.clear();
        } else if (qName.equalsIgnoreCase("GAME")) {
            // start of game record
            DiscoveredUserManager[] mgrs =
                new DiscoveredUserManager[userManagerList.size()];
            userManagerList.toArray(mgrs);
            gameList.get(gameList.size() - 1).setUserManagers(mgrs);
            userManagerList.clear();
        } else if (qName.equalsIgnoreCase("USERMANAGER")) {
            // no action needed
        } else if (qName.equalsIgnoreCase("PARAMETER")) {
            // no action needed
        }
    }

    static public void main(String[] args) {
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            DiscoveryXMLHandler hdlr = new DiscoveryXMLHandler();
            parser.parse(new File("FakeDiscovery.xml"), hdlr);
            for (DiscoveredGame game : hdlr.discoveredGames()) {
                System.err.println("Game: " + game.getName() + " (" +
                        game.getId() + ")");
                for (DiscoveredUserManager mgr : game.getUserManagers()) {
                    System.err.println("    User Manager:" +
                            mgr.getClientClass());
                }
            }

        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public DiscoveredGame[] discoveredGames() {
        return games;
    }

}
