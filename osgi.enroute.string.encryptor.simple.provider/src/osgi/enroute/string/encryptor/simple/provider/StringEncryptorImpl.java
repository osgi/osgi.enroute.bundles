package osgi.enroute.string.encryptor.simple.provider;

import java.util.Map;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.util.text.BasicTextEncryptor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(name = "osgi.enroute.string.encryptor.simple", service = StringEncryptor.class,configurationPolicy=ConfigurationPolicy.REQUIRE)
public class StringEncryptorImpl implements StringEncryptor {

	private BasicTextEncryptor basicTextencryptor;
	private final String SALT_KEY="salt";
	
	@ObjectClassDefinition
	@interface Config {
		@AttributeDefinition(cardinality=1,name="Salt",description="Salt for Encryption",type=AttributeType.PASSWORD)
		String salt();
	}

	@Activate
	void activated(Map<String, Object> config) {
		basicTextencryptor = new BasicTextEncryptor();
		basicTextencryptor.setPassword((String) config.get(SALT_KEY));
	}
	
	@Override
	public String decrypt(String encryptedMessage) {
		return basicTextencryptor.decrypt(encryptedMessage);
	}

	@Override
	public String encrypt(String message) {
		return basicTextencryptor.encrypt(message);
	}

	@Deactivate
	void deActivated() {
		basicTextencryptor = null;
	}

}
