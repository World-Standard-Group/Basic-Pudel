# Basic Pudel - Pudel's Embed Builder Messages

**Version:** 3.0.1  
**Author:** Zazalng  
**For:** Pudel API 2.2.0+

Pudel's Embed Builder is an interactive embed builder plugin designed for the Pudel Discord Bot. It functions as a module within the broader Basic-Pudel project, which serves as a central repository for Basic Command Plugins. Built entirely with Discord's Components v2 system, this plugin provides users with a rich, modern interface to build and preview messages directly within Discord.

---

## ✨ Features
- Interactive Interface: The plugin uses Discord's Components v2 (Container, TextDisplay, Section, MediaGallery, etc.) to create a modern builder interface.
- Live Previews: Users can see a live visual preview of their embed that updates automatically as they make changes.
- Single Command Entry: The entire builder is accessed through a single slash command: /embed.
- Button-Based Editing: All content editing, including titles, descriptions, colors, fields, and images, is handled via interactive buttons and modals.
- Integrated Channel Selection: Users can select the target destination for their embed directly through the UI.
- Classic Output: Once finished, the final result is posted to the selected channel as a classic MessageEmbed.

---

## 🛠️ Commands

### `/embed`
Opens the **Embed Builder Control Panel** (visible only to you). From here, you can:
* Adjusted how your embed display out by looking at live preview.
* Can Post anywhere (Post as Bot but Channel overview by user)
* Session management (redundant confusion which preview is for which control panel)

---

*Note: You have window-time for respond component within 15 min (any interaction happen refresh respond time), or your session & progression was lost and have to start from beginning*