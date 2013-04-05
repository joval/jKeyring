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
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */

package jkeyring.impl.crypto;

import java.awt.EventQueue;
import java.awt.GridLayout;
import java.security.Key;
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
    public static final String PREFS_SALT_KEY = "salt";

    private static final String ENCRYPTION_ALGORITHM = "PBEWithSHA1AndDESede"; // NOI18N

    private SecretKeyFactory KEY_FACTORY;
    private AlgorithmParameterSpec PARAM_SPEC;

    private Preferences prefs;
    private Cipher encrypt, decrypt;
    private boolean unlocked;
    private Callable<Void> encryptionChanging;
    private char[] newMasterPassword;
    private boolean fresh;

    public MasterPasswordEncryption() {
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
	    new PasswordDialog().prompt();
	} catch (Exception x) {
	    x.printStackTrace();
	}
    }

    public boolean enabled() {
	return PARAM_SPEC != null;
    }

    public String id() {
	return "general"; // NOI18N
    }

    public byte[] encrypt(char[] cleartext) throws Exception {
	try {
	    return doEncrypt(cleartext);
	} catch (Exception x) {
	    unlocked = false; // reset
	    throw x;
	}
    }

    public char[] decrypt(byte[] ciphertext) throws Exception {
	AtomicBoolean callEncryptionChanging = new AtomicBoolean();
	try {
	    return doDecrypt(ciphertext);
	} catch (Exception x) {
	    unlocked = false; // reset
	    throw x;
	} finally {
	    if (callEncryptionChanging.get()) {
		try {
		    encryptionChanging.call();
		} catch (Exception x) {
		}
	    }
	}
    }

    void unlock(char[] masterPassword) throws Exception {
	KeySpec keySpec = new PBEKeySpec(masterPassword);
	Key key = KEY_FACTORY.generateSecret(keySpec);
	encrypt.init(Cipher.ENCRYPT_MODE, key, PARAM_SPEC);
	decrypt.init(Cipher.DECRYPT_MODE, key, PARAM_SPEC);
	unlocked = true;
    }

    byte[] doEncrypt(char[] cleartext) throws Exception {
	assert unlocked;
	byte[] cleartextB = Utils.chars2Bytes(cleartext);
	byte[] result = encrypt.doFinal(cleartextB);
	Arrays.fill(cleartextB, (byte) 0);
	return result;
    }

    char[] doDecrypt(byte[] ciphertext) throws Exception {
	assert unlocked;
	byte[] result = decrypt.doFinal(ciphertext);
	char[] cleartext = Utils.bytes2Chars(result);
	Arrays.fill(result, (byte) 0);
	return cleartext;
    }

    public boolean decryptionFailed() {
	unlocked = false;
	return false;
    }

    public void encryptionChangingCallback(Callable<Void> callback) {
	encryptionChanging = callback;
    }

    public void encryptionChanged() {
	assert newMasterPassword != null;
	try {
	    unlock(newMasterPassword);
	} catch (Exception x) {
	    x.printStackTrace();
	}
	Arrays.fill(newMasterPassword, '\0');
	newMasterPassword = null;
    }

    public void freshKeyring(boolean fresh) {
	this.fresh = fresh;
    }

    // Inner Class

    public class PasswordDialog implements Runnable {
	private JFrame guiFrame;
	private JPanel userPanel;
	private JPasswordField passwordFld;

	PasswordDialog() {
	    EventQueue.invokeLater(this);
	}

	// Implement Runnable
	 
	public void run() {
	    guiFrame = new JFrame();
	    guiFrame.setTitle("jKeyring Master Password");
	    guiFrame.setSize(500, 200);

	    //This will center the JFrame in the middle of the screen
	    guiFrame.setLocationRelativeTo(null);

	    //Using a JPanel as the message for the JOptionPane
	    userPanel = new JPanel();
	    userPanel.setLayout(new GridLayout(1,2));

	    JLabel passwordLbl = new JLabel("Password:");
	    passwordFld = new JPasswordField();

	    userPanel.add(passwordLbl);
	    userPanel.add(passwordFld);

	    guiFrame.setVisible(true);
	}

	void prompt() throws Exception {
	    //As the JOptionPane accepts an object as the message
	    //it allows us to use any component we like - in this case 
	    //a JPanel containing the dialog components we want
	    int input = JOptionPane.showConfirmDialog(guiFrame, userPanel, "Enter your password:"
				,JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

	    //OK Button = 0
	    if (input == 0) {
		char[] password = passwordFld.getPassword();
		MasterPasswordEncryption.this.unlock(password);
		Arrays.fill(password, '0');
	    }
	    guiFrame.setVisible(false);
	}
    }
}
