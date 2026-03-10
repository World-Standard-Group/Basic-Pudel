package group.worldstandard.pudel.plugin.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.QueryBuilder;
import group.worldstandard.pudel.plugin.entity.HistoryEntry;
import group.worldstandard.pudel.plugin.entity.QueueEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Manages track scheduling, queue persistence, loop & shuffle modes.
 */
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer player;
    private final long guildId;
    private final Dependencies deps;

    public int loopMode = 0;    // 0=Off, 1=Queue, 2=Track
    public boolean shuffle = false;

    /**
     * Bundles external dependencies needed by the scheduler.
     */
    public record Dependencies(
            PluginContext context,
            AudioPlayerManager playerManager,
            PluginRepository<QueueEntry> queueRepo,
            PluginRepository<HistoryEntry> historyRepo
    ) {}

    public TrackScheduler(AudioPlayer player, long guildId, Dependencies deps) {
        this.player = player;
        this.guildId = guildId;
        this.deps = deps;
    }

    public void queue(AudioTrack track, long userId) {
        try {
            QueueEntry entry = new QueueEntry();
            entry.setGuildId(guildId);
            entry.setUserId(userId);
            entry.setStatus("QUEUE");
            entry.setTitle(track.getInfo().title);
            entry.setTrackBlob(encodeTrack(track));
            entry.setIsLooped(false);
            deps.queueRepo().save(entry);

            if (player.getPlayingTrack() == null) {
                nextTrack();
            }
        } catch (IOException e) {
            deps.context().log("error", "Failed to encode track: " + e.getMessage());
        }
    }

    public void nextTrack() {
        // 1. Move CURRENT -> PLAYED
        List<QueueEntry> active = deps.queueRepo().query()
                .where("guild_id", guildId)
                .where("status", "CURRENT")
                .list();

        for (QueueEntry e : active) {
            e.setStatus("PLAYED");
            deps.queueRepo().save(e);

            if (e.getIsLooped() == null || !e.getIsLooped()) {
                try {
                    AudioTrack infoTrack = decodeTrack(e.getTrackBlob());
                    HistoryEntry hist = new HistoryEntry();
                    hist.setGuildId(guildId);
                    hist.setUserId(e.getUserId());
                    hist.setTrackTitle(e.getTitle());
                    hist.setTrackUrl(infoTrack.getInfo().uri);
                    hist.setPlayedAt(Instant.now().getEpochSecond());
                    deps.historyRepo().save(hist);
                } catch (IOException ex) {
                    deps.context().log("error", "History save failed: " + ex.getMessage());
                }
            }
        }

        // 2. Fetch next
        QueueEntry nextEntry = null;
        QueryBuilder<QueueEntry> query = deps.queueRepo().query()
                .where("guild_id", guildId)
                .where("status", "QUEUE");

        if (shuffle) {
            List<QueueEntry> candidates = query.list();
            if (!candidates.isEmpty()) {
                nextEntry = candidates.get(new Random().nextInt(candidates.size()));
            }
        } else {
            List<QueueEntry> list = query.limit(1).list();
            if (!list.isEmpty()) nextEntry = list.getFirst();
        }

        // 3. Loop Queue: recycle PLAYED -> QUEUE
        if (nextEntry == null && loopMode == 1) {
            List<QueueEntry> played = deps.queueRepo().query()
                    .where("guild_id", guildId)
                    .where("status", "PLAYED")
                    .list();

            if (!played.isEmpty()) {
                for (QueueEntry e : played) {
                    e.setStatus("QUEUE");
                    e.setIsLooped(true);
                    deps.queueRepo().save(e);
                }
                nextTrack();
                return;
            }
        }

        // 4. Play
        if (nextEntry != null) {
            try {
                AudioTrack track = decodeTrack(nextEntry.getTrackBlob());
                nextEntry.setStatus("CURRENT");
                deps.queueRepo().save(nextEntry);
                track.setUserData(nextEntry.getId());
                player.startTrack(track, false);
            } catch (IOException e) {
                nextEntry.setStatus("ERROR");
                deps.queueRepo().save(nextEntry);
                nextTrack();
            }
        } else {
            player.stopTrack();
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason reason) {
        if (reason.mayStartNext) {
            if (loopMode == 2) {
                AudioTrack clone = track.makeClone();
                clone.setUserData(track.getUserData());
                player.startTrack(clone, false);
            } else {
                nextTrack();
            }
        }
    }

    public void cycleLoopMode() { loopMode = (loopMode + 1) % 3; }
    public void toggleShuffle() { shuffle = !shuffle; }
    public void clearQueue() { deps.queueRepo().deleteBy("guild_id", guildId); }

    // ==================== TRACK ENCODING ====================

    public String encodeTrack(AudioTrack track) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        deps.playerManager().encodeTrack(new MessageOutput(output), track);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    public AudioTrack decodeTrack(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        return deps.playerManager().decodeTrack(new MessageInput(input)).decodedTrack;
    }
}

