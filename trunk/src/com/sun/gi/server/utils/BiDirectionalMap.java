package com.sun.gi.server.utils;

import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class BiDirectionalMap implements Map{
  Map forwardsMap;
  Map backwardsMap;
  public BiDirectionalMap(Class mapClass) {
    try {
      forwardsMap = (Map) mapClass.newInstance();
      backwardsMap = (Map)mapClass.newInstance();
    }
    catch (IllegalAccessException ex) {
      ex.printStackTrace();
    }
    catch (InstantiationException ex) {
      ex.printStackTrace();
    }
  }

  public int size() {
    return forwardsMap.size();
  }

  public boolean isEmpty() {
    return forwardsMap.isEmpty();
  }

  public boolean containsKey(Object object) {
    return forwardsMap.containsKey(object);
  }

  public boolean containsValue(Object object) {
    return backwardsMap.containsKey(object);
  }

  public Object get(Object key) {
    return forwardsMap.get(key);
  }

  public Object getKey(Object value){
    return backwardsMap.get(value);
  }

  public Object put(Object object, Object object1) {
    backwardsMap.put(object1,object);
    return forwardsMap.put(object,object1);
  }

  public Object remove(Object object) {
    Object bwKey = forwardsMap.get(object);
    backwardsMap.remove(bwKey);
    return forwardsMap.remove(object);
  }

  public void putAll(Map map) {
    for(Iterator i = map.entrySet().iterator();i.hasNext();){
      Entry e = (Entry) i.next();
      put(e.getKey(),e.getValue());
    }
  }

  public void clear() {
    forwardsMap.clear();
    backwardsMap.clear();
  }

  public Set keySet() {
    return forwardsMap.keySet();
  }

  public Collection values() {
    return forwardsMap.values();
  }

  public Set entrySet() {
    return forwardsMap.entrySet();
  }

  public boolean equals(Object object) {
    return forwardsMap.equals(object);
  }

  public int hashCode() {
    return forwardsMap.hashCode()^backwardsMap.hashCode();
  }
}