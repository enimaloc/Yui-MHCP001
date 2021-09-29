package fr.enimaloc.yui.music;


import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;

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
    public final TrackScheduler scheduler;

    /**
     * Creates a player and a track scheduler.
     *
     * @param manager Audio player manager to use for creating the player.
     */
    public GuildMusicManager(AudioPlayerManager manager) {
        player    = manager.createPlayer();
        scheduler = new TrackScheduler(player) {
            @Override
            public void onTrackException(
                    AudioPlayer player, AudioTrack track, FriendlyException exception
            ) {
                SentryEvent sentryEvent = new SentryEvent(exception);
                sentryEvent.setModule("category", "music");
                sentryEvent.setTag("trackUrl", track.getInfo().uri);
                sentryEvent.setLevel(SentryLevel.ERROR);
                Message message = new Message();
                message.setMessage(exception.getLocalizedMessage());
                sentryEvent.setMessage(message);
                Sentry.captureEvent(sentryEvent);
            }
        };
        player.addListener(scheduler);
    }

    /**
     * @return Wrapper around AudioPlayer to use it as an AudioSendHandler.
     */
    public AudioPlayerSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }
}