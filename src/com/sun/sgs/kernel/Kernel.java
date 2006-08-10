package com.sun.sgs.kernel;

import com.sun.sgs.Quality;
import com.sun.sgs.User;

import java.nio.ByteBuffer;
import java.util.Properties;


/**
 * The <code>Kernel</code> class is a singleton instance that provides
 * access to global resources.
 *
 *
 * @since 1.0
 * @author James Megquier
 * @author David Jurgens
 */
public class Kernel {

    /**
     * The global resource coordinator that [TBD].
     */
    private final ResourceCoordinator resourceCoordinator;

    /**
     * The user that represents the system for {@link Task} purposes.
     */
    private final User systemUser;

    /** 
     * The singleton instance of the Kernel.
     */
    private static Kernel singleton;

    /**
     * The system-level properties map.
     */ 
    private final Properties properties;

    /*
     * The restricted access constructur.
     */
    private Kernel() {
	// REMDINER: figure out what else needs to be initialized in
	// the constructor.
	this.resourceCoordinator = null; // REMINDER: create an implementation of this
	this.systemUser = new User() {
		public void send(ByteBuffer data, Quality quality) { /* null */
		}
	    };
	properties = new Properties(); // REMINDER: identify where these properties really come from.
    }

    public ResourceCoordinator getResourceCoordinator() {
	return resourceCoordinator;
    }
    
    /**
     * Returns the value of the system property associated with
     * <code>key</code>, or <code>null</code> if no property has been
     * defined.
     *
     * @param key the name of the system property.
     *
     * @return the value of the system property or <code>null</code>
     *         if the property is undefined.
     */
    public String getSystemProperty(String key) {
	return properties.getProperty(key);
    }

    public User getSystemUser() {
	return systemUser;
    }

    public static synchronized Kernel instance() {
	if (singleton == null)
	    singleton = new Kernel();
	return singleton;
    }

}
