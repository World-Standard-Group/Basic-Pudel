# Basic Pudel - Pudel's Music
Pudel's Music plugin is a comprehensive, unified music player module designed for the Pudel Discord Bot. It provides users with a centralized "Music Box" interface built entirely using Discord's modern Components v2 system.

## Features
- Single Command Interface: The entire music experience is accessed through a single /music slash command, which opens the interactive Music Box.
- Now Playing Display: Shows the current track's artwork, playback progress, and active loop or shuffle status.
- Playback Controls: Features interactive buttons to easily Pause, Skip, Loop (toggle between Queue, Track, or Off), and Shuffle the current queue.
- Advanced Queuing & Search: Users can add songs via a modal by pasting a URL or entering a search query. The search supports auto-detection, YouTube, and SoundCloud sources, returning a selectable menu of tracks.
- Queue Management: Includes a paginated queue view (displaying 10 tracks per page) where users can view upcoming songs, remove specific tracks, shuffle the remaining queue, or clear it entirely.
- Playback History: Features a paginated history view that logs and displays previously played tracks along with their timestamps.
- State Persistence: Saves the active queue and history to a database, which allows the bot to recover stale queues and resume playback even after a plugin reload or bot restart.
- Smart Voice Integration: Automatically joins the user's voice channel when a song is queued (if not already connected) and gracefully destroys the audio player if the bot leaves the channel.