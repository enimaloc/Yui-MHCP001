package fr.enimaloc.yui.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import fr.enimaloc.yui.commands.MusicCommand;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;

public class MusicManager {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MusicManager() {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        this.musicManagers = new HashMap<>();
    }

    public synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        GuildMusicManager musicManager = musicManagers.get(guild.getIdLong());
        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guild.getIdLong(), musicManager);
        }
        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        return musicManager;
    }

    public void load(Guild guild, String trackUrl, AudioLoadResultHandler handler) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild);

        playerManager.loadItemOrdered(musicManager, trackUrl, handler);
    }

    public void play(Guild guild, AudioTrack track) {
        getGuildAudioPlayer(guild).scheduler.queue(track);
    }

    public void skip(Guild guild) {
        getGuildAudioPlayer(guild).scheduler.nextTrack();
    }

    public void destroy(Guild guild) {
        GuildMusicManager manager = getGuildAudioPlayer(guild);
        manager.player.destroy();
        manager.scheduler.queue().clear();
        manager.wantToSkip.clear();
        manager.wantToStop.clear();
        if (manager.message != null) {
            manager.message.unpin().complete();
            manager.message.editMessageComponents(MusicCommand.disableButtons(manager.message.getActionRows()))
                           .complete();
            manager.message = null;
        }
        //noinspection SuspiciousMethodCalls
        musicManagers.remove(guild);
    }
}
