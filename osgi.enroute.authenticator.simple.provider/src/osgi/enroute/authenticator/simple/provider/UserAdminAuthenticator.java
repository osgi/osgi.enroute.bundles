package osgi.enroute.authenticator.simple.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.slf4j.Logger;

import osgi.enroute.authentication.api.Authenticator;
import osgi.enroute.authenticator.simple.provider.Config.Algorithm;
import osgi.enroute.debug.api.Debug;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.lib.base64.Base64;
import aQute.lib.hex.Hex;

@Component(property = {
        Debug.COMMAND_SCOPE + "=auth", Debug.COMMAND_FUNCTION + "=hash", Debug.COMMAND_FUNCTION + "=passwd", Debug.COMMAND_FUNCTION + "=adduser",
        Debug.COMMAND_FUNCTION + "=rmrole", Debug.COMMAND_FUNCTION + "=role"
})
public class UserAdminAuthenticator implements Authenticator {
    private static final Pattern	AUTHORIZATION_P	= Pattern.compile("Basic\\s+(?<base64>[A-Za-z0-9+/]{3,}={0,2})");
    private static final Pattern	IDPW_P			= Pattern.compile("(?<id>[^:]+):(?<pw>.*)");
    private UserAdmin				userAdmin;
    private Logger					log;

    private byte[]					salt;
    private Algorithm				algorithm;
    private int						iterations;
    private String					root;

    @Activate
    void activate(final Map<String,Object> args) {
        final Config config = Configurable.createConfigurable(Config.class, args);

        this.salt = config.salt();
        if (this.salt == null || this.salt.length == 0) {
            this.salt = new byte[] {
                    0x2f, 0x68, (byte) 0xcb, 0x75, 0x6c, (byte) 0xf1, 0x74, (byte) 0x84, 0x2a, (byte) 0xef
            };
        }

        this.algorithm = config.algorithm();
        if (this.algorithm == null) {
            this.algorithm = Algorithm.PBKDF2WithHmacSHA1;
        }

        this.iterations = config.iterations();
        if (this.iterations < 100) {
            this.iterations = 997;
        }

        this.root = config._root();
        if (this.root != null && this.root.trim().isEmpty()) {
            this.root = null;
        }
    }

    @Override
    public String authenticate(final Map<String,Object> arguments, final String... sources) throws Exception {

        for (final String source : sources) {

            if (Authenticator.BASIC_SOURCE_PASSWORD.equals(source)) {
                final String id = (String) arguments.get(Authenticator.BASIC_SOURCE_USERID);
                final String pw = (String) arguments.get(Authenticator.BASIC_SOURCE_PASSWORD);
                if (id != null && pw != null) {
                    return verify(id, pw);
                }

                this.log.info("BASIC_SOURCE_PASSWORD specified but no userid/password found in arguments");
            }

            if (Authenticator.SERVLET_SOURCE.equals(source)) {
                final String uri = (String) arguments.get(Authenticator.SERVLET_SOURCE_METHOD);
                if (uri.startsWith("https:")) {
                    final TreeMap<String,Object> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    map.putAll(arguments);

                    final String auth = (String) map.get("Authorization");
                    if (auth != null) {
                        Matcher m = AUTHORIZATION_P.matcher(auth);

                        if (m.find()) {
                            final String base64 = m.group("base64");
                            final byte[] bytes = Base64.decodeBase64(base64);
                            final String pwId = new String(bytes, "UTF-8");
                            m = IDPW_P.matcher(pwId);
                            if (m.matches()) {
                                final String id = m.group("id");
                                final String pw = m.group("pw");
                                return verify(id, pw);
                            }
                        }

                        // assume it is not Basic but something else

                    } else {
                        this.log.warn("Servlet authentication requires an Authorization header");
                    }
                } else {
                    this.log.warn("Servlet authentication requires https {}", uri);
                }
            }
        }
        return null;
    }

    @Override
    public boolean forget(final String userid) throws Exception {
        return false;
    }

    private String verify(final String id, final String pw) throws Exception {
        final Role role = this.userAdmin.getRole(id);
        if (role == null) {
            this.log.info("Failed login attempt for %s: no such user", id);
            return null;
        }

        if (!(role instanceof User)) {
            this.log.info("Failed login attempt for %s: id is not a user name but %s", id, role);
            return null;
        }

        final User user = (User) role;

        final String hash = hash(pw);
        if (user.hasCredential(this.algorithm.toString(), hash)) {
            return id;
        }

        if (this.root != null && this.root.equals(hash)) {
            this.log.info("Root login by %s", id);
            return id;
        }

        this.log.info("Failed login attempt for %s: invalid password", id);
        return null;
    }

    public String hash(final String password) throws Exception {
        switch (this.algorithm) {
        default :
        case PBKDF2WithHmacSHA1 :
            final byte[] hash = pbkdf2(password.toCharArray(), this.salt, this.iterations, 24);
            return Hex.toHexString(hash);
        }
    }

    byte[] pbkdf2(final char[] password, final byte[] salt, final int iterations, final int bytes) throws Exception {
        final PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        final SecretKeyFactory skf = SecretKeyFactory.getInstance(this.algorithm.toString());
        return skf.generateSecret(spec).getEncoded();
    }

    public Role adduser(final String id) {
        // TODO validate id
        return this.userAdmin.createRole(id, Role.USER);
    }

    public List<Role> role(final String... filter) throws InvalidSyntaxException {
        final List<Role> roles = new ArrayList<>();

        if (filter.length == 0) {
            getRoles(roles, null);
        } else {

            for (final String f : filter) {
                if (f.startsWith("(") && f.endsWith(")")) {
                    getRoles(roles, f);
                } else {
                    final Role r = this.userAdmin.getRole(f);
                    if (f != null) {
                        roles.add(r);
                    }
                }
            }
        }
        return roles;
    }

    private void getRoles(final List<Role> roles, final String filter) throws InvalidSyntaxException {
        final Role[] rs = this.userAdmin.getRoles(filter);
        if (rs != null) {
            for (final Role role : rs) {
                roles.add(role);
            }
        }
    }

    public int rmrole(final String... id) {
        int n = 0;
        for (final String i : id) {
            this.userAdmin.removeRole(i);
            n++;
        }
        return n;
    }

    @SuppressWarnings("unchecked")
    public void passwd(final String id, final String pw) throws Exception {
        Role role = this.userAdmin.getRole(id);

        if (role == null) {
            role = this.userAdmin.createRole(id, Role.USER);
        } else if (!(role instanceof User)) {
            System.err.println("Not a user role, but " + role);
        }

        final User user = (User) role;

        user.getCredentials().put(this.algorithm.toString(), hash(pw));
    }

    @Reference
    void setUA(final UserAdmin userAdmin) {
        this.userAdmin = userAdmin;
    }

    @Reference
    void setLog(final Logger logger) {
        this.log = logger;
    }

}
