package com.sun.gi.framework.install.impl;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.ValidatorRec;
import com.sun.gi.framework.install.UserMgrRec;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class InstallationURL implements InstallationLoader {
	private Map<Integer, DeploymentRec> idToDeploymentRec = new HashMap<Integer, DeploymentRec>();

	public InstallationURL() {
	}

	/**
	 * InstallationFile
	 * 
	 * @param file
	 *            File
	 */
	public InstallationURL(URL url) throws InstantiationException,
			FileNotFoundException, IOException {
		URLConnection conn = url.openConnection();
		conn.connect();
		InputStream content = conn.getInputStream();
		InputStreamReader isrdr = new InputStreamReader(content);
		BufferedReader rdr = new BufferedReader(isrdr);
		String inline = rdr.readLine();
		URLDeploymentReader deprdr = new URLDeploymentReader();
		while (inline != null) {
			if ((inline.length() > 0) && (inline.charAt(0) != '#')) {
				StringTokenizer tok = new StringTokenizer(inline);
				int appID = Integer.parseInt(tok.nextToken());
				String urlname = tok.nextToken();
				URL drecURL = new URL(urlname);
				DeploymentRec drec = deprdr.getDeploymentRec(drecURL);
				if (drec != null) {
					drec.setID(appID);
					if (drec.getClasspathURL()!=null){
						String classpath = drec.getClasspathURL().trim();
						if (classpath.substring(0,5).equalsIgnoreCase("file:")&&
								(!classpath.substring(5,7).equals("//"))){
							// realtive file URL, monkey with it
							URL ctext = new URL(drecURL.getProtocol()+":"+
								drecURL.getPath());
							URL turl = new URL(ctext,classpath.substring(5));
							classpath = turl.toExternalForm();
							//System.err.println("Modified cp:"+classpath);
							((DeploymentRecImpl)drec).setClasspathURL(classpath);
						}
					}
					idToDeploymentRec.put(appID, drec);
				}
			}
			inline = rdr.readLine();
		}
	}

	/**
	 * listGames
	 * 
	 * @return List
	 */
	public Collection<DeploymentRec> listGames() {
		return idToDeploymentRec.values();
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			args = new String[] { "file:Install.txt" };
		}
		try {
			InstallationLoader inst = new InstallationURL(new URL(args[0]));
			for (DeploymentRec game : inst.listGames()) {
				System.out.println("Game: " + game.getName());
				for (UserMgrRec mgr : game.getUserManagers()) {
					System.out.println("    User Manager:"
							+ mgr.getServerClassName());
					for (ValidatorRec mod : mgr.getValidatorModules()) {
						System.out.println("        Login Module: "
								+ mod.getValidatorClassName());
					}
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
