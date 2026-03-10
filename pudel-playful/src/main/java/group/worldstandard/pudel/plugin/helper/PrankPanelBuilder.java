package group.worldstandard.pudel.plugin.helper;

import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.entity.PrankCollection;
import group.worldstandard.pudel.plugin.entity.PrankContainer;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Components v2 panels for the prank control panel.
 */
public class PrankPanelBuilder {

    private final PluginRepository<PrankContainer> containerRepo;
    private final PluginRepository<PrankCollection> collectionRepo;
    private final String btnPrefix;
    private final String menuPrefix;
    private final Color accentMain;
    private final Color accentView;

    public PrankPanelBuilder(PluginRepository<PrankContainer> containerRepo,
                             PluginRepository<PrankCollection> collectionRepo,
                             String btnPrefix, String menuPrefix,
                             Color accentMain, Color accentView) {
        this.containerRepo = containerRepo;
        this.collectionRepo = collectionRepo;
        this.btnPrefix = btnPrefix;
        this.menuPrefix = menuPrefix;
        this.accentMain = accentMain;
        this.accentView = accentView;
    }

    // ==================== MAIN PANEL ====================

    public Container buildMainPanelContainer(String userId) {
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🎭 Prank Control Panel"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (myContainers.isEmpty()) {
            children.add(TextDisplay.of("_You don't have any prank containers yet._\n_Use **Add Container** to create one!_"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("### 📦 Your Containers\n");
            for (PrankContainer c : myContainers) {
                long prankCount = collectionRepo.query()
                        .where("container_id", c.getContainerId())
                        .count();
                sb.append("• **").append(c.getName()).append("** — ")
                        .append(prankCount).append(" pranks, used ")
                        .append(c.getUsage()).append(" times\n");
            }
            children.add(TextDisplay.of(sb.toString()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.success(btnPrefix + "add-container", "➕ Add Container"),
                Button.primary(btnPrefix + "view-container", "👁️ View Container"),
                Button.danger(btnPrefix + "delete-container", "🗑️ Delete Container")
        ));
        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "export-container", "📤 Export JSON"),
                Button.secondary(btnPrefix + "import-container", "📥 Import JSON")
        ));

        return Container.of(children).withAccentColor(accentMain);
    }

    public MessageCreateBuilder buildMainPanel(String userId) {
        return new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(buildMainPanelContainer(userId));
    }

    public MessageEditBuilder editMainPanel(String userId) {
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(buildMainPanelContainer(userId));
    }

    // ==================== VIEW CONTAINER ====================

    public MessageEditBuilder editViewPanel(String userId, String containerId) {
        PrankContainer container = findContainerById(containerId);
        if (container == null) return editMainPanel(userId);

        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", containerId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📦 " + container.getName()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (pranks.isEmpty()) {
            children.add(TextDisplay.of("_This container is empty._\n_Use **Add Prank** to upload an image/gif!_"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🎴 Pranks (").append(pranks.size()).append(")\n");
            int idx = 1;
            for (PrankCollection p : pranks) {
                String shortId = p.getPrankId().substring(0, 8);
                String truncatedPlaceholder = p.getPlaceholder().length() > 40
                        ? p.getPlaceholder().substring(0, 40) + "..."
                        : p.getPlaceholder();
                sb.append(idx++).append(". `").append(shortId).append("` — _")
                        .append(truncatedPlaceholder).append("_\n");
            }
            children.add(TextDisplay.of(sb.toString()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.success(btnPrefix + "add-prank", "➕ Add Prank"),
                Button.primary(btnPrefix + "edit-prank", "✏️ Edit Prank"),
                Button.danger(btnPrefix + "remove-prank", "🗑️ Remove Prank")
        ));
        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "back-main", "⬅️ Back")
        ));

        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(Container.of(children).withAccentColor(accentView));
    }

    // ==================== CONTAINER SELECT ====================

    public MessageEditBuilder editContainerSelectPanel(String userId, String action) {
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("### 📦 Select Container to " + (action.equals("view") ? "View" : "Delete")));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (myContainers.isEmpty()) {
            children.add(TextDisplay.of("_You don't have any containers yet._"));
        } else {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(menuPrefix + action + "-container")
                    .setPlaceholder("Select a container");

            for (PrankContainer c : myContainers) {
                long prankCount = collectionRepo.query()
                        .where("container_id", c.getContainerId())
                        .count();
                menuBuilder.addOption(
                        c.getName() + " (" + prankCount + " pranks)",
                        c.getContainerId()
                );
            }
            children.add(ActionRow.of(menuBuilder.build()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "back-main", "⬅️ Back")
        ));

        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(Container.of(children).withAccentColor(accentMain));
    }

    // ==================== PRANK SELECT ====================

    public MessageEditBuilder editPrankSelectPanel(String containerId, String action) {
        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", containerId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("### 🎴 Select Prank to " + (action.equals("edit") ? "Edit" : "Remove")));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (pranks.isEmpty()) {
            children.add(TextDisplay.of("_This container has no pranks yet._"));
        } else {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(menuPrefix + action + "-prank")
                    .setPlaceholder("Select a prank to " + action);

            for (PrankCollection p : pranks) {
                String shortId = p.getPrankId().substring(0, 8);
                String label = p.getPlaceholder().length() > 50
                        ? p.getPlaceholder().substring(0, 50) + "..."
                        : p.getPlaceholder();
                menuBuilder.addOption(shortId + " — " + label, p.getPrankId());
            }
            children.add(ActionRow.of(menuBuilder.build()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.secondary(btnPrefix + "back-view", "⬅️ Back")
        ));

        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(Container.of(children).withAccentColor(accentView));
    }

    // ==================== UTILITY ====================

    private PrankContainer findContainerById(String containerId) {
        List<PrankContainer> list = containerRepo.query()
                .where("container_id", containerId)
                .list();
        return list.isEmpty() ? null : list.getFirst();
    }
}

