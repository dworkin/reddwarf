/**
 *
 * <p>Title: FlatFileUserValidator.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.comm.users.validation.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Principal;
import java.util.Map;
import java.util.StringTokenizer;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.framework.install.DeploymentRec;

/**
 * 
 * <p>
 * Title: FlatFileUserValidator.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class FlatFileUserValidator implements UserValidator {
    private Subject subject = null;

    private Map<String, String> params;

    private boolean authenticated = false;

    private boolean reset = false;

    public FlatFileUserValidator(Map<String, String> parameters) {
	params = parameters;
    }

    public Callback[] nextDataRequest() {
	synchronized (this) {
	    if (reset) {
		reset = false;
		return new Callback[] { new NameCallback("User Name", "Guest"),
		    new PasswordCallback("Password", false) };
	    } else {
		return null;
	    }
	}
    }

    public void dataResponse(Callback[] buff) {
	String name = "";
	String password = "";
	for (Callback cb : buff) {
	    if (cb instanceof NameCallback) {
		name = ((NameCallback) cb).getName();
	    } else if (cb instanceof PasswordCallback) {
		password = new String(((PasswordCallback) cb).getPassword());
	    }
	}
	try {
	    URL passfileURL = new URL(params.get("password_file_url"));
	    URLConnection conn = passfileURL.openConnection();
	    conn.connect();
	    InputStream content = conn.getInputStream();
	    InputStreamReader isrdr = new InputStreamReader(content);
	    BufferedReader rdr = new BufferedReader(isrdr);
	    String in = rdr.readLine();
	    while (in != null) {
		StringTokenizer tok = new StringTokenizer(in);
		String usrname = tok.nextToken();
		if (usrname.charAt(0) != '#') { // skip comments
		    String usrpass = tok.nextToken();
		    if (usrname.equals(name)
			&& (usrpass.equals("*")
			    || usrpass.equals(password))) {
			// found matching entry
			authenticated = true;
			subject.getPrincipals().add(
				new StringPrincipal(usrname));
			while (tok.hasMoreTokens()) {
			    String credential = tok.nextToken();
			    subject.getPublicCredentials().add(credential);
			}
		    }
		}
		in = rdr.readLine();
	    }
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public boolean authenticated() {
	return authenticated;
    }

    public Subject getSubject() {
	return subject;
    }

    public void reset(Subject subject) {
	synchronized (this) {
	    authenticated = false;
	    reset = true;
	    this.subject = subject;
	}
    }

    class StringPrincipal implements Principal {
	String name;

	public StringPrincipal(String str) {
	    name = str;
	}

	/**
	 * Returns the name of this principal
	 *
	 * @return the principal name
	 * 
	 * @see java.security.Principal#getName()
	 */
	public String getName() {
	    return name;
	}

	public boolean equals(Object obj) {
	    return name.equals(obj);
	}

	public int hashCode() {
	    return name.hashCode();
	}

	public String toString() {
	    return "StringPrincipal:" + name;
	}
    }
}
