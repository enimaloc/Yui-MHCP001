package fr.enimaloc.yui;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.slf4j.LoggerFactory;

public class CommandListener implements com.jagrosh.jdautilities.command.CommandListener {

    @Override
    public void onSlashCommandException(
            SlashCommandEvent event, SlashCommand command, Throwable throwable
    ) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("An exception as occurred while processing your command")
                .appendDescription(throwable.getClass().getSimpleName()+": "+throwable.getLocalizedMessage());

        if (event.isAcknowledged()) {
            event.getChannel().sendMessageEmbeds(builder.build()).complete();
        } else {
            event.replyEmbeds(builder.build()).setEphemeral(true).complete();
        }
        LoggerFactory.getLogger(CommandListener.class).error("Slash command exception", throwable);
    }
}
