package com.sun.gi.utils.ini;

import java.util.Map;

public class INIParagraph {
  String name;
  Map data;

  /**
   * This is a package private constructor.  Only the INIManager can make these.
   *
   */

  INIParagraph(String name, Map data) {
    this.name=name;
    this.data = data;
  }

  public String getData(String tag){
    return (String)data.get(tag);
  }
}
