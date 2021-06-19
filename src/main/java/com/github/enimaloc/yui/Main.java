package com.github.enimaloc.yui;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sentry.Sentry;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.Locale;

public class Main {
    
    public static void main(String[] args) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                .setLevel(Level.toLevel(
                        System.getenv("logLevel") != null ? System.getenv("logLevel").toUpperCase(Locale.ROOT) : null,
                        Level.INFO
                ));
        
        if (System.getenv("sentryDns") != null) {
            Sentry.init(options -> {
                options.setDsn(System.getenv("sentryDns"));
                options.setDist(System.getProperty("os.arch"));
                options.setEnvironment(System.getenv("dev") != null ? "Development" : "Production");
            });
        }
    
        try {
            new Yui();
        } catch (LoginException e) {
            LoggerFactory.getLogger(Main.class).error("Invalid token", e);
        }
    
    }
    
}
