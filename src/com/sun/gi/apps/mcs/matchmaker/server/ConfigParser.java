package com.sun.gi.apps.mcs.matchmaker.server;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 * 
 * <p>Title: ConfigParser</p>
 * 
 * <p>Description: Parses the Match Maker config file, which is assumed to be an XML
 * document.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class ConfigParser {
	
	private Document document;
	
	public ConfigParser(URL url) {
		if (url != null) {
			parse(url);
		}
		else {
			System.err.println("Bad URL in Match Making Config Parser");
		}
		
	}
	
	private void parse(URL url) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			document = db.parse(url.openStream());
		}
		catch (ParserConfigurationException pce) {
			pce.printStackTrace();
		}
		catch (SAXException se) {
			se.printStackTrace();
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		if (document == null) {
			System.err.println("Error parsing XML document " + url.toString());
		}

	}
	
	public Folder getFolderRoot(SimTask task) {
		Element rootElement = (Element) document.getElementsByTagName("Folder").item(0);
		
		Folder root = createFolder(task, rootElement, "");
		
		return root;
	}
	
	private Folder createFolder(SimTask task, Element element, String parentPath) {
		Folder folder = new Folder(element.getAttribute("name"), element.getAttribute("description"));
		String fullPath = parentPath.equals("") ? folder.getName() : parentPath + "." + folder.getName();
		
		NodeList nodeList = element.getChildNodes();
		NodeList nl = element.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node folderNode = nodeList.item(i);
			if (folderNode instanceof Element && folderNode.getNodeName().equals("Folder")) {
				Folder curSubFolder = createFolder(task, (Element) folderNode, fullPath);
				System.out.println("Creating Folder: " + curSubFolder.getName() + " " + curSubFolder.getDescription() + 
						" as sub folder of " + folder.getName() + " " + folder.getDescription());
				folder.addFolder(task.createGLO(curSubFolder));
			}
		}
		
		// create the lobbies
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node lobbyNode = nodeList.item(i);
			if (lobbyNode instanceof Element && lobbyNode.getNodeName().equals("Lobby")) {
				folder.addLobby(createLobby(task, (Element) lobbyNode, fullPath));
			}
		}
		
		return folder;
	}
	
	private GLOReference createLobby(SimTask task, Element element, String path) {
		String lobbyName = element.getAttribute("name");
		String channelNamePrefix = path.equals("") ? "" : ".";
		String channelName = path + channelNamePrefix + lobbyName;
		ChannelID cid = task.openChannel(channelName);
		task.lock(cid, true);	// lobby access is controled by the server
		System.out.println("Creating Lobby: " + channelName);
		Lobby lobby = new Lobby(lobbyName, element.getAttribute("description"), element.getAttribute("password"), channelName, cid);
		
		// set the lobby attributes that may or may not be present
		lobby.setCanHostBan(getBooleanAttribute(element, "AllowHostToBootBan"));
		lobby.setCanHostBoot(getBooleanAttribute(element, "AllowHostToBoot"));
		lobby.setCanHostChangeSettings(getBooleanAttribute(element, "canHostChangeSettingsAfterCreate"));
		lobby.setMaxConnectionTime(getIntAttribute(element, "maxConnectionTime"));
		lobby.setMaxPlayers(getIntAttribute(element, "maxPlayersInLobby"));
		
		// populate the game parameters
		NodeList gameParametersNodeList = element.getElementsByTagName("GameParameters");
		HashMap<String, Object> gameParameterMap = new HashMap<String, Object>();
		if (gameParametersNodeList.getLength() == 1) {
			NodeList gameAttributeList = ((Element) gameParametersNodeList.item(0)).getElementsByTagName("GameParameter"); 
			for (int i = 0; i < gameAttributeList.getLength(); i++) {
				Node curNode = gameAttributeList.item(i);
				if (curNode instanceof Element && curNode.getNodeName().equals("GameParameter")) {
					Element curGameAttribute = (Element) curNode;
					System.out.println("curGameAttrib " + curGameAttribute.getAttribute("name"));
					lobby.addGameParameter(curGameAttribute.getAttribute("name"), mapAttributeType(curGameAttribute));
				}
			}
		}
		
		//System.out.println("Lobby: " + channelName + " maxPlayersInLobby: " + lobby.getMaxPlayers() + " allowBoot: " + lobby.getCanHostBoot() + 
		//		" allowBan: " + lobby.getCanHostBan() + " changeSettings: " + lobby.getCanHostChangeSettings() + " maxConnect: " + 
		//		lobby.getMaxConnectionTime());

		GLOReference lobbyRef = task.createGLO(lobby);
		
		GLOMap lobbyMap = (GLOMap) task.findGLO("LobbyMap").get(task);
		System.out.println("storing lobby: " + cid);
		lobbyMap.put(cid, lobbyRef);
		
		return lobbyRef;
	}
	
	private Object mapAttributeType(Element element) {
		String value = element.getAttribute("value");
		if (!element.hasAttribute("type")) {
			return value;
		}
		String type = element.getAttribute("type");
		if (type.equalsIgnoreCase("int")) {
			return Integer.valueOf(value);
		}
		else if (type.equalsIgnoreCase("boolean")) {
			return Boolean.valueOf(value);
		}
		else if (type.equalsIgnoreCase("byte")) {
			return new UnsignedByte(Integer.valueOf(value));
		}
			
		return value;		// assume string
	}
	
	private boolean getBooleanAttribute(Element element, String name) {
		return element.hasAttribute(name) && element.getAttribute(name).equalsIgnoreCase("true");
	}
	
	private int getIntAttribute(Element element, String name) {
		int value = 0;
		if (element.hasAttribute(name)) {
			try {
				value = Integer.parseInt(element.getAttribute(name));
			}
			catch (NumberFormatException nfe) {}
		}
		
		return value;
	}	
	

}
