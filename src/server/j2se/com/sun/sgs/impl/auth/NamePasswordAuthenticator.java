
package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;

import com.sun.sgs.kernel.KernelAppContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.CredentialException;


/**
 * This simple implementation provides authentication based on a name and
 * a password. It is intended only for development use.
 * <p>
 * The names and cooresponding passwords are provided through a file, which
 * is read on startup and then never re-read. The file is named using the
 * property <code>PASSWORD_FILE_PROPERTY</code>. The file is of a simple form
 * that consists of one entry per line, where each entry has a name, some
 * whitespace, a SHA-256 hashed password that is encoded via
 * <code>encodeBytes</code>, and finally a newline.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class NamePasswordAuthenticator implements IdentityAuthenticator
{

    /**
     * The property used to define the password file location.
     */
    public static final String PASSWORD_FILE_PROPERTY =
        "com.sun.sgs.impl.auth.NamePasswordAuthenticator.PasswordFile";

    /**
     * The default name for the password file, relative to the app root.
     */
    public static final String DEFAULT_FILE_NAME = "passwords";

    // a fixed map from name to passowrd, loaded from the password file
    private final HashMap<String,byte[]> passwordMap;

    // the instance used to hash passwords
    private MessageDigest digest;

    // the context
    private volatile KernelAppContext context = null;

    /**
     * Creates an instance of <code>NamePasswordAuthenticator</code>.
     *
     * @param properties the application's configuration properties
     *
     * @throws FileNotFoundException if the password file cannot be found
     * @throws IOException if any error occurs reading the password file
     * @throws NoSuchAlgorithmException if SHA-256 is not supported
     */
    public NamePasswordAuthenticator(Properties properties)
        throws FileNotFoundException, IOException, NoSuchAlgorithmException
    {
        if (properties == null)
            throw new NullPointerException("Null properties not allowed");

        // get the name of the password file
        String passFile = properties.getProperty(PASSWORD_FILE_PROPERTY);
        if (passFile == null) {
            String root = properties.getProperty("com.sun.sgs.rootDir");
            passFile = root + File.separator + DEFAULT_FILE_NAME;
        }

        // load the passwords
        FileInputStream in = new FileInputStream(passFile);
        StreamTokenizer stok = new StreamTokenizer(new InputStreamReader(in));
        stok.eolIsSignificant(false);
        //stok.wordChars('0', '0' + 16);
        passwordMap = new HashMap<String,byte[]>();
        while (stok.nextToken() != StreamTokenizer.TT_EOF) {
            String name = stok.sval;
            if (stok.nextToken() == StreamTokenizer.TT_EOF)
                throw new IOException("Unexpected EOL at line " +
                                      stok.lineno());
            byte [] password = decodeBytes(stok.sval.getBytes("UTF-8"));
            passwordMap.put(name, password);
        }

        // finally, create the digest we'll use to hash incoming passwords
        digest = MessageDigest.getInstance("SHA-256");
    }

    /**
     * Decodes an array of bytes that has been encoded by a call to
     * <code>encodeBytes</code>. This results in the original binary
     * representation. This is used to decode a hashed password from the
     * password file.
     *
     * @param bytes an encoded array of bytes as provided by a call
     *                 to <code>encodePassword</code>
     *
     * @return the original binary representation
     */
    public static byte [] decodeBytes(byte [] bytes) {
        byte [] decoded = new byte[bytes.length / 2];
        for (int i = 0; i < decoded.length; i++) {
            int encodedIndex = i * 2;
            decoded[i] = (byte)(((bytes[encodedIndex] - 'a') << 4) +
                                (bytes[encodedIndex + 1] - 'a'));
        }
        return decoded;
    }

    /**
     * Encodes an array of bytes in a form suitable for including in a text
     * file. In this case, a very simple base-16 encoding is used. The
     * original binary representation can be resolved by calling
     * <code>decodeBytes</code>. This is used to turn a hashed password
     * into a form suitable for the password file.
     *
     * @param bytes an array of bytes
     *
     * @return an encoding of the bytes in a form suitable for use in text
     */
    public static byte [] encodeBytes(byte [] bytes) {
        byte [] encoded = new byte[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int encodedIndex = i * 2;
            encoded[encodedIndex] = (byte)(((bytes[i] & 0xF0) >> 4) + 'a');
            encoded[encodedIndex + 1] = (byte)((bytes[i] & 0x0F) + 'a');
        }
        return encoded;
    }

    /**
     * {@inheritDoc}
     */
    public String [] getSupportedCredentialTypes() {
        return new String [] { NamePasswordCredentials.TYPE_IDENTIFIER };
    }

    /**
     * {@inheritDoc}
     */
    public void assignContext(KernelAppContext ctx) {
        if (ctx == null)
            throw new NullPointerException("Null context not allowed");
        if (context != null)
            throw new IllegalStateException("Context was already assigned");
        context = ctx;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The provided <code>IdentityCredentials</code> must be an instance
     * of <code>NamePasswordCredentials</code>.
     *
     * @throws AccountNotFoundException if the identity is unknown
     * @throws CredentialException if the credentials are invalid
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws AccountNotFoundException, CredentialException
    {
        // make sure that we were given the right type of credentials
        if (! (credentials instanceof NamePasswordCredentials))
            throw new CredentialException("unsupported credentials");
        NamePasswordCredentials npc = (NamePasswordCredentials)credentials;

        // get the name, and make sure they have a password entry
        String name = npc.getName();
        
        // HUGE HACK
        //
        // JM - for debugging, just let them in
        //

if (false) {        
        byte [] validPass = passwordMap.get(name);
        if (validPass == null)
            throw new AccountNotFoundException("Unknown user: " + name);

        // hash the given password
        byte [] pass = null;
        synchronized (digest) {
            digest.reset();
            try {
                pass = digest.digest((new String(npc.getPassword())).
                                     getBytes("UTF-8"));
            } catch (IOException ioe) {
                throw new CredentialException("Could not get password: " +
                                              ioe.getMessage());
            }
        }
        
        // verify that the hashes match
        if (! Arrays.equals(validPass, pass))
            throw new CredentialException("Invalid credentials");

}
// END HUGE HACK

        return new IdentityImpl(name);
    }

}
