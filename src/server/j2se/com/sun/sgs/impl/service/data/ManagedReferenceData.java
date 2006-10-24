package com.sun.sgs.impl.service.data;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

final class ManagedReferenceData implements Serializable {

    private static final long serialVersionUID = 1;

    /** @serial */
    private final long oid;

    ManagedReferenceData(long oid) {
	this.oid = oid;
    }

    public String toString() {
	return "ManagedReferenceData[oid:" + oid + "]";
    }

    private Object readResolve() throws ObjectStreamException {
	try {
	    return DataServiceImpl.getContext().getReference(oid);
	} catch (RuntimeException e) {
	    throw (InvalidObjectException) new InvalidObjectException(
		e.getMessage()).initCause(e);
	}
    }
}

