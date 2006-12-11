
package com.sun.sgs.impl.auth;

import com.sun.sgs.impl.auth.NamePasswordAuthenticator;

import java.io.FileOutputStream;

import java.security.MessageDigest;


/**
 * This is a simple utility program used to create the password files that
 * are consumed by <code>NamePasswordAuthenticator</code>. The password
 * files consist of one entry per line, where each entry has a name, some
 * whitespace, a SHA-256 hashed password encoded vi a call to
 * <code>NamePasswordAuthenticator.encodeBytes</code>, and finally a newline.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PasswordFileEditor
{

    /**
     * Main-line for this utility.
     */
    public static void main(String [] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: password_file name password");
            return;
        }

        // make sure we can hash and encode the password
        byte [] pass = MessageDigest.getInstance("SHA-256").
            digest(args[2].getBytes("UTF-8"));
        byte [] encodedPass = NamePasswordAuthenticator.encodeBytes(pass);

        // open the file and append the new entry
        FileOutputStream out = new FileOutputStream(args[0], true);
        out.write(args[1].getBytes("UTF-8"));
        out.write("\t".getBytes("UTF-8"));
        out.write(encodedPass);
        out.write("\n".getBytes("UTF-8"));
    }

}
