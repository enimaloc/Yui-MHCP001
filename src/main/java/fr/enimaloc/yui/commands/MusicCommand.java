package fr.enimaloc.yui.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import fr.enimaloc.enutils.classes.DateUtils;
import fr.enimaloc.enutils.classes.ObjectUtils;
import fr.enimaloc.yui.music.GuildMusicManager;
import fr.enimaloc.yui.music.MusicManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;

public class MusicCommand extends SlashCommand {

    private final EventWaiter  eventWaiter;
    private final MusicManager musicManager;

    public MusicCommand(EventWaiter eventWaiter, MusicManager musicManager) {
        this.eventWaiter  = eventWaiter;
        this.musicManager = musicManager;

        this.name = "music";
        this.help = "Music related command";

        this.children = new SlashCommand[]{
                new Play(),
                new Skip()
        };
    }

    @Override
    public void execute(SlashCommandEvent event) {
    }

    private class Play extends SlashCommand {

        public Play() {
            this.name = "play"; // Will be used as the sub command.
            this.help = "Play a music";

            this.options = List.of(
                    new OptionData(OptionType.STRING, "query", "Url/Name of the song", true),
                    new OptionData(OptionType.STRING, "service", "Service to use when search")
                            .addChoice("Youtube", "ytsearch")
                            .addChoice("Youtube Music", "ytmsearch")
//                            .addChoice("Soundcloud", "scsearch")
            );
        }

        @Override
        public void execute(SlashCommandEvent event) {
            Member member = event.getMember();
            if (member == null ||
                member.getVoiceState() == null ||
                event.getGuild() == null) {
                return;
            }

            String track = Objects.requireNonNull(event.getOption("query")).getAsString();
            try {
                new URL(track);
            } catch (MalformedURLException e) {
                track = ObjectUtils.getOr(() -> Objects.requireNonNull(event.getOption("service")).getAsString(),
                                          "ytsearch") + ":" + track;
            }

            if (!event.getGuild().getAudioManager().isConnected() &&
                !member.getVoiceState().inVoiceChannel()) {
                SelectionMenu.Builder musicChannelMenu = SelectionMenu.create("musicChannel").setMaxValues(1);
                for (VoiceChannel voiceChannel : event.getGuild().getVoiceChannels()) {
                    if (hasPermDJ(member, voiceChannel)) {
                        musicChannelMenu.addOption(voiceChannel.getName(), voiceChannel.getId());
                    }
                }

                boolean haveProposedChannels = !musicChannelMenu.getOptions().isEmpty();
                ReplyAction replyAction = event.reply("Please go in voice channel to do that command" +
                                                      (haveProposedChannels ?
                                                              ", or select a channel from the list below:" :
                                                              ""))
                                               .setEphemeral(true);
                if (haveProposedChannels) {
                    replyAction = replyAction.addActionRow(musicChannelMenu.build());
                }
                InteractionHook hook = replyAction.complete();

                String finalTrack = track;
                eventWaiter.waitForEvent(SelectionMenuEvent.class,
                                         sme -> sme.getMessageIdLong() ==
                                                hook.retrieveOriginal().complete().getIdLong(),
                                         sme -> {
                                             String channelId =
                                                     Objects.requireNonNull(sme.getInteraction().getSelectedOptions())
                                                            .get(0)
                                                            .getValue();
                                             event.getGuild()
                                                  .getAudioManager()
                                                  .openAudioConnection(event.getGuild().getVoiceChannelById(channelId));
                                             musicManager.load(event.getGuild(), finalTrack,
                                                               new ResultHandler(event, sme));
                                         });
                return;
            }
            musicManager.load(event.getGuild(), track, new ResultHandler(event));
        }

        private class ResultHandler implements AudioLoadResultHandler {
            private final SlashCommandEvent             event;
            private       GenericInteractionCreateEvent gice;

            public ResultHandler(SlashCommandEvent event) {
                this.event = event;
            }

            public ResultHandler(SlashCommandEvent event, GenericInteractionCreateEvent gice) {
                this.event = event;
                this.gice  = gice;
            }

