/**
 *
 * <p>Title: CLObjectINputStream.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.utils.classes;


import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CLObjectInputStream
    extends ObjectInputStream {
  ClassLoader cl = null;

  public CLObjectInputStream(InputStream inputStream,
                             ClassLoader classLoader) throws IOException {
    super(inputStream);
    cl = classLoader;
  }

  protected Class resolveClass(ObjectStreamClass desc) throws
      java.io.IOException, java.lang.ClassNotFoundException {
    if (cl == null) {
      return super.resolveClass(desc);
    }
    else { // use our class loader
      return Class.forName(desc.getName(),false,cl);
    }
  }

}