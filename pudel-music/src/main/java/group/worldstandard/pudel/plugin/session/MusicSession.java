package group.worldstandard.pudel.plugin.session;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Tracks the state of an active Music Box session for a user.
 */
public class MusicSession {

    public enum View { MAIN, QUEUE, HISTORY, SEARCH }

    public final long userId;
    public final long guildId;
    public Message message;          // The main Music Box message (persistent)
    public Message tempMessage;      // Temporary popup message for search results / remove menu
    public InteractionHook tempHook; // Hook for temp message lifecycle
    public View view = View.MAIN;
    public int page = 0;

    public MusicSession(long userId, long guildId) {
        this.userId = userId;
        this.guildId = guildId;
    }

    /** Clean up the temporary popup message if it exists */
    public void cleanupTemp() {
        if (tempMessage != null) {
            tempMessage.delete().queue(null, _ -> {});
            tempMessage = null;
        }
        tempHook = null;
    }
}

