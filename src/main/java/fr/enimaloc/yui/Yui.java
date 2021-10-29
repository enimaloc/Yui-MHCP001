package fr.enimaloc.yui;

import com.jagrosh.jdautilities.command.*;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import fr.enimaloc.yui.commands.MusicCommand;
import fr.enimaloc.yui.music.MusicManager;
import java.util.Arrays;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;

public class Yui {

    private final  EventWaiter   eventWaiter;
    private final  CommandClient client;
    private static JDA           jda;
    private final  Logger        logger = LoggerFactory.getLogger(Yui.class);
    private final  MusicManager  musicManager;

    public Yui() throws LoginException {
        logger.info("Starting Yui-MHCP001 (v.{})", Constant.VERSION);
        logger.info("Working on `{}` git branch", Constant.GIT_BRANCH);
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));

        this.eventWaiter = new EventWaiter();
        logger.trace("EventWaiter created [{}]", this.eventWaiter);
        this.musicManager = new MusicManager();
        logger.trace("MusicManager created [{}]", this.musicManager);

        this.client = new CommandClientBuilder()
                .setActivity(Activity.watching("players"))
                .setHelpWord("help")
                .setEmojis(Constant.EMOJI_SUCCESS, Constant.EMOJI_WARNING, Constant.EMOJI_ERROR)
                .addSlashCommands(new SlashCommand[]{
                        new MusicCommand(eventWaiter, musicManager)
                })
                .setOwnerId(Constant.OWNERS_ID[0] + "")
                .forceGuildOnly(System.getenv("dev") != null ? "854508847896723526" : null)
                .setCoOwnerIds(
                        Arrays.stream(Arrays.copyOfRange(Constant.OWNERS_ID, 1, Constant.OWNERS_ID.length))
                              .mapToObj(l -> l + "")
                              .toArray(String[]::new)
                )
                .setListener(new CommandListener())
                .build();
        logger.trace("CommandClient created and built [{}]", this.client);

        jda = JDABuilder.createDefault(System.getenv("token"))
                        .addEventListeners(this.eventWaiter, this.client)
                        .build();
        logger.trace("JDA created and built [{}]", jda);

    }

    public void disconnect() {
        this.client.shutdown();
        jda.shutdown();
    }

    // This is horrible
    public static JDA getJda() {
        return jda;
    }

}
