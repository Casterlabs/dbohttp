package co.casterlabs.dbohttp;

import com.auth0.jwt.interfaces.JWTVerifier;

import co.casterlabs.dbohttp.config.Config;
import co.casterlabs.dbohttp.daemon.Daemon;
import co.casterlabs.dbohttp.database.Database;

public class DBOHTTP {
    public static Config config;
    public static Daemon daemon;
    public static Database database;
    public static Heartbeat heartbeat;

    public static JWTVerifier queryVerifier;
    public static JWTVerifier infoVerifier;

}
