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



public class DiscoveryXMLHandler extends DefaultHandler {
	List<DiscoveredGame> gamesList = new ArrayList<DiscoveredGame>();

	DiscoveredGameImpl currentGame=null;
	DiscoveredUserManagerImpl currentUserManager=null;
	DiscoveredLoginModuleImpl currentLoginModule=null;

	public void startElement(String uri, String localName, String qName,			
			Attributes attributes) throws SAXException {
		//	System.out.println("local name ="+localName);
		//	System.out.println("qname ="+qName);
		
		if (qName.equalsIgnoreCase("DISCOVERY")) { // start of discovery document														
			gamesList.clear();
		} else if (qName.equalsIgnoreCase("GAME")){ // start of game record
			String gameName = attributes.getValue(null,"name");
			int id = Integer.parseInt(attributes.getValue(null,"id"));
			currentGame  = new DiscoveredGameImpl(id,gameName);			
		} else if (qName.equalsIgnoreCase("USERMANAGER")){
			String clientClassname = attributes.getValue(null,"clientclass");
			
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
	
}
