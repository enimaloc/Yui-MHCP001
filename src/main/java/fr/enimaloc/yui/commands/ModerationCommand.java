package fr.enimaloc.yui.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ModerationCommand extends SlashCommand {

    public ModerationCommand(){

        this.name = "moderation";
        this.help = "moderation related command";

        this.children = new SlashCommand[]{
//          new Mods(),
          new Clear(),
//          new Warn(),
//          new Mute(),
//          new Ban()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {
    }

    private class Mods extends SlashCommand{

        @Override
        protected void execute(SlashCommandEvent event) {

        }
    }

    private class Clear extends SlashCommand{

        public Clear() {
            this.name = "clear";
            this.help = "Delete message in the chat";

            this.options = List.of(new OptionData(OptionType.INTEGER, "amount", "Amount of message to delete", true) {
                @NotNull
                @Override
                public DataObject toData() {
                    return super.toData().put("min_value", 1).put("max_value", 100);
                }
            });
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.deferReply(true).complete();
            if (Objects.requireNonNull(event.getMember()).hasPermission(event.getGuildChannel(), Permission.MANAGE_CHANNEL)){
                int amount = Math.toIntExact(Objects.requireNonNull(event.getOption("amount")).getAsLong());
                List<Message> messages = event.getChannel()
                        .getHistory()
                        .retrievePast(amount).complete();
                Map<Throwable, AtomicInteger> errors = new HashMap<>();
                event.getChannel().purgeMessages(messages).forEach(future -> future.exceptionally(throwable -> {
                    errors.getOrDefault(throwable, new AtomicInteger()).incrementAndGet();
                    return null;
                }));
                event.getHook().editOriginalFormat("%d message successfully deleted%s",
                        amount - errors.values().stream().mapToLong(AtomicInteger::get).sum(),
                        errors.keySet().stream().map(key -> String.format("%d failed with error `%s: %s`",
                                errors.get(key).get(),
                                key.getClass().getName(),
                                key.getLocalizedMessage())).collect(Collectors.joining("\n"))).queue();
            }
        }
    }

    private class Warn extends SlashCommand{

        @Override
        protected void execute(SlashCommandEvent event) {

        }
    }

    private class Mute extends SlashCommand{

        @Override
        protected void execute(SlashCommandEvent event) {

        }
    }

    private class Ban extends SlashCommand{

        @Override
        protected void execute(SlashCommandEvent event) {

        }
    }
}
