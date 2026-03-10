package group.worldstandard.pudel.plugin.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Holds the {@link AudioPlayer} and {@link TrackScheduler} for a single guild.
 */
public class GuildMusicManager {

    public final AudioPlayer player;
    public final TrackScheduler scheduler;

    public GuildMusicManager(AudioPlayerManager manager, long guildId,
                             TrackScheduler.Dependencies deps) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player, guildId, deps);
        this.player.addListener(scheduler);
    }
}

