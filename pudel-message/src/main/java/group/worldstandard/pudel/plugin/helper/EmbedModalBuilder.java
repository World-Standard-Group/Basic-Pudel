package group.worldstandard.pudel.plugin.helper;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds and displays modals for the embed builder plugin.
 */
public class EmbedModalBuilder {

    private final String modalPrefix;
    private final String menuPrefix;

    public EmbedModalBuilder(String modalPrefix, String menuPrefix) {
        this.modalPrefix = modalPrefix;
        this.menuPrefix = menuPrefix;
    }

    public void showTitleModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "title", "Title")
                .addComponents(
                        Label.of("Embed Title", TextInput.create("title", TextInputStyle.SHORT)
                                .setPlaceholder("Title")
                                .setMaxLength(256)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showDescriptionModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "description", "Description")
                .addComponents(
                        Label.of("Embed Description", TextInput.create("description", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Description Text")
                                .setMaxLength(4000)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showAuthorModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "author", "Author")
                .addComponents(
                        Label.of("Embed Author Name", TextInput.create("author", TextInputStyle.SHORT)
                                .setPlaceholder("Author Name")
                                .setMaxLength(255)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Author URL", TextInput.create("authorurl", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Icon URL", TextInput.create("authoricon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showFooterModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "footer", "Footer")
                .addComponents(
                        Label.of("Embed Foot Note", TextInput.create("footer", TextInputStyle.SHORT)
                                .setPlaceholder("Foot note")
                                .setMaxLength(2048)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Foot Icon", TextInput.create("footericon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showThumbnailModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "thumbnail", "Thumbnail")
                .addComponents(
                        Label.of("Embed Thumbnail URL", TextInput.create("thumbnail", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showImageModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "image", "Image")
                .addComponents(
                        Label.of("Embed Image URL", TextInput.create("image", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showUrlModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "url", "Title URL")
                .addComponents(
                        Label.of("Embed Title URL", TextInput.create("url", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showTimestampModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(modalPrefix + "timestamp", "Timestamp")
                .addComponents(
                        Label.of("Embed Timestamp", TextInput.create("timestamp", TextInputStyle.SHORT)
                                .setPlaceholder(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ssX")))
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    public void showFieldModal(ButtonInteractionEvent e) {
        StringSelectMenu inlineSelect = StringSelectMenu.create("fieldinline")
                .setPlaceholder("Inline?")
                .addOption("✅ Yes — Inline", "yes")
                .addOption("❌ No — Full Width", "no")
                .build();
        e.replyModal(Modal.create(modalPrefix + "field", "Add Field")
                .addComponents(
                        Label.of("Field Title", TextInput.create("fieldname", TextInputStyle.SHORT)
                                .setPlaceholder("Header")
                                .setMaxLength(256)
                                .build()
                        ),
                        Label.of("Field Content", TextInput.create("fieldvalue", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Value")
                                .setMaxLength(1024)
                                .build()
                        ),
                        Label.of("Display Inline?", inlineSelect)
                ).build()
        ).queue();
    }

    public void showColorSelectMenu(ButtonInteractionEvent event) {
        StringSelectMenu menu = StringSelectMenu.create(menuPrefix + "color")
                .addOption("🔴 Red", "red").addOption("🔵 Blue", "blue").addOption("🟢 Green", "green")
                .addOption("🟡 Yellow", "yellow").addOption("🟠 Orange", "orange").addOption("🟣 Purple", "purple")
                .addOption("⚪ White", "white").addOption("⚫ Black", "black").addOption("🎨 Custom", "custom")
                .addOption("❌ Reset", "none")
                .build();
        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### 🎨 Select Color"),
                                ActionRow.of(menu)
                        ))
                        .build()
        ).setEphemeral(true).queue();
    }

    public void showChannelSelect(ButtonInteractionEvent event) {
        EntitySelectMenu channelMenu = EntitySelectMenu.create("postchannel", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder("Select a channel to post this embed")
                .setMaxValues(1)
                .build();
        event.replyModal(Modal.create(modalPrefix + "post", "Post Embed")
                .addComponents(
                        Label.of("📨 Select Channel", channelMenu)
                ).build()
        ).queue();
    }
}

