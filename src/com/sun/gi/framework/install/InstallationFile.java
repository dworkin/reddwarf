package com.sun.gi.framework.install;

import java.io.*;
import java.util.StringTokenizer;

import java.util.Map;
import java.util.HashMap;
import com.sun.gi.framework.install.xml.GAMEAPP;
import java.util.List;
import java.util.Set;
import java.util.Collection;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class InstallationFile implements InstallationLoader {
  private Map idToInstallRec = new HashMap();
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
    while (inline!=null){
      if ((inline.length() >0) && (inline.charAt(0)!='#')){
        StringTokenizer tok = new StringTokenizer(inline);
        int appID = Integer.parseInt(tok.nextToken());
        String fname = tok.nextToken();
        GAMEAPP installation = GAMEAPP.unmarshal(fname);
        InstallRec rec = new InstallRec(appID, installation);
        idToInstallRec.put(new Integer(appID), rec);
      }
      inline = rdr.readLine();
    }
  }

  /**
   * listGames
   *
   * @return List
   */
  public Collection listGames() {
    return idToInstallRec.values();
  }

}
