/*
 * Basic Pudel - Music Plugin Commands
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.database.*;
import group.worldstandard.pudel.api.event.EventHandler;
import group.worldstandard.pudel.plugin.audio.AudioPlayerSendHandler;
import group.worldstandard.pudel.plugin.audio.GuildMusicManager;
import group.worldstandard.pudel.plugin.audio.TrackScheduler;
import group.worldstandard.pudel.plugin.entity.HistoryEntry;
import group.worldstandard.pudel.plugin.entity.QueueEntry;
import group.worldstandard.pudel.plugin.session.MusicSession;
import group.worldstandard.pudel.plugin.session.MusicSession.View;
import group.worldstandard.pudel.plugin.view.MusicViewBuilder;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Unified Music Plugin for Pudel Discord Bot.
 *
 * <p>Delegates audio management to {@link GuildMusicManager} / {@link TrackScheduler},
 * view rendering to {@link MusicViewBuilder}, and session state to {@link MusicSession}.
 *
 * @author Zazalng
 * @since 3.0.0
 */
@Plugin(
        name = "Pudel's Music",
        version = "3.1.0",
        author = "Zazalng",
        description = "Unified Music Box with Components v2"
)
public class PudelMusicPlugin {

    // ==================== CONSTANTS ====================
    private static final String BTN = "music:";
    private static final String MODAL_PREFIX = "music:modal:";
    private static final String MENU_PREFIX = "music:menu:";

    private static final Color ACCENT_PLAYING = new Color(0x00D4AA);
    private static final Color ACCENT_IDLE = new Color(0x2B2D31);

    // ==================== STATE ====================
    private PluginContext context;
    private AudioPlayerManager playerManager;

    private PluginRepository<QueueEntry> queueRepo;
    private PluginRepository<HistoryEntry> historyRepo;

    private final Map<Long, GuildMusicManager> musicManagers = new ConcurrentHashMap<>();
    private final Map<Long, MusicSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, List<AudioTrack>> searchCache = new ConcurrentHashMap<>();