            @SuppressWarnings("DuplicatedCode")
            @Override
            public void trackLoaded(AudioTrack track) {
                Guild  guild  = event.getGuild();
                Member member = event.getMember();
                if (guild == null || member == null) {
                    return;
                }
                GuildVoiceState voiceState   = member.getVoiceState();
                VoiceChannel    voiceChannel = voiceState != null ? voiceState.getChannel() : null;

                if (!guild.getAudioManager().isConnected() && voiceChannel != null) {
                    guild.getAudioManager().openAudioConnection(voiceState.getChannel());
                } else {
                    voiceChannel = guild.getAudioManager().getConnectedChannel();
                }

                Objects.requireNonNull(getReplyAction())
                       .addEmbeds(prepareEmbed(track, voiceChannel).build())
                       .setEphemeral(true)
                       .complete();
                musicManager.play(event.getGuild(), track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    ReplyAction action = Objects.requireNonNull(getReplyAction())
                                                .setContent("Result of the search, please select:")
                                                .setEphemeral(true);

                    List<Component> buttons = new ArrayList<>();
                    action = action.addActionRow(
                            Button.secondary("all", "Play all (" + playlist.getTracks().size() + " tracks)"));
                    for (AudioTrack track : playlist.getTracks()) {
                        buttons.add(Button.primary(track.getInfo().identifier, track.getInfo().title.length() > 50 ?
                                track.getInfo().title.substring(0, 50) + "..." :
                                track.getInfo().title));
                        if (buttons.size() == 5) {
                            action = action.addActionRow(buttons);
                            buttons.clear();
                        }
                    }
                    if (!buttons.isEmpty()) {
                        action = action.addActionRow(buttons);
                    }
                    InteractionHook hook = action.complete();

                    Consumer<ButtonClickEvent> onClick = bce -> {
                        this.gice = bce;
                        if (Objects.equals(Objects.requireNonNull(bce.getButton()).getId(), "all")) {
                            playPlaylist(playlist);
                        } else {
                            musicManager.load(event.getGuild(), bce.getButton().getId(), this);
                        }
                    };
                    eventWaiter.waitForEvent(ButtonClickEvent.class,
                                             bce -> bce.getMessageIdLong() ==
                                                    hook.retrieveOriginal().complete().getIdLong(),
                                             onClick);
                } else {
                    playPlaylist(playlist);
                }
            }

            @SuppressWarnings("DuplicatedCode")
            private void playPlaylist(AudioPlaylist playlist) {
                Guild  guild  = event.getGuild();
                Member member = event.getMember();
                if (guild == null || member == null) {
                    return;
                }
                GuildVoiceState voiceState   = member.getVoiceState();
                VoiceChannel    voiceChannel = voiceState != null ? voiceState.getChannel() : null;

                if (!guild.getAudioManager().isConnected() && voiceChannel != null) {
                    guild.getAudioManager().openAudioConnection(voiceState.getChannel());
                } else {
                    voiceChannel = guild.getAudioManager().getConnectedChannel();
                }

                Objects.requireNonNull(getReplyAction())
                       .addEmbeds(prepareEmbed(playlist, voiceChannel).build())
                       .setEphemeral(true)
                       .complete();

                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }
                musicManager.play(guild, firstTrack);
                boolean queuing = false;
                for (AudioTrack track : playlist.getTracks()) {
                    if (!queuing) {
                        queuing = track.getInfo().identifier.equals(firstTrack.getInfo().identifier);
                    } else {
                        musicManager.play(guild, track);
                    }
                }
            }

            @Override
            public void noMatches() {
                Objects.requireNonNull(getReplyAction())
                       .setContent("No match for `%s`".formatted(Objects.requireNonNull(event.getOption("query"))
                                                                        .getAsString()))
                       .setEphemeral(true)
                       .complete();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                Objects.requireNonNull(getReplyAction())
                       .setContent(exception.severity.name() + "\n" + exception.getLocalizedMessage())
                       .setEphemeral(true)
                       .complete();
            }

            private ReplyAction getReplyAction() {
                return !event.isAcknowledged() ? event.deferReply() : gice != null ? gice.deferReply() : null;
            }

