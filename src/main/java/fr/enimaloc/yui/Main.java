package fr.enimaloc.yui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import fr.enimaloc.jvmutils.JavaUtils;
import java.util.Locale;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Main {
    
    public static void main(String[] args) {
        JavaUtils.Fixes.fixeForkJoinPool();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                .setLevel(Level.toLevel(
                        System.getenv("logLevel") != null ? System.getenv("logLevel").toUpperCase(Locale.ROOT) : null,
                        Level.INFO
                ));
    
        try {
            new Yui();
        } catch (LoginException e) {
            LoggerFactory.getLogger(Main.class).error("Invalid token", e);
        }
    
    }
    
}
