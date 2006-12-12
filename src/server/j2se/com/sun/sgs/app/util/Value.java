package com.sun.sgs.app.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.io.Serializable;

/** Stores a value that is a managed object or is just serializable. */
abstract class Value<V> implements Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** Private constructor. */
    private Value() { }

    /** Creates an instance of this class. */
    static <V> Value<V> create(V value) {
	return value instanceof ManagedObject
	    ? new ManagedValue<V>(value) : new UnmanagedValue<V>(value);
    }

    /** Returns the value associated with the argument, which can be null. */
    static <V> V get(Value<V> value) {
	return (value == null) ? null : value.get();
    }

    /** Returns the value associated with this instance. */
    abstract V get();

    /**
     * Returns true if the value this instance refers to is equal to the
     * argument.
     */
    abstract boolean sameValue(Object value);

    /** Stores a value that implements ManagedObject. */
    private static final class ManagedValue<V> extends Value<V> {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the managed object. */
	private final ManagedReference ref;

	/** The hashCode of the managed object. */
	private final int hash;

	/** Creates an instance for the specified managed object. */
	ManagedValue(V value) {
	    /* FIXME: Use AppContext.getDataManager */
	    ref = SimpleManagedHashMap.dataManager.createReference(
		(ManagedObject) value);
	    hash = value.hashCode();
	}

	V get() {
	    /* Parameterized maps are inherently non-typesafe. */
	    @SuppressWarnings("unchecked")
		V result = (V) ref.get(ManagedObject.class);
	    return result;
	}

	boolean sameValue(Object value) {
	    return value != null &&
		hash == value.hashCode() &&
		ref.get(ManagedObject.class).equals(value);
	}

	public boolean equals(Object o) {
	    if (o == this) {
		return true;
	    } else if (o instanceof ManagedValue) {
		ManagedValue mv = (ManagedValue) o;
		return ref.equals(mv.ref) ||
		    (hash == mv.hash && get().equals(mv.get()));
	    } else {
		return false;
	    }
	}

	public int hashCode() { return hash; }
    }

    /** Stores a non-managed value, which can be null. */
    private static final class UnmanagedValue<V> extends Value<V> {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The value. */
	private final V value;

	/** Creates an instance for the specified object. */
	UnmanagedValue(V value) { this.value = value; }

	V get() { return value; }

	boolean sameValue(Object value) {
	    return safeEquals(this.value, value);
	}

	public boolean equals(Object o) {
	    return o == this ||
		(o instanceof UnmanagedValue &&
		 safeEquals(value, ((UnmanagedValue) o).value));
	}
		
	public int hashCode() { return value == null ? 0 : value.hashCode(); }

	private static boolean safeEquals(Object x, Object y) {
	    return x == y || (x != null && x.equals(y));
	}
    }
}
