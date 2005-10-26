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
import com.sun.gi.comm.discovery.DiscoveredLoginModule;
import com.sun.gi.comm.discovery.DiscoveredUserManager;



public class DiscoveryXMLHandler extends DefaultHandler {
	DiscoveredGame[] games;
	List<DiscoveredGameImpl> gameList = new ArrayList<DiscoveredGameImpl>();
	List<DiscoveredUserManagerImpl> userManagerList = new ArrayList<DiscoveredUserManagerImpl>();
	List<DiscoveredLoginModuleImpl> loginModuleList = new ArrayList<DiscoveredLoginModuleImpl>();

	public void startElement(String uri, String localName, String qName,			
			Attributes attributes) throws SAXException {
		//	System.out.println("local name ="+localName);
		//	System.out.println("qname ="+qName);
		
		if (qName.equalsIgnoreCase("DISCOVERY")) { // start of discovery document														
			gameList.clear();
		} else if (qName.equalsIgnoreCase("GAME")){ // start of game record
			String gameName = attributes.getValue("name");
			int id = Integer.parseInt(attributes.getValue("id"));
			gameList.add(new DiscoveredGameImpl(id,gameName));	
			userManagerList.clear();
		} else if (qName.equalsIgnoreCase("USERMANAGER")){
			String clientClassname = attributes.getValue("clientclass");
			userManagerList.add(new DiscoveredUserManagerImpl(clientClassname));
			loginModuleList.clear();
		} else if (qName.equalsIgnoreCase("PARAMETER")){
			String tag = attributes.getValue("tag");
			String value = attributes.getValue("value");
			userManagerList.get(userManagerList.size()-1).addParameter(tag,value);
		} else if (qName.equalsIgnoreCase("LOGINMODULE")){
			String className = attributes.getValue("moduleclass");
			loginModuleList.add(new DiscoveredLoginModuleImpl(className));
		}
	}
	
	public void endElement(String uri,
            String localName,
            String qName)
     throws SAXException {
		if (qName.equalsIgnoreCase("DISCOVERY")) { // start of discovery document														
			games = new DiscoveredGame[gameList.size()];
			gameList.toArray(games);
			gameList.clear();
		} else if (qName.equalsIgnoreCase("GAME")){ // start of game record
			DiscoveredUserManager[] mgrs = new DiscoveredUserManager[userManagerList.size()];
			userManagerList.toArray(mgrs);
			gameList.get(gameList.size()-1).setUserManagers(mgrs);
			userManagerList.clear();
		} else if (qName.equalsIgnoreCase("USERMANAGER")){
			DiscoveredLoginModule[] modules = new DiscoveredLoginModule[loginModuleList.size()];
			loginModuleList.toArray(modules);
			userManagerList.get(userManagerList.size()-1).setLoginModules(modules);
			loginModuleList.clear();
		} else if (qName.equalsIgnoreCase("PARAMETER")){
			// no action needed
		} else if (qName.equalsIgnoreCase("LOGINMODULE")){
			// no action needed
		}
	}
	
	static public void main(String[] args){
		try {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			parser.parse(new File("FakeDiscovery.xml"),new DiscoveryXMLHandler());
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
		// TODO Auto-generated method stub
		return games;
	}
	
}
