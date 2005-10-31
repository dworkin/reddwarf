package com.sun.gi.framework.install.impl;

import java.io.*;
import java.net.URL;
import java.util.StringTokenizer;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.LoginModuleRec;
import com.sun.gi.framework.install.UserMgrRec;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class InstallationFile implements InstallationLoader {
  private Map<Integer,DeploymentRec> idToDeploymentRec = new HashMap<Integer,DeploymentRec>();
  public InstallationFile() {
  }

  /**
   * InstallationFile
   *
   * @param file File
   */
  public InstallationFile(File file) throws InstantiationException,
      FileNotFoundException, IOException {
    if (!file.isFile()){
      throw new InstantiationException("Installation file "+file.getAbsoluteFile()+
                                     " is not a valid file!");
    }
    BufferedReader rdr = new BufferedReader(new FileReader(file));
    String inline = rdr.readLine();
    URLDeploymentReader deprdr = new URLDeploymentReader();
    while (inline!=null){
      if ((inline.length() >0) && (inline.charAt(0)!='#')){
        StringTokenizer tok = new StringTokenizer(inline);
        int appID = Integer.parseInt(tok.nextToken());
        String urlname = tok.nextToken();
        DeploymentRec drec = deprdr.getDeploymentRec(new URL(urlname));
        if (drec != null){
        	drec.setID(appID);
        	idToDeploymentRec.put(appID,drec);
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

  public static void main(String[] args){
	  if (args.length==0){
		  args = new String[] {"Install.txt"};
	  }
	  try {
		InstallationLoader inst = new InstallationFile(new File(args[0]));
		for(DeploymentRec game : inst.listGames()){			
			System.out.println("Game: "+game.getName());
			for(UserMgrRec mgr : game.getUserManagers()){
				System.out.println("    User Manager:"+mgr.getServerClassName());
				for(LoginModuleRec mod : mgr.getLoginModules()){
						System.out.println("        Login Module: "+mod.getModuleClassName());
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
