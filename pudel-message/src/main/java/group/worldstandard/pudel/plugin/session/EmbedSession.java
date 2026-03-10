package group.worldstandard.pudel.plugin.session;

import net.dv8tion.jda.api.entities.Message;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the state of an active embed builder session for a user.
 */
public class EmbedSession {
    public final long userId;
    public Message previewMessage;
    public String title, description, thumbnail, image, author, authorUrl, authorIcon, footer, footerIcon, url;
    public Color color;
    public OffsetDateTime timestamp;
    public List<EmbedField> fields = new ArrayList<>();

    public EmbedSession(long userId) {
        this.userId = userId;
    }
}

