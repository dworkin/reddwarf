package com.sun.sgs.app.util;

import java.io.Serializable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

@SuppressWarnings({"unchecked"})
public class PrefixHashSet<E>
    extends AbstractSet<E>
    implements Set<E>, Serializable, ManagedObject {

    private static final long serialVersionUID = 1230892300L;

    private static final Marker PRESENT = new Marker();

    private final ManagedReference map;
    //private final PrefixHashMap<E,Marker> map;

    public PrefixHashSet() {
	map = AppContext.getDataManager().
	    createReference(new PrefixHashMap<E,Marker>());
	
    }
 
    public Iterator<E> iterator() {
	return map.get(PrefixHashMap.class).keySet().iterator();
    }
   
    public int size() {
	return map.get(PrefixHashMap.class).size();
    }

    public boolean isEmpty() {
	return map.get(PrefixHashMap.class).isEmpty();
    }

    public boolean contains(Object o) {
	return map.get(PrefixHashMap.class).containsKey(o);
    }

    public boolean add(E e) {
	return map.get(PrefixHashMap.class).put(e, PRESENT) == null;
    }

    public boolean remove(Object o) {
	return map.get(PrefixHashMap.class).remove(o) == PRESENT;
    }

    public void clear() {
	map.get(PrefixHashMap.class).clear();
    }

    private static class Marker implements Serializable { 
	private static final long serialVersionUID = 3;
    }
    
}