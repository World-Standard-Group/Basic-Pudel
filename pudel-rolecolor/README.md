# Basic Pudel - Pudel's Role Color

**Version:** 1.0.1  
**Author:** Zazalng  
**For:** Pudel API 2.2.0+

Pudel's Role Color is a plugin module for the Pudel Discord Bot that allows users to easily set and manage custom color roles for their profiles. Built using the JDA library, it manages user assignments and role creation dynamically while tracking everything via a database to prevent cluttering the server with unused roles.

---

## ✨ Features
- Custom Colors via Hex: Users can customize their name color in the server by inputting standard 6-character hex color codes (e.g., FF0000 for red).
- Color Reset: Users can easily remove their custom color role at any time to return to their default appearance.
- Strict Color Enforcement: The plugin enforces a strict "1 color per user per guild" rule, automatically removing a user's previous color role when they select a new one.
- Role Reusability: To keep the server's role list clean, the plugin reuses existing custom color roles. If multiple users request the exact same hex color in the same server, they are assigned the same Discord role.
- Automatic Role Cleanup: The bot constantly monitors role assignments. If a managed color role drops to 0 assigned users, the plugin automatically deletes the role from both the Discord server and the database.

---

## 🛠️ Commands

### `/rolecolor <hex|reset>`
Create new role (if hex color does not exist)
Move to hex role (if hex color exist)
Remove role color (if parameter was reset)

---

*Note: 1 Guild may have roles up to 250 (Everything combine as well as @everyone is one of role counting).*