package com.sun.gi.comm.discovery.impl;

import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.net.*;
import com.sun.gi.comm.discovery.xml.DISCOVERY;
import com.sun.gi.comm.discovery.xml.GAME;
import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.xml.USERMANAGER;
import com.sun.gi.comm.discovery.xml.PARAMETER;
import com.sun.gi.comm.discovery.xml.LOGINMODULE;
import com.sun.gi.comm.discovery.DiscoveredLoginModule;

public class URLDiscoverer
    implements Discoverer {
  URL url;
  DISCOVERY xmlfile;
  public URLDiscoverer(URL url) {
    this.url=url;

  }

  /**
   * games
   *
   * @return DiscoveredGame[]
   */
  public DiscoveredGame[] games() {
    URLConnection conn = null;
    try {
      try {
        conn = url.openConnection();
      }
      catch (IOException ex) {
        ex.printStackTrace();
        return null;
      }
      conn.connect();
      InputStream content = conn.getInputStream();
      System.out.println("Found discovery stream of size: " + content.available());
      xmlfile = DISCOVERY.unmarshal(content);
      GAME[] xmlgames = xmlfile.getGAME();
      DiscoveredGameImpl[] games = new DiscoveredGameImpl[xmlgames.length];
      for (int i = 0; i < xmlgames.length; i++) {
        games[i] =
            new DiscoveredGameImpl(Integer.parseInt(xmlgames[i].getId()),
                                   xmlgames[i].getName());
        USERMANAGER[] xmlusermanagers = xmlgames[i].getUSERMANAGER();
        DiscoveredUserManagerImpl[] userManagers =
            new DiscoveredUserManagerImpl[xmlusermanagers.length];
        for (int j = 0; j < xmlusermanagers.length; j++) {
          userManagers[j] = new DiscoveredUserManagerImpl(
              xmlusermanagers[j].getClientclass());
          PARAMETER[] xmlparams = xmlusermanagers[j].getPARAMETER();
          for (int k = 0; k < xmlparams.length; k++) {
            userManagers[j].addParameter(xmlparams[k].getTag(),
                                         xmlparams[k].getValue());
          }
          LOGINMODULE[] loginmodules = xmlusermanagers[j].getLOGINMODULE();
          DiscoveredLoginModule[] modules =
              new DiscoveredLoginModule[loginmodules.length];
          for (int k = 0; k < modules.length; k++) {
            modules[k] = new DiscoveredLoginModuleImpl(loginmodules[k].
                getClassname());
          }
          userManagers[j].setLoginModules(modules);
        }
        games[i].setUserManagers(userManagers);
      }
      return games;
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    return null;
  }

  static public void main(String[] args) {
    String discname = "http://10.5.34.12/discovery.xml";
    if (args.length == 1) {
      discname = args[0];
    }

    try {
      URLDiscoverer disco = new URLDiscoverer(new URL(discname));
      DiscoveredGame[] games = disco.games();
      System.out.println("Discovered " + games.length + " games.");
    }
    catch (MalformedURLException ex) {
      ex.printStackTrace();
    }

  }
}
