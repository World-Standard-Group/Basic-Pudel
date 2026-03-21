package group.worldstandard.pudel.plugin.view;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.audio.GuildMusicManager;
import group.worldstandard.pudel.plugin.entity.HistoryEntry;
import group.worldstandard.pudel.plugin.entity.QueueEntry;
import group.worldstandard.pudel.plugin.session.MusicSession;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Components v2 views for the Music Box plugin.
 */
public class MusicViewBuilder {

    private static final int PAGE_SIZE = 10;
    private static final Color ACCENT_PLAYING = new Color(0x00D4AA);
    private static final Color ACCENT_IDLE = new Color(0x2B2D31);
    private static final Color ACCENT_QUEUE = new Color(0xFFA500);
    private static final Color ACCENT_HISTORY = new Color(0x808080);

    private final String btnPrefix;
    private final String menuPrefix;
    private final PluginRepository<QueueEntry> queueRepo;
    private final PluginRepository<HistoryEntry> historyRepo;

    public MusicViewBuilder(String btnPrefix, String menuPrefix,
                            PluginRepository<QueueEntry> queueRepo,
                            PluginRepository<HistoryEntry> historyRepo) {
        this.btnPrefix = btnPrefix;
        this.menuPrefix = menuPrefix;
        this.queueRepo = queueRepo;
        this.historyRepo = historyRepo;
    }

    public Container buildMainView(GuildMusicManager mgr, MusicSession session) {
        AudioTrack current = mgr.player.getPlayingTrack();
        List<ContainerChildComponent> children = new ArrayList<>();

        if (current != null) {
            String titleText = "### 🎵 [" + current.getInfo().title + "](" + current.getInfo().uri + ")";

            if (current.getInfo().artworkUrl != null && !current.getInfo().artworkUrl.isEmpty()) {
                children.add(Section.of(
                        Thumbnail.fromUrl(current.getInfo().artworkUrl),
                        TextDisplay.of(titleText),
                        TextDisplay.of("-# " + current.getInfo().author)
                ));
            } else {
                children.add(TextDisplay.of(titleText));
                children.add(TextDisplay.of("-# " + current.getInfo().author));
            }

            children.add(Separator.create(false, Separator.Spacing.SMALL));

            String loopIcon = switch (mgr.scheduler.loopMode) {
                case 1 -> "🔁 Queue";
                case 2 -> "🔂 Track";
                default -> "➡ Off";
            };
            String shuffleIcon = mgr.scheduler.shuffle ? "🔀 On" : "➡ Off";
            String pauseIcon = mgr.player.isPaused() ? "⏸ Paused" : "▶ Playing";

            children.add(TextDisplay.of(
                    "⏱ " + formatTime(current.getPosition()) + " / " + formatTime(current.getDuration())
                            + "\u2003\u2003" + pauseIcon
                            + "\n🔁 Loop: " + loopIcon + "\u2003\u2003🔀 Shuffle: " + shuffleIcon
            ));

            children.add(TextDisplay.of("-# Last Action: %s".formatted(session.lastAction)));
            children.add(Separator.create(true, Separator.Spacing.SMALL));

            boolean isPaused = mgr.player.isPaused();
            children.add(ActionRow.of(
                    Button.primary(btnPrefix + "pause", isPaused ? "▶ Resume" : "⏸ Pause"),
                    Button.secondary(btnPrefix + "skip", "⏭ Skip"),
                    Button.secondary(btnPrefix + "loop", "🔁 Loop"),
                    Button.secondary(btnPrefix + "shuffle", "🔀 Shuffle")
            ));
        } else {
            children.add(TextDisplay.of("# 🎵 Music Box"));
            children.add(Separator.create(false, Separator.Spacing.SMALL));
            children.add(TextDisplay.of("_No track playing. Queue a song to get started!_"));
            children.add(Separator.create(true, Separator.Spacing.SMALL));
        }

        children.add(ActionRow.of(
                Button.success(btnPrefix + "queuesong", "🎵 Queue Song"),
                Button.primary(btnPrefix + "queueview", "📋 Queue"),
                Button.secondary(btnPrefix + "history", "📜 History")
        ));

        Color accent = current != null ? ACCENT_PLAYING : ACCENT_IDLE;
        return Container.of(children).withAccentColor(accent);
    }

