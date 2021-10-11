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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
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
                new Skip(),
                new Stop(),
                new Queue(),
                new Pause(),
                new NowPlaying()
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
            Guild  guild  = event.getGuild();
            if (member == null ||
                member.getVoiceState() == null ||
                guild == null) {
                return;
            }
            GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);

            String track = Objects.requireNonNull(event.getOption("query")).getAsString();
            try {
                new URL(track);
            } catch (MalformedURLException e) {
                track = ObjectUtils.getOr(() -> Objects.requireNonNull(event.getOption("service")).getAsString(),
                                          "ytsearch") + ":" + track;
            }

            if (!guild.getAudioManager().isConnected() &&
                !member.getVoiceState().inVoiceChannel()) {
                SelectionMenu.Builder musicChannelMenu = SelectionMenu.create("musicChannel").setMaxValues(1);
                for (VoiceChannel voiceChannel : guild.getVoiceChannels()) {
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
                                             manager.voiceChannel = guild.getVoiceChannelById(channelId);
                                             guild
                                                     .getAudioManager()
                                                     .openAudioConnection(manager.voiceChannel);
                                             musicManager.load(guild, finalTrack,
                                                               new ResultHandler(event, sme));
                                         });
                return;
            } else if (!guild.getAudioManager().isConnected() && member.getVoiceState().inVoiceChannel()) {
                manager.voiceChannel = member.getVoiceState().getChannel();
                guild.getAudioManager().openAudioConnection(manager.voiceChannel);
            }
            musicManager.load(guild, track, new ResultHandler(event));
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
                GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);

                Objects.requireNonNull(getReplyAction())
                       .addEmbeds(prepareEmbed(track, manager.voiceChannel).build())
                       .setEphemeral(true)
                       .complete();
                musicManager.play(event.getGuild(), track);

                manager.updateMessage();
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

                GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);

                Objects.requireNonNull(getReplyAction())
                       .addEmbeds(prepareEmbed(playlist, manager.voiceChannel).build())
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

                manager.updateMessage();
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
            Member member = event.getMember();
            Guild  guild  = event.getGuild();
            if (member == null || guild == null) {
                return;
            }

            skip(eventWaiter,
                 musicManager,
                 event,
                 (event.getOption("force") != null && Objects.requireNonNull(event.getOption("force")).getAsBoolean() &&
                  hasPermDJ(member, musicManager.getGuildAudioPlayer(guild).voiceChannel)),
                 jump);
        }

        private static void skip(EventWaiter eventWaiter, MusicManager musicManager, GenericInteractionCreateEvent event, boolean force, long jump) {
            Guild  guild  = event.getGuild();
            Member member = event.getMember();
            if (guild == null || member == null) {
                return;
            }
            GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);
            if (manager.voiceChannel == null) {
                return;
            }

            manager.wantToSkip.add(event.getMember());
            AtomicLong needForSkip = new AtomicLong(
                    manager.voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2);
            if (manager.wantToSkip.size() >= needForSkip.get() || force) {
                for (int i = 0; i < jump; i++) {
                    musicManager.skip(guild);
                }
                InteractionHook hook = event.reply("Skipping `%s` tracks".formatted(jump)).complete();
                hook.deleteOriginal().completeAfter(2, TimeUnit.SECONDS);

                manager.wantToSkip.clear();
                manager.updateMessage();
            } else {
                InteractionHook hook = event.reply(("`%s/%s needed for skipping` want to skip %s song," +
                                                    " click one button below for vote").formatted(
                                                    manager.wantToSkip.size(),
                                                    needForSkip.get(),
                                                    jump))
                                            .addActionRow(Button.secondary("skip", "Skip song"))
                                            .complete();
                poll(eventWaiter,
                     hook,
                     (actual, needed) -> "`" + actual + "/" + needed + " needed for skipping` want to skip " + jump +
                                         " song, click one button below for vote",
                     manager.wantToSkip,
                     manager.voiceChannel,
                     unused -> Math.toIntExact(
                             manager.voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2),
                     bce -> {
                         for (int i = 0; i < jump; i++) {
                             musicManager.skip(guild);
                         }
                         bce.editMessage("Skipping `%s` tracks".formatted(jump))
                            .setActionRow(bce.getButton().asDisabled())
                            .complete();
                         hook.deleteOriginal().completeAfter(2, TimeUnit.SECONDS);

                         manager.wantToSkip.clear();
                         manager.updateMessage();
                     },
                     manager.player.getPlayingTrack().getDuration() - manager.player.getPlayingTrack().getPosition(),
                     TimeUnit.MILLISECONDS);
            }
        }
    }

    private class Stop extends SlashCommand {

        public Stop() {
            this.name = "stop"; // Will be used as the sub command.
            this.help = "Stop music";

            this.options = List.of(
                    new OptionData(OptionType.BOOLEAN, "force", "Force track skip")
            );
        }

        @SuppressWarnings("DuplicatedCode")
        @Override
        public void execute(SlashCommandEvent event) {
            Guild  guild  = event.getGuild();
            Member member = event.getMember();
            if (guild == null || member == null) {
                return;
            }
            GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);
            if (manager.voiceChannel == null) {
                return;
            }

            stop(eventWaiter,
                 musicManager,
                 event,
                 event.getOption("force") != null && Objects.requireNonNull(event.getOption("force")).getAsBoolean() &&
                 hasPermDJ(member, manager.voiceChannel),
                 guild,
                 member,
                 manager);
        }

        private static void stop(
                EventWaiter eventWaiter, MusicManager musicManager,
                GenericInteractionCreateEvent event, boolean force, Guild guild, Member member,
                GuildMusicManager manager
        ) {
            manager.wantToStop.add(event.getMember());
            AtomicLong needForStop = new AtomicLong(
                    manager.voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2);
            if (manager.wantToStop.size() >= needForStop.get() || force) {
                musicManager.destroy(guild);
                guild.getAudioManager().closeAudioConnection();
                event.reply("Player stopped :wave:").complete();
            } else {
                InteractionHook hook = event.reply(("`%s/%s needed for stopping` want to stop the music," +
                                                    " click one button below for vote").formatted(
                                                    manager.wantToStop.size(),
                                                    needForStop.get()))
                                            .addActionRow(Button.danger("stop", "Stop song"))
                                            .complete();
                poll(eventWaiter, hook,
                     (actual, needed) -> "`" + actual + "/" + needed +
                                         " needed for skipping` want to stop the music, click one button below for vote",
                     manager.wantToStop,
                     manager.voiceChannel,
                     unused -> Math.toIntExact(
                             manager.voiceChannel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() / 2),
                     bce -> {
                         musicManager.destroy(guild);
                         guild.getAudioManager().closeAudioConnection();
                         bce.editMessage("Player stopped :wave:")
                            .setActionRows(disableButtons(bce.getMessage().getActionRows()))
                            .complete();
                     },
                     5,
                     TimeUnit.MINUTES);
                     /*manager.scheduler.queue().stream().mapToLong(AudioTrack::getDuration).sum() -
                     manager.player.getPlayingTrack().getPosition(),
                     TimeUnit.MILLISECONDS);*/
            }
        }
    }

    private class Queue extends SlashCommand {

        public Queue() {
            this.name           = "queue";
            this.help           = "Track queue related command";
            this.defaultEnabled = false;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private class Pause extends SlashCommand {

        public Pause() {
            this.name = "pause";
            this.help = "(Un)pause music";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            Member member = event.getMember();
            Guild  guild  = event.getGuild();
            if (member == null ||
                member.getVoiceState() == null ||
                guild == null) {
                return;
            }
            GuildMusicManager manager = musicManager.getGuildAudioPlayer(guild);

            pause(musicManager, event, guild, manager);
        }

        public static void pause(MusicManager musicManager, GenericInteractionCreateEvent event, Guild guild, GuildMusicManager manager) {
            boolean paused = manager.player.isPaused();
            musicManager.getGuildAudioPlayer(guild).player.setPaused(!paused);
            InteractionHook hook = event.reply((paused ? "Unp" : "P") + "aused the music").complete();
            hook.deleteOriginal().completeAfter(2, TimeUnit.SECONDS);

            manager.updateMessage();
        }

    }

    private class NowPlaying extends SlashCommand {
        public NowPlaying() {
            this.name = "now-playing";
            this.help = "Display what song is playing";

            this.options = List.of(
                    new OptionData(OptionType.BOOLEAN, "favorite",
                                   "Set as favorite message, this permit to make action with this and live update")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            GuildMusicManager manager = musicManager.getGuildAudioPlayer(
                    Objects.requireNonNull(event.getGuild()));
            InteractionHook hook = event.replyEmbeds(manager.buildNowPlaying().build())
                                        .addActionRow(Button.primary("favorite", "Favorite"),
                                                      Button.primary("skip", "Skip"),
                                                      Button.primary("pause", "Pause"),
                                                      Button.danger("stop", "Stop"))
                                        .complete();

            if (event.getOption("favorite") != null) {
                favorite(manager, hook, null);
            }

            repeat(ButtonClickEvent.class,
                   bce -> bce.getMessageIdLong() == hook.retrieveOriginal().complete().getIdLong() &&
                          manager.voiceChannel.getMembers().contains(bce.getMember()),
                   bce -> {
                       Button button = bce.getButton();
                       if (button == null || button.getId() == null) {
                           return;
                       }
                       switch (button.getId()) {
                           case "favorite" -> favorite(manager, hook, bce);
                           case "skip" -> skip(manager, hook, bce);
                           case "pause" -> pause(manager, hook, bce);
                           case "stop" -> stop(manager, hook, bce);
                       }
                   },
                   bce -> manager.player.getPlayingTrack() != null);
        }

        private void favorite(GuildMusicManager manager, InteractionHook hook, ButtonClickEvent bce) {
            boolean unfavorite = manager.message != null &&
                                 manager.message.getIdLong() == hook.retrieveOriginal().complete().getIdLong();
            if (!unfavorite) {
                if (manager.message != null) {
                    manager.message.unpin().complete();
                }
                manager.message = hook.retrieveOriginal().complete();
                manager.message.pin().complete();
                if (bce != null && !bce.isAcknowledged() && bce.getButton() != null) {
                    bce.editButton(bce.getButton().withLabel("Unfavorite")).complete();
                }
            } else {
                manager.message.unpin().complete();
                manager.message = null;
                if (bce != null && !bce.isAcknowledged() && bce.getButton() != null) {
                    bce.editButton(bce.getButton().withLabel("Favorite")).complete();
                }
            }
        }

        private void skip(GuildMusicManager manager, InteractionHook hook, ButtonClickEvent bce) {
            MusicCommand.Skip.skip(eventWaiter, musicManager, bce, false, 1);
        }

        private void pause(GuildMusicManager manager, InteractionHook hook, ButtonClickEvent bce) {
            MusicCommand.Pause.pause(musicManager, bce, hook.getInteraction().getGuild(), manager);
        }

        private void stop(GuildMusicManager manager, InteractionHook hook, ButtonClickEvent bce) {
            MusicCommand.Stop.stop(eventWaiter,
                                   musicManager,
                                   bce,
                                   false,
                                   hook.getInteraction().getGuild(),
                                   bce.getMember(),
                                   manager);
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

    private static void poll(
            EventWaiter eventWaiter,
            InteractionHook hook,
            BiFunction<Integer, Integer, String> originalMessage,
            List<Member> voted,
            VoiceChannel voiceChannel,
            Function<Void, Integer> needTo,
            Consumer<ButtonClickEvent> onVoteEnd,
            long timeout,
            TimeUnit unit
    ) {
        poll(eventWaiter, hook, originalMessage, bce -> true, voted, voiceChannel, needTo, onVoteEnd, timeout, unit);
    }

    private static void poll(
            EventWaiter eventWaiter,
            InteractionHook hook,
            BiFunction<Integer, Integer, String> originalMessage,
            Predicate<ButtonClickEvent> condition,
            List<Member> voted,
            VoiceChannel voiceChannel,
            Function<Void, Integer> needTo,
            Consumer<ButtonClickEvent> onVoteEnd,
            long timeout,
            TimeUnit unit
    ) {
        eventWaiter.waitForEvent(ButtonClickEvent.class,
                                 condition.and(bce -> bce.getMessageIdLong() ==
                                                      hook.retrieveOriginal().complete().getIdLong() &&
                                                      !voted.contains(bce.getMember()) &&
                                                      voiceChannel.getMembers().contains(bce.getMember())),
                                 bce -> {
                                     int apply = needTo.apply(null);
                                     if (voted.size() >= apply) {
                                         onVoteEnd.accept(bce);
                                     } else {
                                         bce.editMessage(originalMessage.apply(voted.size(), apply)).complete();
                                         poll(eventWaiter,
                                              hook,
                                              originalMessage,
                                              condition,
                                              voted,
                                              voiceChannel,
                                              needTo,
                                              onVoteEnd,
                                              timeout,
                                              unit
                                         );
                                     }
                                 },
                                 timeout,
                                 unit,
                                 () -> {
                                     Message message = hook.retrieveOriginal().complete();
                                     hook.editOriginal("~~%s~~\nVote ended".formatted(message.getContentRaw()))
                                         .setActionRows(disableButtons(message.getActionRows()))
                                         .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                                     voted.clear();
                                 });
    }

    private <T extends Event> void repeat(
            Class<T> eventClazz,
            Predicate<T> condition,
            Consumer<T> action,
            Function<T, Boolean> conditionToContinue
    ) {
        repeat(eventClazz, condition, action, conditionToContinue, -1, null, null);
    }

    private <T extends Event> void repeat(
            Class<T> classType,
            Predicate<T> condition,
            Consumer<T> action,
            Function<T, Boolean> conditionToContinue,
            long timeout,
            TimeUnit unit,
            Runnable timeoutAction
    ) {
        eventWaiter.waitForEvent(classType, condition, action.andThen(event -> {
                                     if (conditionToContinue.apply(event)) {
                                         repeat(classType, condition, action, conditionToContinue, timeout, unit, timeoutAction);
                                     }
                                 }),
                                 timeout,
                                 unit,
                                 timeoutAction);
    }

    public static Collection<ActionRow> disableButtons(Collection<ActionRow> actionRows) {
        List<ActionRow> rows = new ArrayList<>();
        for (ActionRow row : actionRows) {
            List<Component> components = new ArrayList<>();
            for (Component component : row.getComponents()) {
                if (component instanceof Button button) {
                    component = button.asDisabled();
                }
                components.add(component);
            }
            rows.add(ActionRow.of(components));
        }
        return rows;
    }
}
