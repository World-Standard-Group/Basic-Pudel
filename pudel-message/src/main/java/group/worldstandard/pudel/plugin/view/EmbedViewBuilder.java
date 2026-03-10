package group.worldstandard.pudel.plugin.view;

import group.worldstandard.pudel.plugin.session.EmbedField;
import group.worldstandard.pudel.plugin.session.EmbedSession;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Components v2 views and the final embed for the embed builder plugin.
 */
public class EmbedViewBuilder {

    private static final Color DEFAULT_PREVIEW_COLOR = new Color(0x2B2D31);

    private final String buttonPrefix;

    public EmbedViewBuilder(String buttonPrefix) {
        this.buttonPrefix = buttonPrefix;
    }

    // ==================== V2 MESSAGE BUILDERS ====================

    public MessageCreateBuilder buildV2Message(EmbedSession session) {
        return new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(getBuilderContainer(), buildPreviewContainer(session));
    }

    public MessageEditBuilder editV2Message(EmbedSession session) {
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(getBuilderContainer(), buildPreviewContainer(session));
    }

    public MessageEditData editV2Data(EmbedSession session) {
        return editV2Message(session).build();
    }

    // ==================== BUILDER CONTROLS ====================

    private Container getBuilderContainer() {
        return Container.of(
                TextDisplay.of("# 🛠️ Embed Builder\nUse the buttons below to edit. The preview below updates automatically."),
                Separator.create(true, Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.primary(buttonPrefix + "title", "📝 Title"),
                        Button.primary(buttonPrefix + "description", "📄 Desc"),
                        Button.primary(buttonPrefix + "color", "🎨 Color"),
                        Button.primary(buttonPrefix + "author", "👤 Author"),
                        Button.primary(buttonPrefix + "footer", "📌 Footer")
                ),
                ActionRow.of(
                        Button.primary(buttonPrefix + "thumbnail", "🖼️ Thumb"),
                        Button.primary(buttonPrefix + "image", "🌄 Image"),
                        Button.primary(buttonPrefix + "url", "🔗 URL"),
                        Button.primary(buttonPrefix + "timestamp", "⏰ Time")
                ),
                ActionRow.of(
                        Button.success(buttonPrefix + "field", "➕ Field"),
                        Button.danger(buttonPrefix + "clearfields", "🗑️ Clear Fields")
                ),
                ActionRow.of(
                        Button.success(buttonPrefix + "post", "✅ Post Embed"),
                        Button.danger(buttonPrefix + "cancel", "✖ Cancel")
                )
        );
    }

    // ==================== LIVE PREVIEW ====================

    /**
     * Live preview container — renders embed content using Components v2.
     * <p>
     * The container's accent color matches the embed color, visually mimicking
     * the colored left-border of a classic embed.
     */
    private Container buildPreviewContainer(EmbedSession session) {
        boolean hasContent = session.title != null || session.description != null || session.author != null
                || session.footer != null || session.image != null || session.thumbnail != null
                || session.timestamp != null || !session.fields.isEmpty();

        Color accentColor = session.color != null ? session.color : DEFAULT_PREVIEW_COLOR;

        if (!hasContent) {
            return Container.of(
                    TextDisplay.of("### 🆕 New Embed"),
                    TextDisplay.of("_Start adding content using the buttons above._\n_This preview will update automatically._")
            ).withAccentColor(Color.LIGHT_GRAY);
        }

        List<ContainerChildComponent> children = new ArrayList<>();

        // Author line
        if (session.author != null) {
            String authorText = session.authorUrl != null
                    ? "-# [" + session.author + "](" + session.authorUrl + ")"
                    : "-# " + session.author;
            if (session.authorIcon != null) {
                children.add(Section.of(
                        Thumbnail.fromUrl(session.authorIcon),
                        TextDisplay.of(authorText)
                ));
            } else {
                children.add(TextDisplay.of(authorText));
            }
        }

        // Title (optionally paired with Thumbnail)
        if (session.title != null) {
            String titleText = session.url != null
                    ? "### [" + session.title + "](" + session.url + ")"
                    : "### " + session.title;

            if (session.thumbnail != null) {
                children.add(Section.of(
                        Thumbnail.fromUrl(session.thumbnail),
                        TextDisplay.of(titleText)
                ));
            } else {
                children.add(TextDisplay.of(titleText));
            }
        } else if (session.thumbnail != null) {
            children.add(Section.of(
                    Thumbnail.fromUrl(session.thumbnail),
                    TextDisplay.of("\u200B")
            ));
        }

        // Description
        if (session.description != null) {
            children.add(TextDisplay.of(session.description));
        }

        // Fields
        if (!session.fields.isEmpty()) {
            children.add(Separator.create(false, Separator.Spacing.SMALL));

            StringBuilder inlineGroup = new StringBuilder();
            for (EmbedField f : session.fields) {
                if (f.inline()) {
                    if (!inlineGroup.isEmpty()) inlineGroup.append("\u2003\u2003\u2003");
                    inlineGroup.append("**").append(f.name()).append("**\n").append(f.value());
                } else {
                    if (!inlineGroup.isEmpty()) {
                        children.add(TextDisplay.of(inlineGroup.toString()));
                        inlineGroup.setLength(0);
                    }
                    children.add(TextDisplay.of("**" + f.name() + "**\n" + f.value()));
                }
            }
            if (!inlineGroup.isEmpty()) {
                children.add(TextDisplay.of(inlineGroup.toString()));
            }
        }

        // Image
        if (session.image != null) {
            children.add(MediaGallery.of(MediaGalleryItem.fromUrl(session.image)));
        }

        // Footer + Timestamp
        if (session.footer != null || session.timestamp != null) {
            children.add(Separator.create(false, Separator.Spacing.SMALL));
            StringBuilder footerText = new StringBuilder("-# ");
            if (session.footer != null) footerText.append(session.footer);
            if (session.footer != null && session.timestamp != null) footerText.append(" • ");
            if (session.timestamp != null) {
                footerText.append(session.timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
            }
            children.add(TextDisplay.of(footerText.toString()));
        }

        return Container.of(children).withAccentColor(accentColor);
    }

    // ==================== FINAL EMBED BUILDER ====================

    /**
     * Builds the final classic {@link MessageEmbed} for posting to the target channel.
     */
    public MessageEmbed buildFinalEmbed(EmbedSession session) {
        EmbedBuilder b = new EmbedBuilder();
        if (session.title != null) b.setTitle(session.title, session.url);
        if (session.description != null) b.setDescription(session.description);
        if (session.color != null) b.setColor(session.color);
        if (session.author != null) b.setAuthor(session.author, session.authorUrl, session.authorIcon);
        if (session.footer != null) b.setFooter(session.footer, session.footerIcon);
        if (session.thumbnail != null) b.setThumbnail(session.thumbnail);
        if (session.image != null) b.setImage(session.image);
        if (session.timestamp != null) b.setTimestamp(session.timestamp);
        for (EmbedField f : session.fields) b.addField(f.name(), f.value(), f.inline());

        if (b.isEmpty()) b.setDescription("_Empty Embed_");
        return b.build();
    }
}

