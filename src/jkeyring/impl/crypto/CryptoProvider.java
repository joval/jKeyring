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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

import jkeyring.impl.Utils;
import jkeyring.intf.IEncryptionProvider;
import jkeyring.intf.IKeyring;

/**
 * Cryptographic keyring provider using an encryption provider, which stores data in the user preferences.
 */
public class CryptoProvider implements IKeyring, Callable<Void> {
    private static final String DESCRIPTION = ".description";
    private static final String SAMPLE_KEY = "__sample__";

    private IEncryptionProvider encryption;

    /**
     * Create a new cryptographic IKeyring with the specified encryption provider.
     */
    public CryptoProvider(IEncryptionProvider encryption) {
	this.encryption = encryption;
    }

    // Implement IKeyring
 
    public boolean enabled() {
        if (encryption != null) {
            if (encryption.enabled()) {
                encryption.encryptionChangingCallback(this);
                return testSampleKey(prefs());
            }
        }
        return false;
    }

    public char[] read(String key) {
        byte[] ciphertext = prefs().getByteArray(key, null);
        if (ciphertext == null) {
            return null;
        }
        try {
            return encryption.decrypt(ciphertext);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }

    public void save(String key, char[] password, String description) {
        _save(key, password, description);
    }

    public void delete(String key) {
        Preferences prefs = prefs();
        prefs.remove(key);
        prefs.remove(key + DESCRIPTION);
    }

    // Implement Callable<Void>

    // encryption changing
    public Void call() throws Exception {
        Map<String,char[]> saved = new HashMap<String,char[]>();
        Preferences prefs = prefs();
        for (String k : prefs.keys()) {
            if (k.endsWith(DESCRIPTION)) {
                continue;
            }
            byte[] ciphertext = prefs.getByteArray(k, null);
            if (ciphertext == null) {
                continue;
            }
            saved.put(k, encryption.decrypt(ciphertext));
        }
        encryption.encryptionChanged();
        for (Map.Entry<String,char[]> entry : saved.entrySet()) {
            prefs.putByteArray(entry.getKey(), encryption.encrypt(entry.getValue()));
        }
        return null;
    }

    // Private
    
    private boolean testSampleKey(Preferences prefs) {
        byte[] ciphertext = prefs.getByteArray(SAMPLE_KEY, null);
        if (ciphertext == null) {
            encryption.freshKeyring(true);
            byte[] randomArray = new byte[36];
            new SecureRandom().nextBytes(randomArray);
            if (_save(SAMPLE_KEY, (SAMPLE_KEY + new String(randomArray)).toCharArray(), "Sample key")) {
                return true;
            } else {
                return false;
            }
        } else {
            encryption.freshKeyring(false);
            while (true) {
                try {
                    if (new String(encryption.decrypt(ciphertext)).startsWith(SAMPLE_KEY)) {
                        return true;
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                }
                return !encryption.decryptionFailed();
            }
        }
    }

    private Preferences prefs() {
        return Utils.userPreferences().node("jKeyring").node(encryption.id());
    }

    private boolean _save(String key, char[] password, String description) {
        Preferences prefs = prefs();
        try {
            prefs.putByteArray(key, encryption.encrypt(password));
        } catch (Exception x) {
            x.printStackTrace();
            return false;
        }
        if (description != null) {
            // Preferences interface gives no access to *.properties comments, so:
            prefs.put(key + DESCRIPTION, description);
        }
        return true;
    }
}
