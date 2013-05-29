package jkeyring;

import jkeyring.intf.IKeyring;

public class Test {
    public static void main(String[] argv) {
	try {
	    IKeyring keyring = KeyringFactory.getKeyring();
	    keyring.save("test", "my data".getBytes("US-ASCII"), "just some test data");
	    byte[] data = keyring.read("test");
	    if (data == null) {
		System.out.println("failed");
	    } else {
		System.out.println(new String(data, "US-ASCII"));
		keyring.delete("test");
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
