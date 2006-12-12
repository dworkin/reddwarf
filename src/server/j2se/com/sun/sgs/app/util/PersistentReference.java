package com.sun.sgs.app.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;

/**
 * Stores a reference to an object that will be stored persistently either as a
 * reference to a {@link ManagedObject} or as a direct reference to a
 * serializable one.  Applications can use this class to store references to
 * objects that may or may not be managed objects without needing special case
 * logic for the two cases.
 *
 * @param	<V> the type of object associated with this reference
 */
public abstract class PersistentReference<V> implements Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** Private constructor. */
    PersistentReference() { }

    /**
     * Creates an instance that refers to the specified value.
     *
     * @param	<V> the type of the value associated with the reference
     * @param	value the value or <code>null</code>
     * @return	the reference
     */
    public static <V> PersistentReference<V> create(V value) {
	return value instanceof ManagedObject
	    ? new Managed<V>(value) : new Unmanaged<V>(value);
    }

    /**
     * Returns the value associated with the argument.  Both the argument and
     * the return value may be <code>null</code>.
     *
     * @param	<V> the type of the value associated with the reference
     * @param	ref the reference or <code>null</code>
     * @return	the value of the reference
     */
    public static <V> V get(PersistentReference<V> ref) {
	return (ref == null) ? null : ref.get();
    }

    /**
     * Returns the value associated with this reference.  The return value may
     * be <code>null</code>.
     *
     * @return	the value associated with this reference
     */
    public abstract V get();

    /**
     * Returns <code>true</code> if the value associated with this reference is
     * equal to the argument, which may be <code>null</code>.
     *
     * @param	value the value to compare to the value associated with this
     *		reference
     * @return	<code>true</code> if the value associated with this reference
     *		is equal to the argument, else <code>false</code>
     */
    public abstract boolean valueEquals(Object value);

    /**
     * Returns a string that describes the value associated with this
     * reference.
     *
     * @return	a string describing the value associated with this reference
     */
    public abstract String valueToString();

    /** Stores a value that implements ManagedObject. */
    private static final class Managed<V> extends PersistentReference<V> {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the managed object. */
	private final ManagedReference ref;

	/** The hashCode of the managed object. */
	private final int hash;

	/** Creates an instance for the specified managed object. */
	Managed(V value) {
	    /* FIXME: Use AppContext.getDataManager */
	    ref = SimpleManagedHashMap.dataManager.createReference(
		(ManagedObject) value);
	    hash = value.hashCode();
	}

	public V get() {
	    /* Parameterized maps are inherently non-typesafe. */
	    @SuppressWarnings("unchecked")
		V result = (V) ref.get(ManagedObject.class);
	    return result;
	}

	public boolean valueEquals(Object value) {
	    return value != null &&
		hash == value.hashCode() &&
		ref.get(ManagedObject.class).equals(value);
	}

	public String valueToString() {
	    return ref.toString();
	}

	public boolean equals(Object o) {
	    if (o == this) {
		return true;
	    } else if (o instanceof Managed) {
		Managed m = (Managed) o;
		return ref.equals(m.ref) ||
		    (hash == m.hash && get().equals(m.get()));
	    } else {
		return false;
	    }
	}

	public int hashCode() {
	    return hash;
	}

	public String toString() {
	    return "PersistentReference.Managed[ref:" + ref +
		"hash:" + hash + "]";
	}
    }

    /** Stores a non-managed value, which can be null. */
    private static final class Unmanaged<V> extends PersistentReference<V> {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The value. */
	private final V value;

	/** Creates an instance for the specified object or null. */
	Unmanaged(V value) {
	    this.value = value;
	}

	public V get() {
	    return value;
	}

	public boolean valueEquals(Object value) {
	    return safeEquals(this.value, value);
	}

	public String valueToString() {
	    return String.valueOf(value);
	}

	public boolean equals(Object o) {
	    return o == this ||
		(o instanceof Unmanaged &&
		 safeEquals(value, ((Unmanaged) o).value));
	}
		
	public int hashCode() {
	    return value == null ? 0 : value.hashCode();
	}

	public String toString() {
	    return "PersistentReference.Unmanaged[value:" + value + "]";
	}

	private static boolean safeEquals(Object x, Object y) {
	    return x == y || (x != null && x.equals(y));
	}
    }
}
