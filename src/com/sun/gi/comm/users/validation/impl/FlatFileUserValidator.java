/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
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

/**
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
                    if (usrname.equals(name) &&
                            (usrpass.equals("*") || usrpass.equals(password)))
                    {
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