            private EmbedBuilder prepareEmbed(AudioItem item, VoiceChannel voiceChannel) {
                String title     = "PLACEHOLDER";
                String uri       = "https://PLACEHOLDER.com";
                String firstLine = "PLACEHOLDER";
                long   duration  = 0;

                if (item instanceof AudioTrack track) {
                    AudioTrackInfo info = track.getInfo();
                    title     = info.title;
                    uri       = info.uri;
                    firstLine = "**Author**: %s".formatted(info.author);
                    duration  = info.isStream ? -1 : info.length;
                }
                if (item instanceof AudioPlaylist playlist) {
                    title = playlist.isSearchResult() ?
                            playlist.getName().replaceFirst("Search results for: ", "") :
                            playlist.getName();
                    uri   = null;
                    if (playlist.isSearchResult()) {
                        if (playlist.getTracks().get(0) instanceof YoutubeAudioTrack) {
                            uri = "https://www.youtube.com/results?search_query=%s"
                                    .formatted(title.replaceFirst(" ", "+"));
                        }
                    }
                    firstLine = "**Length**: %s tracks".formatted(playlist.getTracks().size());
                    duration  = playlist.getTracks().stream().mapToLong(AudioTrack::getDuration).sum();
                }

                String durationStr = duration == -1 ?
                        "Livestream" :
                        DateUtils.formatDateFromMillis(duration, "%2$d:%3$02d:%4$02d");

                EmbedBuilder builder = new EmbedBuilder()
                        .setTitle("Playing `%s`".formatted(title), uri)
                        .appendDescription(firstLine)
                        .appendDescription("\n**Duration**: %s".formatted(durationStr))
                        .addField("Bound", "**Voice channel**: %s".formatted(voiceChannel.getAsMention()), true);

                if (item instanceof AudioPlaylist playlist) {
                    StringBuilder nextFive = new StringBuilder();
                    for (int i = 0; i < playlist.getTracks().size(); i++) {
                        if (i > 5) {
                            break;
                        }
                        AudioTrack     audioTrack = playlist.getTracks().get(i);
                        AudioTrackInfo info       = audioTrack.getInfo();
                        nextFive.append("\n[%s | %s](%s)".formatted(info.title, info.author, info.uri));
                    }
                    builder.addField("Next 5 tracks", nextFive.substring(1), false);
                }

                return builder;
            }

        }
    }

    @SuppressWarnings("DuplicatedCode")
    private class Skip extends SlashCommand {

        public Skip() {
            this.name = "skip"; // Will be used as the sub command.
            this.help = "Skip a music";

            this.options = List.of(
                    new OptionData(OptionType.INTEGER, "jump", "Jump `x` tracks"),
                    new OptionData(OptionType.BOOLEAN, "force", "Force track skip")
            );
        }

        @Override
        public void execute(SlashCommandEvent event) {
            long jump = event.getOption("jump") != null ?
                    Objects.requireNonNull(event.getOption("jump")).getAsLong() :
                    1;

            Guild  guild  = event.getGuild();
            Member member = event.getMember();
            if (guild == null || member == null) {
                return;
            }
            GuildVoiceState voiceState   = member.getVoiceState();
            VoiceChannel    voiceChannel = voiceState != null ? voiceState.getChannel() : null;
            if (!guild.getAudioManager().isConnected() && voiceChannel != null) {
                guild.getAudioManager().openAudioConnection(voiceState.getChannel());
            }
            if (voiceChannel == null) {
                return;
            }

            GuildMusicManager player = musicManager.getGuildAudioPlayer(guild);
            player.wantToSkip.add(event.getMember());
            AtomicLong needForSkip = new AtomicLong(voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2);
            if (player.wantToSkip.size() >= needForSkip.get() ||
                (event.getOption("force") != null && Objects.requireNonNull(event.getOption("force")).getAsBoolean() &&
                 hasPermDJ(member, voiceChannel))) {
                for (int i = 0; i < jump; i++) {
                    musicManager.skip(guild);
                }
                event.reply("Skipping `%s` tracks".formatted(jump)).complete();
                player.wantToSkip.clear();
            } else {
                InteractionHook hook = event.reply(("`%s/%s needed for skipping` want to skip %s song," +
                                                    " click one button below for vote").formatted(
                                                    player.wantToSkip.size(),
                                                    needForSkip.get(),
                                                    jump))
                                            .addActionRow(Button.secondary("skip", "Skip song"))
                                            .complete();

                wait(jump, guild, voiceChannel, player, needForSkip, hook);
            }
        }

        private void wait(
                long jump, Guild guild, VoiceChannel voiceChannel, GuildMusicManager player, AtomicLong needForSkip,
                InteractionHook hook
        ) {
            AudioTrack playingTrack = player.player.getPlayingTrack();
            eventWaiter.waitForEvent(ButtonClickEvent.class,
                                     bce -> bce.getMessageIdLong() ==
                                            hook.retrieveOriginal().complete().getIdLong() &&
                                            !player.wantToSkip.contains(bce.getMember()),
                                     bce -> {
                                         needForSkip.set(voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2);
                                         player.wantToSkip.add(bce.getMember());

                                         if (player.wantToSkip.size() >= needForSkip.get()) {
                                             for (int i = 0; i < jump; i++) {
                                                 musicManager.skip(guild);
                                             }
                                             bce.editMessage("Skipping `%s` tracks".formatted(jump))
                                                .setActionRow(bce.getButton().asDisabled())
                                                .complete();
                                             player.wantToSkip.clear();
                                         } else {
                                             bce.editMessage(("`%s/%s needed for skipping` want to skip %s song," +
                                                              " click one button below for vote").formatted(
                                                     player.wantToSkip.size(),
                                                     needForSkip.get(),
                                                     jump)).complete();
                                             wait(jump, guild, voiceChannel, player, needForSkip, hook);
                                         }
                                     },
                                     playingTrack.getDuration() - playingTrack.getPosition(),
                                     TimeUnit.MILLISECONDS,
                                     () -> hook.editOriginal("~~%s~~\nTrack end, vote cancelled".formatted(
                                                       hook.retrieveOriginal().complete().getContentRaw()))
                                               .setActionRow(Button.secondary("skip", "Skip song").asDisabled())
                                               .queue(unused -> player.wantToSkip.clear(),
                                                      new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE)));
        }
    }

    private boolean hasPermDJ(Member member, VoiceChannel voiceChannel) {
        return member.getRoles()
                     .stream()
                     .map(Role::getName)
                     .anyMatch(name -> name.equalsIgnoreCase("DJ")) ||
               member.hasPermission(Permission.MANAGE_CHANNEL) ||
               (voiceChannel.getPermissionOverride(member) != null &&
                Objects.requireNonNull(voiceChannel.getPermissionOverride(member)).getAllowed().contains(
                        Permission.MANAGE_CHANNEL)) ||
               member.getRoles()
                     .stream()
                     .map(voiceChannel::getPermissionOverride)
                     .anyMatch(perm -> perm != null && perm.getAllowed().contains(Permission.MANAGE_CHANNEL));
    }
}
