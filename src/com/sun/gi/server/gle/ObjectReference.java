package com.sun.gi.server.gle;

import java.io.Serializable;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface ObjectReference extends Serializable{
    Serializable get(Task task);
    Serializable peek(Task task);
    void set(ObjectReference ref);
}