    public Container buildQueueView(MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .list();

        int totalItems = queue.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        session.page = Math.min(session.page, totalPages - 1);

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📋 Queue"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (queue.isEmpty()) {
            children.add(TextDisplay.of("_Queue is empty. Use **🎵 Queue Song** to add tracks!_"));
        } else {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                QueueEntry entry = queue.get(i);
                String title = entry.getTitle();
                if (title.length() > 60) title = title.substring(0, 57) + "...";
                sb.append(String.format("`%d.` %s\n", i + 1, title));
            }
            children.add(TextDisplay.of(sb.toString()));
            children.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages
                    + " • " + totalItems + " tracks total"));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));

        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "qprev", "◀").withDisabled(session.page <= 0),
                Button.secondary(btnPrefix + "qnext", "▶").withDisabled(session.page >= totalPages - 1),
                Button.danger(btnPrefix + "remove", "🗑 Remove").withDisabled(queue.isEmpty()),
                Button.secondary(btnPrefix + "reindex", "🔀 Shuffle Queue").withDisabled(queue.isEmpty()),
                Button.danger(btnPrefix + "clearqueue", "🧹 Clear All").withDisabled(queue.isEmpty())
        ));
        children.add(ActionRow.of(
                Button.primary(btnPrefix + "back", "🔙 Back to Player")
        ));

        return Container.of(children).withAccentColor(ACCENT_QUEUE);
    }

    public Container buildHistoryView(MusicSession session) {
        List<HistoryEntry> history = historyRepo.query()
                .where("guild_id", session.guildId)
                .orderByDesc("played_at")
                .list();

        int totalItems = history.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        session.page = Math.min(session.page, totalPages - 1);

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📜 History"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (history.isEmpty()) {
            children.add(TextDisplay.of("_No history yet. Play some music!_"));
        } else {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, totalItems);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                HistoryEntry h = history.get(i);
                String time = h.getPlayedAt().toString();
                String title = h.getTrackTitle();
                if (title.length() > 50) title = title.substring(0, 47) + "...";
                sb.append(String.format("<t:%s:S> [%s](%s)\n", time, title, h.getTrackUrl()));
            }
            children.add(TextDisplay.of(sb.toString()));
            children.add(TextDisplay.of("-# Page " + (session.page + 1) + "/" + totalPages
                    + " • " + totalItems + " entries"));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));

        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "hprev", "◀").withDisabled(session.page <= 0),
                Button.secondary(btnPrefix + "hnext", "▶").withDisabled(session.page >= totalPages - 1),
                Button.primary(btnPrefix + "back", "🔙 Back to Player")
        ));

        return Container.of(children).withAccentColor(ACCENT_HISTORY);
    }

    public Container buildSearchView(MusicSession session, List<AudioTrack> tracks, String searchId) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🔍 Search Results"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            String title = t.getInfo().title;
            if (title.length() > 60) title = title.substring(0, 57) + "...";
            sb.append(String.format("`%d.` **%s** — %s (%s)\n",
                    i + 1, title, t.getInfo().author, formatTime(t.getDuration())));
        }
        children.add(TextDisplay.of(sb.toString()));

        StringSelectMenu.Builder menu = StringSelectMenu.create(menuPrefix + "select:" + searchId)
                .setPlaceholder("Select a track to queue...");

        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            String label = (i + 1) + ". " + t.getInfo().title;
            if (label.length() > 100) label = label.substring(0, 97) + "...";
            menu.addOption(label, String.valueOf(i), t.getInfo().author);
        }

        children.add(ActionRow.of(menu.build()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.primary(btnPrefix + "back", "🔙 Cancel")
        ));

        return Container.of(children).withAccentColor(ACCENT_PLAYING);
    }

    public Container buildRemoveView(MusicSession session, List<QueueEntry> queue) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🗑 Remove from Queue"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(TextDisplay.of("Select a track to remove:"));

        StringSelectMenu.Builder menu = StringSelectMenu.create(menuPrefix + "remove")
                .setPlaceholder("Select a track to remove...");

        for (int i = 0; i < Math.min(25, queue.size()); i++) {
            QueueEntry entry = queue.get(i);
            String label = (i + 1) + ". " + entry.getTitle();
            if (label.length() > 100) label = label.substring(0, 97) + "...";
            menu.addOption(label, String.valueOf(entry.getId()));
        }

        children.add(ActionRow.of(menu.build()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.primary(btnPrefix + "queueview", "🔙 Back to Queue")
        ));

        return Container.of(children).withAccentColor(ACCENT_QUEUE);
    }

    // ==================== UTILITY ====================

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%02d:%02d", minutes, seconds);
    }
}