    private MusicViewBuilder viewBuilder;

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        PluginDatabaseManager db = ctx.getDatabaseManager();
        initializeDatabase(db);
        initializeLavaPlayer();
        this.viewBuilder = new MusicViewBuilder(BTN, MENU_PREFIX, queueRepo, historyRepo);
        ctx.log("info", "%s initialized (v%s — Components v2)".formatted(ctx.getInfo().getName(), ctx.getInfo().getVersion()));
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        musicManagers.values().forEach(m -> m.player.destroy());
        playerManager.shutdown();
        return true;
    }

    // ==================== DATABASE ====================

    private void initializeDatabase(PluginDatabaseManager db) {
        TableSchema queueSchema = TableSchema.builder("music_queue")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_blob", ColumnType.TEXT, false)
                .column("status", ColumnType.STRING, 20, false, "'QUEUE'")
                .column("title", ColumnType.STRING, 255, true)
                .column("is_looped", ColumnType.BOOLEAN, false, "false")
                .index("guild_id")
                .build();
        db.createTable(queueSchema);

        TableSchema historySchema = TableSchema.builder("music_history")
                .column("guild_id", ColumnType.BIGINT, false)
                .column("user_id", ColumnType.BIGINT, false)
                .column("track_title", ColumnType.STRING, 255, false)
                .column("track_url", ColumnType.TEXT, false)
                .column("played_at", ColumnType.BIGINT, false)
                .index("guild_id")
                .build();
        db.createTable(historySchema);

        this.queueRepo = db.getRepository("music_queue", QueueEntry.class);
        this.historyRepo = db.getRepository("music_history", HistoryEntry.class);
    }

    // ==================== LAVAPLAYER ====================

    private void initializeLavaPlayer() {
        this.playerManager = new DefaultAudioPlayerManager();

        YoutubeSourceOptions ytk = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectPlaylistIds(true)
                .setAllowDirectVideoIds(true)
                .setRemoteCipher("https://cipher.kikkia.dev/", "", context.getPudel().getUserAgent());

        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(
                ytk,
                new MusicWithThumbnail(),
                new WebWithThumbnail(),
                new MWebWithThumbnail(),
                new WebEmbeddedWithThumbnail(),
                new AndroidMusicWithThumbnail(),
                new AndroidVrWithThumbnail(),
                new IosWithThumbnail(),
                new Tv(),
                new TvHtml5SimplyWithThumbnail()
        );

        AudioSourceManagers.registerRemoteSources(
                this.playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );

        String oauth2Api = "";
        ytSourceManager.useOauth2(oauth2Api, !oauth2Api.isEmpty());
        this.playerManager.registerSourceManager(ytSourceManager);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(name = "music",
            description = "Open the Music Box or directly search & queue a song",
            nsfw = false,
            global = false,
            options = {
                    @CommandOption(
                            name = "search",
                            description = "Search & queue a song directly (URL or search query, auto source)",
                            type = OptionType.STRING
                    )
            },
            integrationTo = {IntegrationType.GUILD_INSTALL},
            integrationContext = {InteractionContextType.GUILD}
    )
    public void onMusic(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        long userId = event.getUser().getIdLong();

        // Clean old session
        MusicSession old = activeSessions.get(userId);
        if (old != null) {
            old.cleanupTemp();
            if (old.message != null) {
                try {
                    old.message.delete().queue();
                } catch (IllegalStateException _) {
                    context.log("warn", "message_id '".concat(old.message.getId()).concat("' may long gone. Skip delete."));
                }
            }
        }

        MusicSession session = new MusicSession(userId, guild.getIdLong());
        activeSessions.put(userId, session);

        GuildMusicManager mgr = getGuildAudioPlayer(guild);

        // Resume from stale queue if nothing is playing
        if (mgr.player.getPlayingTrack() == null) {
            recoverStaleQueue(guild.getIdLong());

            boolean hasQueue = !queueRepo.query()
                    .where("guild_id", guild.getIdLong())
                    .where("status", "QUEUE")
                    .limit(1).list().isEmpty();

            if (hasQueue && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                if (!guild.getAudioManager().isConnected()) {
                    guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
                }
                mgr.scheduler.nextTrack();
            }
        }

        // Direct search query
        var searchOption = event.getOption("search");
        if (searchOption != null) {
            String query = searchOption.getAsString().trim();
            if (!query.isEmpty()) {
                handleDirectSearch(event, session, mgr, guild, member, query);
                return;
            }
        }

        session.lastAction = "Opened Music Box";
        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(viewBuilder.buildMainView(mgr, session))
                        .build()
        ).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(msg -> session.message = msg));
    }

    // ==================== DIRECT SEARCH ====================

    private void handleDirectSearch(SlashCommandInteractionEvent event, MusicSession session,
                                    GuildMusicManager mgr, Guild guild, Member member, String query) {
        if (!guild.getAudioManager().isConnected() && member != null
                && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
            guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
        }

        String searchPrefix = query.startsWith("http") ? "" : "ytsearch:";
        long userId = session.userId;

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("# 🔍 Searching..."),
                                Separator.create(false, Separator.Spacing.SMALL),
                                TextDisplay.of("_Searching for_ `" + query + "` _please wait..._")
                        ).withAccentColor(ACCENT_PLAYING))
                        .build()
        ).setEphemeral(true).queue(hook -> {
            hook.retrieveOriginal().queue(msg -> session.message = msg);

            playerManager.loadItemOrdered(mgr, searchPrefix + query, new AudioLoadResultHandler() {
                @Override public void trackLoaded(AudioTrack track) {
                    mgr.scheduler.queue(track, userId);
                    session.lastAction = "🎵 Queued: " + truncate(track.getInfo().title, 40);
                    updateSessionMessage(session, mgr);
                }

                @Override public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.isSearchResult()) {
                        List<AudioTrack> tracks = playlist.getTracks()
                                .subList(0, Math.min(5, playlist.getTracks().size()));
                        String searchId = UUID.randomUUID().toString();
                        searchCache.put(searchId, tracks);
                        session.view = View.SEARCH;

                        hook.sendMessage(
                                new MessageCreateBuilder()
                                        .useComponentsV2(true)
                                        .setComponents(viewBuilder.buildSearchView(session, tracks, searchId))
                                        .build()
                        ).setEphemeral(true).queue(tempMsg -> {
                            session.tempMessage = tempMsg;
                            session.tempHook = hook;
                        });
                        updateSessionMessage(session, mgr);
                    } else {
                        for (AudioTrack track : playlist.getTracks()) {
                            mgr.scheduler.queue(track, userId);
                        }
                        session.lastAction = "📋 Queued playlist: " + truncate(playlist.getName(), 35) + " (" + playlist.getTracks().size() + " tracks)";
                        updateSessionMessage(session, mgr);
                    }
                }

                @Override public void noMatches() {
                    if (session.message != null) {
                        session.message.editMessage(buildErrorView("# ❌ No Results",
                                "_No matches found for_ `" + query + "`").build()).queue();
                    }
                }

                @Override public void loadFailed(FriendlyException exception) {
                    if (session.message != null) {
                        session.message.editMessage(buildErrorView("# ❌ Load Failed",
                                "_" + exception.getMessage() + "_").build()).queue();
                    }
                }
            });
        });
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BTN)
    public void onButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/music` to open a new Music Box.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) return;

        GuildMusicManager mgr = getGuildAudioPlayer(guild);
        String id = event.getComponentId().substring(BTN.length());

        // Check if this button interaction is from a temp popup message
        boolean isFromTemp = session.tempMessage != null
                && event.getMessage().getIdLong() == session.tempMessage.getIdLong();

        if (isFromTemp) {
            switch (id) {
                case "back" -> {
                    event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
                    session.tempMessage = null;
                    session.tempHook = null;
                    session.view = View.MAIN;
                    return;
                }
                case "queueview" -> {
                    event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
                    session.tempMessage = null;
                    session.tempHook = null;
                    session.view = View.QUEUE;
                    session.page = 0;
                    if (session.message != null) {
                        session.message.editMessage(
                                new MessageEditBuilder().useComponentsV2(true)
                                        .setComponents(viewBuilder.buildQueueView(session)).build()
                        ).queue();
                    }
                    return;
                }
                default -> { event.deferEdit().queue(); return; }
            }
        }

        switch (id) {
            // Playback Controls
            case "pause" -> {
                boolean nowPaused = !mgr.player.isPaused();
                mgr.player.setPaused(nowPaused);
                session.lastAction = nowPaused ? "⏸ Paused playback" : "▶ Resumed playback";
                editToMainView(event, mgr, session);
            }
            case "skip" -> {
                AudioTrack skipped = mgr.player.getPlayingTrack();
                mgr.scheduler.nextTrack();
                session.lastAction = skipped != null
                        ? "⏭ Skipped: " + truncate(skipped.getInfo().title, 40)
                        : "⏭ Skipped";
                editToMainView(event, mgr, session);
            }
            case "loop" -> {
                mgr.scheduler.cycleLoopMode();
                String mode = switch (mgr.scheduler.loopMode) {
                    case 1 -> "🔁 Loop: Queue";
                    case 2 -> "🔂 Loop: Track";
                    default -> "➡ Loop: Off";
                };
                session.lastAction = mode;
                editToMainView(event, mgr, session);
            }
            case "shuffle" -> {
                mgr.scheduler.toggleShuffle();
                session.lastAction = mgr.scheduler.shuffle ? "🔀 Shuffle: On" : "➡ Shuffle: Off";
                editToMainView(event, mgr, session);
            }

            // Navigation
            case "queuesong" -> showQueueSongModal(event);
            case "queueview" -> { session.view = View.QUEUE; session.page = 0; editToQueueView(event, session); }
            case "history" -> { session.view = View.HISTORY; session.page = 0; editToHistoryView(event, session); }
            case "back" -> { session.view = View.MAIN; session.page = 0; session.lastAction = "🔙 Back to player"; editToMainView(event, mgr, session); }

            // Queue View Controls
            case "qprev" -> { session.page = Math.max(0, session.page - 1); editToQueueView(event, session); }
            case "qnext" -> { session.page++; editToQueueView(event, session); }
            case "remove" -> showRemoveMenu(event, session);
            case "reindex" -> { reindexQueue(session); session.lastAction = "🔀 Queue shuffled"; editToQueueView(event, session); }
            case "clearqueue" -> { clearGuildQueue(session.guildId); session.page = 0; session.lastAction = "🧹 Queue cleared"; editToQueueView(event, session); }

            // History View Controls
            case "hprev" -> { session.page = Math.max(0, session.page - 1); editToHistoryView(event, session); }
            case "hnext" -> { session.page++; editToHistoryView(event, session); }

            // Voice Join
            case "join" -> {
                Member member = event.getMember();
                if (member != null && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                    guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
                    session.lastAction = "🔊 Joined voice channel";
                    editToMainView(event, mgr, session);
                } else {
                    event.reply("❌ You must be in a voice channel!")
                            .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL_PREFIX)
    public void onModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());

        if ("queuesong".equals(modalId)) {
            String query = getModalValue(event, "query");

            if (query.isEmpty()) {
                event.reply("❌ Please enter a search query or URL!").setEphemeral(true)
                        .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                return;
            }

            var sourceMapping = event.getValue("source");
            String source = "auto";
            if (sourceMapping != null && !sourceMapping.getAsStringList().isEmpty()) {
                source = sourceMapping.getAsStringList().getFirst();
            }

            String searchPrefix;
            if (query.startsWith("http")) {
                searchPrefix = "";
            } else {
                searchPrefix = switch (source) {
                    case "youtube" -> "ytsearch:";
                    case "soundcloud" -> "scsearch:";
                    default -> "ytsearch:";
                };
            }

            Guild guild = event.getGuild();
            if (guild == null) return;

            Member member = event.getMember();
            GuildMusicManager mgr = getGuildAudioPlayer(guild);

            if (!guild.getAudioManager().isConnected() && member != null
                    && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                guild.getAudioManager().openAudioConnection(member.getVoiceState().getChannel());
            }

            session.cleanupTemp();
            final String finalSearchPrefix = searchPrefix;
            event.reply(
                    new MessageCreateBuilder()
                            .useComponentsV2(true)
                            .setComponents(Container.of(
                                    TextDisplay.of("# 🔍 Searching..."),
                                    Separator.create(false, Separator.Spacing.SMALL),
                                    TextDisplay.of("_Loading results, please wait..._")
                            ).withAccentColor(ACCENT_PLAYING))
                            .build()
            ).setEphemeral(true).queue(hook -> {
                session.tempHook = hook;
                hook.retrieveOriginal().queue(msg -> session.tempMessage = msg);

                playerManager.loadItemOrdered(mgr, finalSearchPrefix + query, new AudioLoadResultHandler() {
                    @Override public void trackLoaded(AudioTrack track) {
                        mgr.scheduler.queue(track, userId);
                        session.lastAction = "🎵 Queued: " + truncate(track.getInfo().title, 40);
                        session.cleanupTemp();
                        updateSessionMessage(session, mgr);
                    }

                    @Override public void playlistLoaded(AudioPlaylist playlist) {
                        if (playlist.isSearchResult()) {
                            handleSearchResults(session, playlist);
                        } else {
                            for (AudioTrack track : playlist.getTracks()) {
                                mgr.scheduler.queue(track, userId);
                            }
                            session.lastAction = "📋 Queued playlist: " + truncate(playlist.getName(), 35) + " (" + playlist.getTracks().size() + " tracks)";
                            session.cleanupTemp();
                            updateSessionMessage(session, mgr);
                        }
                    }

                    @Override public void noMatches() {
                        var hookRef = session.tempHook;
                        session.tempMessage = null;
                        session.tempHook = null;
                        if (hookRef != null) {
                            hookRef.editOriginal(
                                    new MessageEditBuilder().useComponentsV2(true)
                                            .setComponents(Container.of(
                                                    TextDisplay.of("# ❌ No Results"),
                                                    Separator.create(false, Separator.Spacing.SMALL),
                                                    TextDisplay.of("_No matches found!_")
                                            ).withAccentColor(ACCENT_IDLE)).build()
                            ).queue(
                                    _ -> hookRef.deleteOriginal().queueAfter(5, TimeUnit.SECONDS)
                            );
                        }
                    }

                    @Override public void loadFailed(FriendlyException exception) {
                        var hookRef = session.tempHook;
                        session.tempMessage = null;
                        session.tempHook = null;
                        if (hookRef != null) {
                            hookRef.editOriginal(
                                    new MessageEditBuilder().useComponentsV2(true)
                                            .setComponents(Container.of(
                                                    TextDisplay.of("# ❌ Load Failed"),
                                                    Separator.create(false, Separator.Spacing.SMALL),
                                                    TextDisplay.of("_" + exception.getMessage() + "_")
                                            ).withAccentColor(ACCENT_IDLE)).build()
                            ).queue(
                                    _ -> hookRef.deleteOriginal().queueAfter(5, TimeUnit.SECONDS)
                            );
                        }
                    }
                });
            });
        }
    }

    // ==================== SELECT MENU HANDLER ====================

    @SelectMenuHandler(MENU_PREFIX)
    public void onSelectMenu(StringSelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        MusicSession session = activeSessions.get(userId);
        if (session == null) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        String menuId = event.getComponentId();

        if (menuId.startsWith(MENU_PREFIX + "select:")) {
            String searchId = menuId.substring((MENU_PREFIX + "select:").length());
            List<AudioTrack> tracks = searchCache.get(searchId);

            if (tracks == null) {
                event.reply("❌ Search expired.").setEphemeral(true)
                        .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                return;
            }

            int index = Integer.parseInt(event.getValues().getFirst());
            AudioTrack selected = tracks.get(index);

            GuildMusicManager mgr = getGuildAudioPlayer(guild);
            mgr.scheduler.queue(selected, userId);
            searchCache.remove(searchId);

            event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
            session.tempMessage = null;
            session.tempHook = null;

            session.lastAction = "🎵 Queued: " + truncate(selected.getInfo().title, 40);
            session.view = View.MAIN;
            updateSessionMessage(session, mgr);
            return;
        }

        if (menuId.equals(MENU_PREFIX + "remove")) {
            String dbIdStr = event.getValues().getFirst();
            long dbId = Long.parseLong(dbIdStr);

            Optional<QueueEntry> entryOpt = queueRepo.findById(dbId);
            String removedTitle = "unknown";
            if (entryOpt.isPresent() && entryOpt.get().getGuildId() == session.guildId) {
                removedTitle = entryOpt.get().getTitle();
                queueRepo.deleteById(dbId);
            }

            event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, _ -> {}));
            session.tempMessage = null;
            session.tempHook = null;

            session.lastAction = "🗑 Removed: " + truncate(removedTitle, 40);
            session.view = View.QUEUE;
            if (session.message != null) {
                session.message.editMessage(
                        new MessageEditBuilder().useComponentsV2(true)
                                .setComponents(viewBuilder.buildQueueView(session)).build()
                ).queue();
            }
        }
    }

    // ==================== VOICE EVENT ====================

    @EventHandler
    public void onVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelLeft() != null) {
            Guild guild = event.getGuild();
            if (event.getMember().getUser().getIdLong() == guild.getSelfMember().getUser().getIdLong()) {
                GuildMusicManager mgr = musicManagers.get(guild.getIdLong());
                if (mgr != null) {
                    mgr.player.destroy();
                    mgr.scheduler.clearQueue();
                    musicManagers.remove(guild.getIdLong());
                }
            }
        }
    }

    // ==================== VIEW EDIT HELPERS ====================

    private void editToMainView(ButtonInteractionEvent event, GuildMusicManager mgr, MusicSession session) {
        session.view = View.MAIN;
        event.editMessage(
                new MessageEditBuilder().useComponentsV2(true)
                        .setComponents(viewBuilder.buildMainView(mgr, session)).build()
        ).queue();
    }

    private void editToQueueView(ButtonInteractionEvent event, MusicSession session) {
        event.editMessage(
                new MessageEditBuilder().useComponentsV2(true)
                        .setComponents(viewBuilder.buildQueueView(session)).build()
        ).queue();
    }

    private void editToHistoryView(ButtonInteractionEvent event, MusicSession session) {
        event.editMessage(
                new MessageEditBuilder().useComponentsV2(true)
                        .setComponents(viewBuilder.buildHistoryView(session)).build()
        ).queue();
    }

    private void updateSessionMessage(MusicSession session, GuildMusicManager mgr) {
        if (session.message != null) {
            session.view = View.MAIN;
            session.message.editMessage(
                    new MessageEditBuilder().useComponentsV2(true)
                            .setComponents(viewBuilder.buildMainView(mgr, session)).build()
            ).queue();
        }
    }

    private MessageEditBuilder buildErrorView(String title, String description) {
        return new MessageEditBuilder().useComponentsV2(true)
                .setComponents(Container.of(
                        TextDisplay.of(title),
                        Separator.create(false, Separator.Spacing.SMALL),
                        TextDisplay.of(description),
                        Separator.create(true, Separator.Spacing.SMALL),
                        net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                                net.dv8tion.jda.api.components.buttons.Button.primary(BTN + "back", "🔙 Back to Player")
                        )
                ).withAccentColor(ACCENT_IDLE));
    }

    // ==================== MODAL / MENU BUILDERS ====================

    private void showQueueSongModal(ButtonInteractionEvent event) {
        StringSelectMenu sourceMenu = StringSelectMenu.create("source")
                .setPlaceholder("Search source")
                .addOption("🔍 Auto-detect (URL or YouTube)", "auto")
                .addOption("▶ YouTube", "youtube")
                .addOption("☁ SoundCloud", "soundcloud")
                .setDefaultOptions(SelectOption.of("🔍 Auto-detect (URL or YouTube)", "auto"))
                .build();

        event.replyModal(Modal.create(MODAL_PREFIX + "queuesong", "Queue a Song")
                .addComponents(
                        Label.of("URL or Search Query", TextInput.create("query", TextInputStyle.SHORT)
                                .setPlaceholder("Paste a URL or type a search query...")
                                .setMaxLength(500)
                                .setRequired(true)
                                .build()
                        ),
                        Label.of("Search Source", sourceMenu)
                ).build()
        ).queue();
    }

    private void showRemoveMenu(ButtonInteractionEvent event, MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .limit(25)
                .list();

        if (queue.isEmpty()) {
            event.reply("ℹ️ Queue is empty!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        session.cleanupTemp();

        event.reply(
                new MessageCreateBuilder().useComponentsV2(true)
                        .setComponents(viewBuilder.buildRemoveView(session, queue)).build()
        ).setEphemeral(true).queue(hook -> {
            session.tempHook = hook;
            hook.retrieveOriginal().queue(msg -> session.tempMessage = msg);
        });
    }

    private void handleSearchResults(MusicSession session, AudioPlaylist playlist) {
        List<AudioTrack> tracks = playlist.getTracks().subList(0, Math.min(5, playlist.getTracks().size()));
        String searchId = UUID.randomUUID().toString();
        searchCache.put(searchId, tracks);

        if (session.tempHook != null) {
            session.view = View.SEARCH;
            session.tempHook.editOriginal(
                    new MessageEditBuilder().useComponentsV2(true)
                            .setComponents(viewBuilder.buildSearchView(session, tracks, searchId)).build()
            ).queue(msg -> session.tempMessage = msg);
        }
    }

    // ==================== QUEUE MANIPULATION ====================

    private void reindexQueue(MusicSession session) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", session.guildId)
                .where("status", "QUEUE")
                .list();

        if (queue.size() <= 1) return;

        Collections.shuffle(queue);
        for (QueueEntry entry : queue) { queueRepo.deleteById(entry.getId()); }
        for (QueueEntry entry : queue) { entry.setId(null); queueRepo.save(entry); }
    }

    private void clearGuildQueue(long guildId) {
        List<QueueEntry> queue = queueRepo.query()
                .where("guild_id", guildId)
                .where("status", "QUEUE")
                .list();
        for (QueueEntry entry : queue) { queueRepo.deleteById(entry.getId()); }
    }

    private void recoverStaleQueue(long guildId) {
        List<QueueEntry> stale = queueRepo.query().where("guild_id", guildId).where("status", "CURRENT").list();
        for (QueueEntry entry : stale) { entry.setStatus("QUEUE"); entry.setIsLooped(false); queueRepo.save(entry); }

        List<QueueEntry> errors = queueRepo.query().where("guild_id", guildId).where("status", "ERROR").list();
        for (QueueEntry entry : errors) { entry.setStatus("QUEUE"); entry.setIsLooped(false); queueRepo.save(entry); }

        List<QueueEntry> played = queueRepo.query().where("guild_id", guildId).where("status", "PLAYED").list();
        for (QueueEntry entry : played) { queueRepo.deleteById(entry.getId()); }
    }

    // ==================== AUDIO MANAGER ====================

    private GuildMusicManager getGuildAudioPlayer(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(), _ -> {
            TrackScheduler.Dependencies deps = new TrackScheduler.Dependencies(context, playerManager, queueRepo, historyRepo);
            GuildMusicManager mgr = new GuildMusicManager(playerManager, guild.getIdLong(), deps);
            guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(mgr.player));
            return mgr;
        });
    }

    // ==================== UTILITIES ====================

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "Unknown";
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }
}