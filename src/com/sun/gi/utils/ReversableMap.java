package com.sun.gi.utils;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.Iterator;

public class ReversableMap implements Map{
  Map mainMap;
  Map reverseMap;

  public ReversableMap(){
    this(new HashMap(),new HashMap());
  }
  public ReversableMap(Map mainMap, Map reverseMap){
    this.mainMap = mainMap;
    this.reverseMap = reverseMap;
  }
  /**
   * hashCode
   *
   * @return int
   */
  public int hashCode() {
    return mainMap.hashCode()^reverseMap.hashCode();
  }

  /**
   * size
   *
   * @return int
   */
  public int size() {
    return mainMap.size();
  }

  /**
   * clear
   */
  public void clear() {
    mainMap.clear();
    reverseMap.clear();
  }

  /**
   * isEmpty
   *
   * @return boolean
   */
  public boolean isEmpty() {
    return mainMap.isEmpty();
  }

  /**
   * containsKey
   *
   * @param key Object
   * @return boolean
   */
  public boolean containsKey(Object key) {
    return mainMap.containsKey(key);
  }

  /**
   * containsValue
   *
   * @param value Object
   * @return boolean
   */
  public boolean containsValue(Object value) {
    return mainMap.containsValue(value);
  }

  /**
   * equals
   *
   * @param o Object
   * @return boolean
   */
  public boolean equals(Object o) {
    return super.equals(o);
  }

  /**
   * values
   *
   * @return Collection
   */
  public Collection values() {
    return mainMap.values();
  }

  /**
   * putAll
   *
   * @param t Map
   */
  public void putAll(Map t) {
    for(Iterator i = t.entrySet().iterator();i.hasNext();){
      Entry entry = (Entry)i.next();
      put(entry.getKey(),entry.getValue());
    }
  }

  /**
   * entrySet
   *
   * @return Set
   */
  public Set entrySet() {
    return mainMap.entrySet();
  }

  /**
   * keySet
   *
   * @return Set
   */
  public Set keySet() {
    return mainMap.keySet();
  }

  /**
   * get
   *
   * @param key Object
   * @return Object
   */
  public Object get(Object key) {
    return mainMap.get(key);
  }

  public Object reverseGet(Object value){
    return reverseMap.get(value);
  }

  /**
   * remove
   *
   * @param key Object
   * @return Object
   */
  public Object remove(Object key) {
    Object value = mainMap.get(key);
    if (value != null) {
      reverseMap.remove(value);
    }
    return mainMap.remove(key);
  }

  /**
   * put
   *
   * @param key Object
   * @param value Object
   * @return Object
   */
  public Object put(Object key, Object value) {
    reverseMap.put(value,key);
    return mainMap.put(key,value);
  }

}
