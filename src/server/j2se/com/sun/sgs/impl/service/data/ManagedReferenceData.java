package com.sun.sgs.impl.service.data;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

final class ManagedReferenceData implements Serializable {

    private static final long serialVersionUID = 1;

    /** @serial */
    private final long id;

    ManagedReferenceData(long id) {
	this.id = id;
    }

    public String toString() {
	return "ManagedReferenceData[id:" + id + "]";
    }

    private Object readResolve() throws ObjectStreamException {
	try {
	    return DataServiceImpl.getContext().getReference(id);
	} catch (RuntimeException e) {
	    throw (InvalidObjectException) new InvalidObjectException(
		e.getMessage()).initCause(e);
	}
    }
}

