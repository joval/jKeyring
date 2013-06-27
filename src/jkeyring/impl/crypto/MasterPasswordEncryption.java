/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 * jOVAL.org elects to include this software in this distribution
 * under the CDDL license.
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */

package jkeyring.impl.crypto;

import java.awt.EventQueue;
import java.awt.GridLayout;
import java.security.Key;
import java.io.Console;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JLabel;

import jkeyring.impl.Utils;
import jkeyring.intf.IEncryptionProvider;

/**
 * Encrypts data using a master password governed by Preferences.
 */
public class MasterPasswordEncryption implements IEncryptionProvider {
    public static final int BACKSPACE = '';
    public static final int CR = '\r';
    public static final int LF = '\n';

    public static final String PREFS_SALT_KEY = "salt";

    private static final String ENCRYPTION_ALGORITHM = "PBEWithSHA1AndDESede"; // NOI18N

    private SecretKeyFactory KEY_FACTORY;
    private AlgorithmParameterSpec PARAM_SPEC;

    private Preferences prefs;
    private Cipher encrypt, decrypt;
    private boolean unlocked;
    private Callable<Void> encryptionChanging;
    private char[] newMasterPassword = null;
    private boolean fresh;
    private Mode mode;

    public MasterPasswordEncryption(Mode mode) {
	this.mode = mode;
	try {
	    KEY_FACTORY = SecretKeyFactory.getInstance(ENCRYPTION_ALGORITHM);
	    encrypt = Cipher.getInstance(ENCRYPTION_ALGORITHM);
	    decrypt = Cipher.getInstance(ENCRYPTION_ALGORITHM);
	    Preferences prefs = Utils.userPreferences().node("jKeyring");
	    byte[] salt = prefs.getByteArray(PREFS_SALT_KEY, null);
	    if (salt == null) {
		salt = new byte[36];
		new SecureRandom().nextBytes(salt);
		prefs.putByteArray(PREFS_SALT_KEY, salt);
	    }
	    PARAM_SPEC = new PBEParameterSpec(salt, 20);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public boolean enabled() {
	return PARAM_SPEC != null;
    }

    public String id() {
	return "general"; // NOI18N
    }

    public byte[] encrypt(byte[] cleartext) throws Exception {
	try {
	    return doEncrypt(cleartext);
	} catch (Exception e) {
	    unlocked = false; // reset
	    throw e;
	}
    }

    public byte[] decrypt(byte[] ciphertext) throws Exception {
	AtomicBoolean callEncryptionChanging = new AtomicBoolean();
	try {
	    return doDecrypt(ciphertext);
	} catch (Exception e) {
	    unlocked = false; // reset
	    throw e;
	} finally {
	    if (callEncryptionChanging.get()) {
		try {
		    encryptionChanging.call();
		} catch (Exception e) {
		}
	    }
	}
    }

    // Internal

    void unlock() throws Exception {
	char[] masterPassword = null;
	if (newMasterPassword == null) {
	    switch(mode) {
	      case GUI:
		masterPassword = new PasswordDialog().prompt();
		break;
	      case CLI:
		masterPassword = getPassword();
		break;
	    }
	} else {
	    masterPassword = newMasterPassword;
	}
	KeySpec keySpec = new PBEKeySpec(masterPassword);
	Key key = KEY_FACTORY.generateSecret(keySpec);
	encrypt.init(Cipher.ENCRYPT_MODE, key, PARAM_SPEC);
	decrypt.init(Cipher.DECRYPT_MODE, key, PARAM_SPEC);
	unlocked = true;
	Arrays.fill(masterPassword, '0');
    }

    byte[] doEncrypt(byte[] cleartext) throws Exception {
	if (!unlocked) {
	    unlock();
	}
	assert unlocked;
	return encrypt.doFinal(cleartext);
    }

    byte[] doDecrypt(byte[] ciphertext) throws Exception {
	if (!unlocked) {
	    unlock();
	}
	assert unlocked;
	return decrypt.doFinal(ciphertext);
    }

    public boolean decryptionFailed() {
	unlocked = false;
	switch(mode) {
	  case GUI:
	    JOptionPane.showMessageDialog(new JFrame(), "Failed to open keyring", "Password incorrect",
		JOptionPane.ERROR_MESSAGE);
	    break;
	  case CLI:
	    System.err.println("*** Password Incorrect ***");
	    System.out.println("Failed to open keyring");
	    break;
	}
	return false;
    }

    public void encryptionChangingCallback(Callable<Void> callback) {
	encryptionChanging = callback;
    }

    public void encryptionChanged() {
	assert newMasterPassword != null;
	try {
	    unlock();
	} catch (Exception e) {
	    e.printStackTrace();
	}
	Arrays.fill(newMasterPassword, '\0');
	newMasterPassword = null;
    }

    public void freshKeyring(boolean fresh) {
	this.fresh = fresh;
    }

    // Private 

    /**
     * Obtain the master password using the command-line.
     */
    private char[] getPassword() throws Exception {
	return System.console().readPassword("%s", "Master password: ");
    }

    /**
     * Obtain the master password using a dialog box.
     */
    class PasswordDialog {
	private JPanel userPanel;
	private JPasswordField passwordField, confirmField = null;
	private String title;

	PasswordDialog() {
	    this(null);
	}

	private PasswordDialog(String title) {
	    this.title = title;
	    passwordField = new JPasswordField(10);
	    userPanel = new JPanel();
	    if (fresh) {
		if (title == null) {
		    this.title = "Enter a password for the new keyring";
		}
		userPanel.setLayout(new GridLayout(2,2));

		// first row
		userPanel.add(new JLabel("New Master Password:", JLabel.RIGHT));
		userPanel.add(passwordField);

		// second row
		userPanel.add(new JLabel("Confirm Password:", JLabel.RIGHT));
		confirmField = new JPasswordField(10);
		userPanel.add(confirmField);
	    } else {
		if (title == null) {
		    this.title = "Enter master password";
		}
		userPanel.setLayout(new GridLayout(1,2));

		// single row
		userPanel.add(new JLabel("Master Password:", JLabel.CENTER));
		userPanel.add(passwordField);
	    }
	}

	char[] prompt() throws Exception {
	    //
	    // As the JOptionPane accepts an object as the message
	    // it allows us to use any component we like - in this case 
	    // a JPanel containing the dialog components we want
	    //
	    int input = JOptionPane.showConfirmDialog(new JFrame(), userPanel, title, JOptionPane.OK_CANCEL_OPTION,
		JOptionPane.PLAIN_MESSAGE);

	    //OK Button = 0
	    if (input == 0) {
		if (confirmField == null) {
		    return passwordField.getPassword();
		} else {
		    if (Arrays.equals(passwordField.getPassword(), confirmField.getPassword())) {
			Arrays.fill(confirmField.getPassword(), '0');
			return passwordField.getPassword();
		    } else {
			return new PasswordDialog("Mismatch, try again:").prompt();
		    }
		}
	    } else {
		throw new Exception("cancelled");
	    }
	}
    }
}
