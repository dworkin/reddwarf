package com.sun.gi.utils;

import java.util.Set;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CollectionUtils {
  public CollectionUtils() {
  }

  public static Object get(Set set, Object match){
    if (match == null) {
      return null;
    }
    for(Iterator i=set.iterator();i.hasNext();){
      Object obj = i.next();
      if (obj.equals(match)){
        return obj;
      }
    }
    return null;
  }

  public static Set minus(Set set1, Set set2){
    Set result = new TreeSet();
    for(Iterator i=set1.iterator();i.hasNext();){
      Object obj = i.next();
      if (!set2.contains(obj)){
        result.add(obj);
      }
    }
    return result;
  }
}