package fr.enimaloc.yui.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import fr.enimaloc.enutils.tuples.Tuple2;
import fr.enimaloc.yui.entity.VoiceInviteAction;
import fr.enimaloc.yui.utils.WikipediaUtils;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.text.Charsets;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.ImageInfo;
import org.fastily.jwiki.dwrap.PageSection;

public class MiscCommand extends SlashCommand {

    public MiscCommand() {
        this.name     = "misc";
        this.help     = "Miscellaneous command";
        this.children = new SlashCommand[]{
                new VoiceActivity(),
                new UrlReader()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {
    }

    private class VoiceActivity extends SlashCommand {
        public VoiceActivity() {
            this.name = "voice-activity";
            this.help = "Start a voice activity";

            this.options = List.of(
                    new OptionData(OptionType.STRING, "application-id", "Application to start the game", true)
                            .addChoice("Youtube", "880218394199220334")
                            .addChoice("Youtube Dev", "880218832743055411")
                            .addChoice("Poker", "755827207812677713")
                            .addChoice("Betrayal", "773336526917861400")
                            .addChoice("Fishing", "814288819477020702")
                            .addChoice("Chess", "832012774040141894")
                            .addChoice("Chess Dev", "832012586023256104")
                            .addChoice("Lettertile", "879863686565621790")
                            .addChoice("Wordsnack", "879863976006127627")
                            .addChoice("Doodlecrew", "878067389634314250")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            Member member = event.getMember();
            if (member == null || member.getVoiceState() == null || !member.getVoiceState().inVoiceChannel()) {
                return;
            }

            long applicationId = event.getOption("application-id").getAsLong();
            Invite invite = new VoiceInviteAction(event.getJDA(), member.getVoiceState().getChannel().getId())
                    .setTargetType(VoiceInviteAction.TargetType.EMBEDDED_APPLICATION)
                    .setTargetApplicationId(applicationId)
                    .complete();

            event.reply("Here is your invite:\n" + invite.getUrl()).complete();
        }
    }

    private class UrlReader extends SlashCommand {
        public static final Pattern DISCORD_INVITE = Message.INVITE_PATTERN;
        public static final Pattern WIKIPEDIA      = Pattern.compile(
                "(?<baseWiki>https?://(?<domain>(?<lang>.*)\\.wikipedia\\.org)/wiki/)" +
                "(?<category>.*:)?(?<article>[^#]*)(?<anchor>#.*)?");
        public static final Pattern TWITCH         = Pattern.compile(
                "https?://(?:www\\.)?twitch\\.tv/(?<channel>.*)");

        public UrlReader() {
            this.name    = "url-reader";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "url", "Url to read", true),
                    new OptionData(OptionType.BOOLEAN, "display", "Display in chat or only for you")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            InteractionHook hook = event.deferReply()
                                        .setEphemeral(event.getOption("display") == null ||
                                                      !event.getOption("display").getAsBoolean())
                                        .complete();
            String url = Objects.requireNonNull(event.getOption("url")).getAsString();

            EmbedBuilder builder = new EmbedBuilder();

            Matcher matcher;
            if ((matcher = DISCORD_INVITE.matcher(url)).find()) {
                String       code    = matcher.group("code");
                Invite       invite  = Invite.resolve(event.getJDA(), code).complete();
                Invite.Guild guild   = invite.getGuild();
                boolean      isGuild = guild != null;
                Invite.Group group   = invite.getGroup();
                boolean      isGroup = group != null;
                builder.setTitle(isGuild ? guild.getName() : isGroup ?
                               "[GROUP] " + group.getName() : invite.getCode(), invite.getUrl())
                       .setThumbnail(isGuild ? guild.getIconUrl() : isGroup ? group.getIconUrl() : null);
                if (invite.getInviter() != null) {
                    builder.addField("Inviter",
                                     invite.getInviter().getAsTag() + " (" + invite.getInviter().getAsMention() + ")",
                                     true);
                }
            } else if ((matcher = WIKIPEDIA.matcher(url)).find()) {
                builder = WikipediaUtils.contentToDiscordEmbed(matcher);
            }

            hook.editOriginalEmbeds(builder.build()).queue();
        }
    }
}
