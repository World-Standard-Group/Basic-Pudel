# Advanced Pudel - 📂 Pudel's Category Management

**Version:** 2.0.0  
**Author:** Zazalng  
**For:** Pudel API 2.2.2+

Pudel's Category Management is a plugin module for the Pudel Discord Bot that provides a full Components v2 control panel for creating, importing, managing permissions, and unlinking Discord categories via slash command. It automates the assignment of category-level permission overrides based on the selected permission profiles, such as manager and default role, keeping your server organized and secure.

---

## ✨ Features

* **Interactive Control Panel:** A fully UI-driven ephemeral control panel built with Discord's modern Components v2 system (`Container`, `TextDisplay`, `Separator`, `ActionRow`).
* **Create Categories:** Create new Discord categories with optional Manager User, Default Role, and opt-in Pudel tracking — all from a single modal using `TextInput`, `EntitySelectMenu`, and `CheckboxGroup`.
* **Import Existing Categories:** Import categories already in your server under Pudel's control, with an acknowledgement checkbox to confirm permission sync awareness.
* **Default Permission Settings:** Configure default Allow / Inherit / Deny permission states by profile using the permission editor's explicit state controls.
* **Privilege Roles:** Grant specific roles the ability to create, import, and edit permissions without needing the Discord `Manage Channels` permission.
* **Category Viewer:** Browse all Pudel-tracked categories with stats including channel counts (text, voice, forum), assigned manager, and default role.
* **Unlink Categories:** Remove a category from Pudel's tracking without deleting it from the server. Visibility is scoped — admins see all, managers see only their own.
* **Permission Auto-Apply:** When creating or importing, Pudel automatically applies permission overrides based on your configured profile. Setting a Default Role makes the category private (denies `VIEW_CHANNEL` for @everyone).
* **Child Channel Sync:** On import with a Manager or Default Role, all child channels are synced to the updated category permissions.
* **Database Persistence:** All tracked categories, permission profile, and privilege roles are stored in Pudel's plugin database for persistence across restarts.

---

## 🛠️ Commands

### `/categorizement`
Opens the **Category Management Control Panel** (visible only to you). From here, you can:
* **+ Create** — Open a modal to create a new category (Name, Manager User, Default Role, Control via Pudel checkbox)
* **+ Import** — Open a modal to import an existing category under Pudel's control (with acknowledgement checkbox)
* **⚙️ Setting** — View and edit permissions profile, manage Privilege Roles
* **📋 View Category** — Browse tracked categories and view their details
* **🔗 Unlink Category** — Remove a category from Pudel tracking (keeps the category in the guild)

---

## 🔐 Permission Model

| Action | Requirement |
|--------|-------------|
| Create / Import | `Manage Channels` permission **or** Privilege Role |
| Edit Default Permissions | `Manage Channels` permission **or** Privilege Role |
| Add / Remove Privilege Role | `Manage Channels` permission only |
| View Settings / Categories | Everyone |
| Unlink Category | `Manage Channels`, Privilege Role, **or** matching Manager |

---

## ⚙️ Permission Editor (NES-style Cursor)

The permission editor displays all manageable category permissions grouped by section (**Management**, **Messaging**, **Voice**) with three possible states:

| Icon | State | Meaning |
|------|-------|---------|
| ✅ | Allow | Permission explicitly allowed |
| ⬜ | Inherit | Permission inherited from server defaults |
| ❌ | Deny | Permission explicitly denied |

Navigate with **⬆️ Up** / **⬇️ Down**, then **✅ Confirm** to save or **🔃 Reset** to reload from database.

> **Note:** `VIEW_CHANNEL` is excluded from the editor — for Manager it is always Inherit, for Default Role it is always Allow (auto-applied).

---

## 📋 Managed Permissions

### Management
- Manage Channel
- Manage Permission
- Manage Webhooks
- Manage Events
- Create Events
- Create Invite
- Use App Commands
- Use Activities
- Use External Apps

### Messaging
- Send Message & Posts
- Create Public Threads
- Use External Emojis
- Use External Stickers
- Send in Threads & Posts
- Create Private Threads
- Embed Links
- Attach Files
- Add Reactions
- Mention @everyone, @role
- Manage Messages
- Manage Threads & Posts
- Read Message History
- Send TTS Message
- Send Voice Message
- Create Polls

### Voice
- Connect
- Speak
- Video
- Use Soundboard
- Use External Sounds
- Use Voice Activity
- Priority Speaker
- Mute Members
- Deafen Members
- Move Members
- Set Voice Status
- Request to Speak

---

*Note: This plugin tracks categories via Pudel's database; manually creating or modifying category permissions through Discord after create/import via command will not be reflected in Pudel's settings panel.*  
*Note: You have a window-time for responding to components within 15 minutes, or your session will expire and you must start again.*

