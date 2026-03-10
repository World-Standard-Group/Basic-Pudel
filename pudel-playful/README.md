# Basic Pudel - 🎭 Pudel's Playful Time

**Version:** 1.1.0  
**Author:** Zazalng  
**For:** Pudel API 2.2.0+

A fun, interactive plugin for the Pudel Discord Bot that allows users to create their own collections of "pranks" (harmless image/GIF reactions) with custom placeholder messages. 

Whether you want to "bonk", "slap", or "hug" your friends, this plugin lets you organize your favorite reaction GIFs into custom containers and fire them at random with personalized text!

---

## ✨ Features

* **Interactive Control Panel:** A fully UI-driven ephemeral control panel using Discord's modern UI components.
* **Custom Containers:** Group your pranks by theme (e.g., "bonk", "hug", "high-five").
* **Flexible Uploads:** Add images/GIFs by directly uploading files or pasting hosted URLs.
* **Dynamic Messages:** Use placeholders to automatically mention the person using the command and their target.
* **Import/Export:** Easily share your prank collections with others or back them up using JSON files!
* **Randomized Selection:** Firing a prank container randomly selects one of the images/GIFs inside it to keep things fresh.

---

## 🛠️ Commands

### `/prank`
Opens the modal for **quick fire prank** or **open control panel**. From here, you can:
* Add, View, and Delete containers.
* Add pranks (via upload or URL), edit, and remove them.
* Export your entire collection to a JSON file or import an existing one.

### `/prank <name> [target]`
Fires a random prank from the specified container.
* **`name`** (Required): The name of your prank container (e.g., `bonk`).
* **`target`** (Optional): The user you want to target with the prank.

---

## 📝 Message Placeholders

When adding a new prank, you can use special placeholders in your message template. The bot will automatically swap these out when the prank is fired:

* **`%m`** — Mentions the user who fired the command (you).
* **`%t`** — Mentions the target user (if provided).

**Example Template:** `%m hit %t so hard!`  
**Result:** `@Zazalng hit @Friend so hard!`

---

## 📦 Export / Import Format (JSON)

You can share your containers with friends by exporting them from the Control Panel. The bot generates a JSON file structured like this:

```json
{
  "bonk": [
    {
      "id": "uuid-here",
      "url": "[https://example.com/bonk1.gif](https://example.com/bonk1.gif)",
      "placeholder": "%m bonked %t!"
    }
  ],
  "slap": [
    {
      "id": "uuid-here",
      "url": "[https://example.com/slap.gif](https://example.com/slap.gif)",
      "placeholder": "%m slapped %t with a large trout!"
    }
  ]
}

```

*Note: When importing, if a container with the same name already exists, the plugin will merge the new pranks into it.*