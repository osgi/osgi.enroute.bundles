package osgi.enroute.string.encryptor.simple.provider;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class StringEncryptorImplTest extends TestCase {
	
	private StringEncryptorImpl encryptorImpl = new StringEncryptorImpl();
	

	public void setUp(){
		Map<String,Object> config = new HashMap<>();
		config.put("salt", "enroute");
		encryptorImpl.activated(config);
	}
	
	//Skipped encryption as we can do any testing on encrypted value
	
	public void testDecryption(){		
		String encryptedText = "uW3lqLTmvWqU3limfg63wwtenZyC03+M"; //Encrypted text for "enRoute Rocks"
		String decryptedText = encryptorImpl.decrypt(encryptedText);
		assertEquals("enRoute Rocks", decryptedText);		
	}
	
	public void testHackedDecryption(){		
		String encryptedText = "uW3lqLTmvWqU3limfg63wwtenZyC03"; //Hacked Encrypted text for "enRoute Rocks"
		String decryptedText="";
		try {
			decryptedText = encryptorImpl.decrypt(encryptedText);
			fail("Corrupted text should fail encryption");
		} catch (Exception e) {
			assertTrue(e instanceof org.jasypt.exceptions.EncryptionOperationNotPossibleException);
		}		
	}
	
	public void tearDown(){
		encryptorImpl.deActivated();
	}
}
