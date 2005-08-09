package com.sun.gi.logic.impl;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.*;
import java.util.StringTokenizer;
import java.net.URL;
import java.util.Map;
import java.net.*;
import java.util.HashMap;
import com.sun.gi.logic.SimFinder;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class InstallFileSimFinder implements SimFinder {
  File installFile;

  private static final boolean DEBUG = true;
  private Map appNames = new HashMap();
  public InstallFileSimFinder(File f) throws InstantiationException {
    if (!f.isFile()) {
      System.err.println("Error passed init file: "+f.getName()+" is not a file!");
      throw new InstantiationException();
    }
    installFile = f;
  }

  public Map getAllSims() {
    Map classes = new HashMap();
    try {
      FileReader fr = new FileReader(installFile);
      BufferedReader br = new BufferedReader(fr);
      String line;
      do {
        try {
          do {
            line = br.readLine();
            if (DEBUG) {
              System.out.println("Read line: "+line);
            }
          } while ((line!=null)&&
                   ((line.length()==0)||(line.charAt(0)=='#')));
        }
        catch (IOException ex1) {
          ex1.printStackTrace();
          line = null;
        }
        if (line != null) {
          StringTokenizer st = new StringTokenizer(line, ",");
          String appname = st.nextToken().trim();
          long appID = Long.parseLong(st.nextToken().trim());
          String bootclassname = st.nextToken().trim();
          String classpath = st.nextToken().trim();
          StringTokenizer patht = new StringTokenizer(classpath, ":");
          int pcount = patht.countTokens();
          URL[] pathURLs = new URL[pcount];
          for(int i=0;i<pcount;i++){
            String path = patht.nextToken().trim();
            try {
              URL url = new File(path).toURL();
              System.out.println("URL = "+url.toString());
              pathURLs[i] = url;
            }
            catch (MalformedURLException ex2) {
              ex2.printStackTrace();
            }
          }
          URLClassLoader myClassLoader = new URLClassLoader(pathURLs);
          Class bootclass = null;
          try {
            bootclass = myClassLoader.loadClass(bootclassname);
            Long lid = new Long(appID);
            classes.put(lid,bootclass);
            appNames.put(lid,appname);
          }
          catch (ClassNotFoundException ex3) {
            ex3.printStackTrace();
          }
        }
      } while (line != null);
      return classes;
    }
    catch (FileNotFoundException ex) {
      ex.printStackTrace();
      return null;
    }
  }

  /**
   * getAppName
   *
   * @param appID long
   * @return String
   */
  public String getAppName(long appID) {
    return (String)appNames.get(new Long(appID));
  }

}
