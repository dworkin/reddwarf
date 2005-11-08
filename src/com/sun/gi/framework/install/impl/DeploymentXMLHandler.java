package com.sun.gi.framework.install.impl;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.ValidatorRec;
import com.sun.gi.framework.install.UserMgrRec;



public class DeploymentXMLHandler extends DefaultHandler {
	DeploymentRecImpl drec;
	UserMgrRecImpl umrec;
	ValidatorRecImpl vrec;
	boolean inValidator;
	
	public void startElement(String uri, String localName, String qName,			
			Attributes attributes) throws SAXException {
				
		if (qName.equalsIgnoreCase("GAMEAPP")){ // start of game record
			inValidator = false; // clean up if previous was  ill formed
			String gameName = attributes.getValue("gamename");
			drec = new DeploymentRecImpl(gameName);
		} else if (qName.equalsIgnoreCase("USERMANAGER")){
			String serverClassname = attributes.getValue("serverclass");
			umrec = new UserMgrRecImpl(serverClassname);
		} else if (qName.equalsIgnoreCase("PARAMETER")){
			String tag = attributes.getValue("tag");
			String value = attributes.getValue("value");
			if (!inValidator){
				umrec.setParameter(tag,value);
			} else { //validator param
				vrec.setParameter(tag,value);
			}
		} else if (qName.equalsIgnoreCase("VALIDATOR")){
			inValidator = true;
			String className = attributes.getValue("moduleclass");
			vrec = new ValidatorRecImpl(className);
			umrec.addValidatorModule(vrec);
		}
	}
	
	public void endElement(String uri,
            String localName,
            String qName)
     throws SAXException {
		 if (qName.equalsIgnoreCase("GAME")){ // start of game record
			 // nothing, all done
		 } else if (qName.equalsIgnoreCase("USERMANAGER")){
			drec.addUserManager(umrec);
		} else if (qName.equalsIgnoreCase("PARAMETER")){
			// no action needed
		} else if (qName.equalsIgnoreCase("VALIDATOR")){
			inValidator = false;
		}
	}
	
	public DeploymentRec getDeploymentRec(){
		return drec;
	}
	
	static public void main(String[] args){
		try {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
			DeploymentXMLHandler hdlr = new DeploymentXMLHandler();
			parser.parse(new File("chattest_deployment.xml"),hdlr);
			DeploymentRec game = hdlr.getDeploymentRec();
			System.out.println("Game: "+game.getName());
			for(UserMgrRec mgr : game.getUserManagers()){
				System.out.println("    User Manager:"+mgr.getServerClassName());
				for(ValidatorRec mod : mgr.getValidatorModules()){
						System.out.println("        Login Module: "+mod.getValidatorClassName());
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
}
