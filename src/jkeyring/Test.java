package jkeyring;

import jkeyring.intf.IKeyring;

public class Test {
    public static void main(String[] argv) {
	IKeyring keyring = KeyringFactory.getKeyring();
	keyring.save("test", "my data".toCharArray(), "just some test data");
	char[] data = keyring.read("test");
	if (data == null) {
	    System.out.println("failed");
	} else {
	    System.out.println(new String(data));
	    keyring.delete("test");
	}
    }
}
