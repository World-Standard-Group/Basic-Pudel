# Basic-Pudel
A central module of Basic Command Plugins for Pudel

## Motivation

The inspiration behind Pudel's creation stems from the discontinuation of the highly regarded Eli bot, developed by a talented developer. Recognizing the need for a replacement, Pudel was conceived to carry on the legacy of Eli, ensuring users could continue to enjoy a similar experience.

## Features
- Most Command and Feature had legacy from Eli
- Music Playback: Play music from various sources, including YouTube, SoundCloud, and more.
- [WIP] Moderation Tools: Kick, ban, mute, and manage user roles.
- [WIP] Fun Commands: Engage users with games, memes, and interactive commands.
- [WIP] Utility Commands: Provide useful tools like weather updates, time zones, and more.

## Dependencies

- JDA: [![maven-central](https://img.shields.io/maven-central/v/net.dv8tion/JDA?color=blue)](https://mvnrepository.com/artifact/net.dv8tion/JDA/latest)
- LavaPlayer: [![Maven Central](https://img.shields.io/maven-central/v/dev.arbjerg/lavaplayer?versionPrefix=2)](https://central.sonatype.com/artifact/dev.arbjerg/lavaplayer)
- youtube-source: ![GitHub Release](https://img.shields.io/github/v/release/lavalink-devs/youtube-source)

## Getting Started

To get started with Pudel, follow these steps:

1. Clone the [Pudel](https://github.com/World-Standard-Group/Pudel-Spring-Boot) repository to your local machine.
2. Ensure you have Java 25 installed on your system.
3. Follow up guides in the repository to set up and configure Pudel according to your needs.
4. Run the bot, Pudel had built-in command mostly related to Pudel own application control/settings.
5. Clone this repository to your local machine.
6. `mvn clean package` on needed module as .jar files.
7. Add the .jar files to Pudel's plugin directory.
8. Restart Pudel to load the new plugins.
9. Post http to Pudel's REST API to enable the plugins.

## Acknowledgments

Although developer Eli did not directly contribute to Pudel's development, the bot's features and behavior are rooted in Eli's legacy. Pudel aims to carry on the spirit of Eli by offering a reliable and enjoyable chatbot experience that captures the essence of the original project. Special thanks to the developer of Eli for inspiring this project.