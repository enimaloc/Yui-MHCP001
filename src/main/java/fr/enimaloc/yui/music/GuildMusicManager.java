package fr.enimaloc.yui.music;


import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import fr.enimaloc.enutils.classes.DateUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * Holder for both the player and a track scheduler for one guild.
 */
public class GuildMusicManager {
    /**
     * Audio player for the guild.
     */
    public final AudioPlayer    player;
    /**
     * Track scheduler for the player.
     */
    public final YTrackScheduler scheduler;

    /**
     * Skip vote
     */
    public List<Member> wantToSkip = new ArrayList<>();

    /**
     * Stop vote
     */
    public List<Member> wantToStop = new ArrayList<>();

    /**
     * Bounded voice channel
     */
    public VoiceChannel voiceChannel;

    /**
     * favorite message
     */
    public Message message;

    /**
     * Creates a player and a track scheduler.
     *
     * @param manager Audio player manager to use for creating the player.
     */
    public GuildMusicManager(AudioPlayerManager manager) {
        this.player    = manager.createPlayer();
        this.scheduler = new YTrackScheduler();
        player.addListener(scheduler);
    }

    /**
     * @return Wrapper around AudioPlayer to use it as an AudioSendHandler.
     */
    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }

    public void updateMessage() {
        if (message != null) {
            message.editMessageEmbeds(buildNowPlaying().build())
                   .queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
        }
    }

    public EmbedBuilder buildNowPlaying() {
        AudioTrack                item       = player.getPlayingTrack();
        if (item == null) {
            return new EmbedBuilder().setTitle("No song");
        }
        BlockingQueue<AudioTrack> tracksList = scheduler.queue();

        AudioTrackInfo itemInfo = item.getInfo();
        String         title    = itemInfo.title;
        String         uri      = itemInfo.uri;
        String         author   = itemInfo.author;

        String durationStr = itemInfo.isStream ?
                "Livestream" :
                DateUtils.formatDateFromMillis(itemInfo.length, "%2$d:%3$02d:%4$02d");

        String totalDurationStr = itemInfo.isStream ?
                "Livestream" :
                DateUtils.formatDateFromMillis(tracksList.stream().mapToLong(AudioTrack::getDuration).sum()+itemInfo.length,
                                               "%2$d:%3$02d:%4$02d");

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Playing `%s`".formatted(title), uri)
                .appendDescription("**Author**: %s".formatted(author))
                .appendDescription("\n**Duration**: %s".formatted(durationStr))
                .appendDescription("\n**Total Duration**: %s".formatted(totalDurationStr))
                .addField("Bound", "**Voice channel**: %s".formatted(voiceChannel.getAsMention()), true);

        StringBuilder next = new StringBuilder();
        int           i    = 0;
        for (; i < tracksList.size(); i++) {
            if (i > 5) {
                break;
            }
            AudioTrack     audioTrack = tracksList.stream().toList().get(i);
            AudioTrackInfo info       = audioTrack.getInfo();
            next.append("\n[%s | %s](%s)".formatted(info.title, info.author, info.uri));
        }
        if (!next.isEmpty()) {
            builder.addField("Next %s tracks".formatted(Math.min(i, 5)), next.substring(1), false);
        }

        return builder;
    }

    public class YTrackScheduler extends AudioEventAdapter {
        private final BlockingQueue<AudioTrack> queue;

        public YTrackScheduler() {
            this.queue   = new LinkedBlockingQueue<>();
        }

        /**
         * Add the next track to queue or play right away if nothing is in the queue.
         *
         * @param track The track to play or add to queue.
         */
        public void queue(AudioTrack track) {
            // Calling startTrack with the noInterrupt set to true will start the track only if nothing is currently playing. If
            // something is playing, it returns false and does nothing. In that case the player was already playing so this
            // track goes to the queue instead.
            if (!player.startTrack(track, true)) {
                queue.offer(track);
            }
        }

        /**
         * Start the next track, stopping the current one if it is playing.
         */
        public void nextTrack() {
            // Start the next track, regardless of if something is already playing or not. In case queue was empty, we are
            // giving null to startTrack, which is a valid argument and will simply stop the player.
            AudioTrack nextTrack = queue.poll();
            if (nextTrack != null) {
                player.startTrack(nextTrack, false);
            } else {
                voiceChannel.getGuild().getAudioManager().closeAudioConnection();
            }
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            // Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
            if (endReason.mayStartNext) {
                nextTrack();
            }
        }

        public BlockingQueue<AudioTrack> queue() {
            return queue;
        }

        @Override
        public void onTrackStart(AudioPlayer player, AudioTrack track) {
            updateMessage();
            super.onTrackStart(player, track);
        }
    }
}