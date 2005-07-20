package com.sun.gi.comm.discovery.impl;

import com.sun.gi.comm.discovery.DiscoveredGame;
import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.net.*;
import com.sun.gi.comm.discovery.DiscoveredLoginModule;
import com.sun.gi.comm.discovery.Discoverer;

public class URLDiscoverer
    implements Discoverer {
  URL url;
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
      // parse XML here
      return null; // replace with real return
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
