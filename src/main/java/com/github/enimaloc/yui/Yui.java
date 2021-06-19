package com.github.enimaloc.yui;

import ch.qos.logback.classic.Level;
import com.github.enimaloc.yui.internal.MixedCommand;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Yui {
    
    private final EventWaiter   eventWaiter;
    private final CommandClient client;
    private final JDA           jda;
    private final Logger        logger = LoggerFactory.getLogger(Yui.class);
    
    public Yui() throws LoginException {
        logger.info("Starting Yui-MHCP001 (v."+Constant.VERSION+")");
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
        
        this.eventWaiter = new EventWaiter();
    
        List<MixedCommand> mixedCommand = Arrays.asList();
        
        this.client = new CommandClientBuilder()
                .setActivity(Activity.watching("players"))
                .setHelpWord("help")
                .setEmojis(Constant.EMOJI_SUCCESS, Constant.EMOJI_WARNING, Constant.EMOJI_ERROR)
                .setPrefix(Constant.PREFIX)
                .addSlashCommands(mixedCommand.toArray(SlashCommand[]::new))
                .addCommands(mixedCommand.toArray(Command[]::new))
                .setOwnerId(Constant.OWNERS_ID[0]+"")
                .setCoOwnerIds(
                        Arrays.stream(Arrays.copyOfRange(Constant.OWNERS_ID, 1, Constant.OWNERS_ID.length))
                              .mapToObj(l -> l+"")
                              .toArray(String[]::new)
                )
                .build();
    
        this.jda = JDABuilder.createDefault(System.getenv("token"))
                              .addEventListeners(this.eventWaiter, this.client)
                              .build();
    }
    
    public void disconnect() {
        this.client.shutdown();
        this.jda.shutdown();
    }
    
}
