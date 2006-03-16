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

package com.sun.gi.apps.mcs.matchmaker.server;

import static com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol.*;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.gi.apps.mcs.matchmaker.common.UnsignedByte;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;


/**
 * <p>
 * Title: ConfigParser
 * </p>
 * 
 * <p>
 * Description: Parses the Match Maker config file, which is assumed to
 * be an XML document.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class ConfigParser {

    private Document document;

    public ConfigParser(URL url) {
        if (url != null) {
            parse(url);
        } else {
            System.err.println("Bad URL in Match Making Config Parser");
        }
    }

    private void parse(URL url) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            document = db.parse(url.openStream());
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        if (document == null) {
            System.err.println("Error parsing XML document " + url.toString());
        }
    }

    public Folder getFolderRoot(SimTask task) {
        Element rootElement =
            (Element) document.getElementsByTagName("Folder").item(0);

        Folder root = createFolder(task, rootElement, "");

        return root;
    }

    private Folder createFolder(SimTask task, Element element, String parentPath) {
        Folder folder = new Folder(element.getAttribute("name"),
                element.getAttribute("description"));
        String fullPath = parentPath.equals("") ? folder.getName() : parentPath
                + "." + folder.getName();

        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node folderNode = nodeList.item(i);
            if (folderNode instanceof Element
                    && folderNode.getNodeName().equals("Folder")) {
                Folder curSubFolder = createFolder(task, (Element) folderNode,
                        fullPath);
                System.out.println("Creating Folder: " + curSubFolder.getName()
                        + " " + curSubFolder.getDescription()
                        + " as sub folder of " + folder.getName() + " "
                        + folder.getDescription());
                folder.addFolder(task.createGLO(curSubFolder));
            }
        }

        // create the lobbies
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node lobbyNode = nodeList.item(i);
            if (lobbyNode instanceof Element
                    && lobbyNode.getNodeName().equals("Lobby")) {
                folder.addLobby(createLobby(task, (Element) lobbyNode, fullPath));
            }
        }

        return folder;
    }

    private GLOReference<Lobby> createLobby(SimTask task, Element element,
            String path)
    {
        String lobbyName = element.getAttribute("name");
        String channelNamePrefix = path.equals("") ? "" : ".";
        String channelName = path + channelNamePrefix + lobbyName;
        ChannelID cid = task.openChannel(channelName);
        task.lock(cid, true); // lobby access is controled by the
                                // server
        System.out.println("Creating Lobby: " + channelName);
        String password = (element.hasAttribute("password")
                ? element.getAttribute("password") : null);
        Lobby lobby = new Lobby(lobbyName, element.getAttribute("description"),
                password, channelName, cid);

        // set the lobby attributes that may or may not be present
        lobby.setCanHostBan(getBooleanAttribute(element, "AllowHostToBootBan"));
        lobby.setCanHostBoot(getBooleanAttribute(element, "AllowHostToBoot"));
        lobby.setCanHostChangeSettings(getBooleanAttribute(element,
                "canHostChangeSettingsAfterCreate"));
        lobby.setMaxConnectionTime(getIntAttribute(element, "maxConnectionTime"));
        lobby.setMaxPlayers(getIntAttribute(element, "maxPlayersInLobby"));
        lobby.setMaxPlayersInGameRoom(getIntAttribute(element, "maxPlayersInGameRoom"));
        lobby.setMinPlayersInGameRoomStart(getIntAttribute(element, "minPlayersInGameRoomStart"));
        lobby.setMaxPlayersInGameRoomStart(getIntAttribute(element, "maxPlayersInGameRoomStart"));

        // populate the game parameters
        NodeList gameParametersNodeList =
            element.getElementsByTagName("GameParameters");
        if (gameParametersNodeList.getLength() == 1) {
            NodeList gameAttributeList =
                ((Element) gameParametersNodeList.item(0)).getElementsByTagName("GameParameter");
            for (int i = 0; i < gameAttributeList.getLength(); i++) {
                Node curNode = gameAttributeList.item(i);
                if (curNode instanceof Element && curNode.getNodeName().equals("GameParameter")) {
                    Element curGameAttribute = (Element) curNode;
                    lobby.addGameParameter(curGameAttribute.getAttribute("name"),
                    		mapAttributeType(curGameAttribute));
                }
            }
        }

        GLOReference<Lobby> lobbyRef = task.createGLO(lobby);

        GLOMap<SGSUUID, GLOReference<Lobby>> lobbyMap =
            (GLOMap<SGSUUID, GLOReference<Lobby>>) task.findGLO("LobbyMap").get(task);
        lobbyMap.put(cid, lobbyRef);

        return lobbyRef;
    }

    private Integer mapType(Element element) {
    	if (!element.hasAttribute("type")) {
    		return TYPE_STRING;
    	}
    	String type = element.getAttribute("type");
        if (type.equalsIgnoreCase("int")) {
            return TYPE_INTEGER;
        } else if (type.equalsIgnoreCase("boolean")) {
            return TYPE_BOOLEAN;
        } else if (type.equalsIgnoreCase("byte")) {
            return TYPE_BYTE;
        } else if (type.equalsIgnoreCase("uuid")) {
        	return TYPE_UUID;
        }
        return TYPE_STRING;
   
    }
    
    private Object mapAttributeType(Element element) {
    	String value = element.getAttribute("value");
    	if (!element.hasAttribute("type")) {
            return value;
        }
        String type = element.getAttribute("type");
        
        if (type.equalsIgnoreCase("int")) {
            return value.equals("")  ? 0 : Integer.valueOf(value);
        } else if (type.equalsIgnoreCase("boolean")) {
            return value.equals("") ? false: Boolean.valueOf(value);
        } else if (type.equalsIgnoreCase("byte")) {
            return new UnsignedByte(value.equals("") ? 0 : Integer.valueOf(value));
        } else if (type.equalsIgnoreCase("uuid")) {
        	return createUUID(value.equals("") ? new byte[16] : value.getBytes());
        }

        return value; // assume string
    }
    
    private SGSUUID createUUID(byte[] bytes) {
        SGSUUID id = null;
        try {
            id = new StatisticalUUID(bytes);
        } catch (InstantiationException ie) {
            // ignore
        }

        return id;
    }

    private boolean getBooleanAttribute(Element element, String name) {
        return element.hasAttribute(name)
                && element.getAttribute(name).equalsIgnoreCase("true");
    }

    private int getIntAttribute(Element element, String name) {
        int value = 0;
        if (element.hasAttribute(name)) {
            try {
                value = Integer.parseInt(element.getAttribute(name));
            } catch (NumberFormatException nfe) {
                // ignore
            }
        }

        return value;
    }

}
