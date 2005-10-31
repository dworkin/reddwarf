package com.sun.gi.framework.install.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.LoginModuleRec;
import com.sun.gi.framework.install.UserMgrRec;



public class URLDeploymentReader {
	
	
	public  URLDeploymentReader(){
		
	}
	
	public synchronized DeploymentRec getDeploymentRec(URL deploymentRecURL){
		URLConnection conn = null;
		try {
			conn = deploymentRecURL.openConnection();
			conn.connect();
			InputStream content = conn.getInputStream();
//			 parse XML here
			try {
				SAXParser parser = SAXParserFactory.newInstance()
						.newSAXParser();
				DeploymentXMLHandler hdlr = new DeploymentXMLHandler();
				parser.parse(content,hdlr);
				return hdlr.getDeploymentRec();
				
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();			
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} catch (IOException ex) {
			ex.printStackTrace();
			
		}
		return null;
				
	}
	
	/**
	 * Test Main
	 * @param args
	 */
	public static void main(String[] args){
		if (args.length==0){
			args = new String[] {"chattest_deployment.xml"};
		}
		String localPath = System.getProperty("user.dir");
		URLDeploymentReader rdr = new URLDeploymentReader();
		for(String filename : args){
			try {
				DeploymentRec game = rdr.getDeploymentRec(new File(localPath,filename).toURI().toURL());				
				System.out.println("Game: "+game.getName());
				for(UserMgrRec mgr : game.getUserManagers()){
					System.out.println("    User Manager:"+mgr.getServerClassName());
					for(LoginModuleRec mod : mgr.getLoginModules()){
							System.out.println("        Login Module: "+mod.getModuleClassName());
					}
				}
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
