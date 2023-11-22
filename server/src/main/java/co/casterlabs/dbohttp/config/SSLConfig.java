package co.casterlabs.dbohttp.config;

import java.io.File;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rhs.server.HttpServerBuilder.SSLConfiguration;
import co.casterlabs.rhs.session.TLSVersion;

@JsonClass(exposeAll = true)
public class SSLConfig {
    public boolean enabled = false;

    public TLSVersion[] tls = TLSVersion.values();

    public String[] enabledCipherSuites = {
            "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256",
            "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_CCM",
            "TLS_DHE_PSK_WITH_AES_128_CCM",
            "TLS_DHE_RSA_WITH_AES_256_CCM",
            "TLS_DHE_RSA_WITH_AES_128_CCM",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_AES_128_CCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
    }; // Null = All Available

    public int dhSize = 2048;

    public String keystorePassword = "";
    public String keystore = "";

    public SSLConfiguration toRHS() {
        File keystoreFile = new File(this.keystore);

        SSLConfiguration rakuraiConfig = new SSLConfiguration(keystoreFile, this.keystorePassword.toCharArray());

        rakuraiConfig.setDHSize(this.dhSize);
        rakuraiConfig.setEnabledCipherSuites(this.enabledCipherSuites);
        rakuraiConfig.setEnabledTlsVersions(this.tls);

        return rakuraiConfig;
    }

}
