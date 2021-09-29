package fr.enimaloc.yui;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import fr.enimaloc.yui.music.MusicManager;
import java.util.Arrays;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Yui {
    
    private final EventWaiter   eventWaiter;
    private final CommandClient client;
    private final JDA           jda;
    private final Logger       logger = LoggerFactory.getLogger(Yui.class);
    private final MusicManager musicManager;
    
    public Yui() throws LoginException {
        logger.info("Starting Yui-MHCP001 (v."+Constant.VERSION+")");
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
        
        this.eventWaiter = new EventWaiter();
        this.musicManager = new MusicManager();

        this.client = new CommandClientBuilder()
                .setActivity(Activity.watching("players"))
                .setHelpWord("help")
                .setEmojis(Constant.EMOJI_SUCCESS, Constant.EMOJI_WARNING, Constant.EMOJI_ERROR)
                .addSlashCommands(new SlashCommand[]{
                })
                .setOwnerId(Constant.OWNERS_ID[0]+"")
                .forceGuildOnly(System.getenv("dev") != null ? "854508847896723526" : null)
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
