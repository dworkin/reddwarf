package com.sun.gi.utils.ini;

import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.*;

public class INIManager {
  Map paragraphs = new HashMap();
  public INIManager(File iniFile) {
    Map currentParagraph = null;
    try {
      BufferedReader rdr = new BufferedReader(new FileReader(iniFile));
      String line = rdr.readLine();
      line = line.trim();
      if (line.charAt(0)=='[') { // new paragraph
        line = line.substring(1,line.length()-1);
        currentParagraph = new HashMap();
        paragraphs.put(line,currentParagraph);
      } else { // data line
        int equalsloc = line.indexOf('=');
        String left;
        String right = "";
        if (equalsloc == -1) { // empty parameter
          left = line;
        } else {
          left = line.substring(0, equalsloc).trim();
          right = line.substring(equalsloc+1).trim();
        }
        currentParagraph.put(left,right);
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public INIParagraph getParagraph(String paragraph){
    Map p = (Map)paragraphs.get(paragraph);
    if ( p == null) {
      return null;
    }
    return new INIParagraph(paragraph,p);
  }
}
