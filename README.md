![logo](src/main/resources/assets/lanannouncer/icon.png)

# LAN Announcer (Fabric)

Announces your Minecraft server to the local network (LAN) using UDP broadcast and multicast.

This mod is designed specifically for **Fabric dedicated servers**. It emulates the "Open to LAN" functionality of the Minecraft client, making your dedicated server appear automatically in the "Local World" section of the server list for players on the same network.

## Features

- **Zero Configuration**: Just drop it in and it works.
- **Dual Stack Support**: Works with both IPv4 and IPv6.
- **Smart Interface Selection**: Automatically detects your network's broadcast address.
- **Log Friendly**: Gracefully handles network issues (like restricted Docker networks) without spamming logs.

## Technical Details

Every 1.5 seconds, the mod sends a UDP payload:

```
[MOTD]<server motd>[/MOTD][AD]<server listen port>[/AD]
```

For example:

```
[MOTD]A Minecraft Server[/MOTD][AD]25565[/AD]
```

### Addressing
The mod sends these packets over UDP port **4445** to:
- The **broadcast address** of every active, non-loopback IPv4 network interface (e.g., `192.168.1.255`).
- The IPv4 multicast address `224.0.2.60` (as a fallback).
- The IPv6 multicast address `ff75:230::60`.

# Published on

- https://modrinth.com/mod/lan-announcer
- https://curseforge.com/minecraft/mc-mods/lan-announcer

# Docker Support

When running the server in a Docker container, the broadcast/multicast packets will typically not reach the local network unless the container is running in **host networking mode**.

To enable this in your `docker-compose.yml`:
```yaml
services:
  minecraft:
    network_mode: "host"
    # ... other settings
```

The mod now handles `Network is unreachable` errors gracefully by logging them once and suppressing further spam if host mode is not used.

# Credits

A majority of this project was created with the help of GPT-4.

# License

This project is licensed under the MIT License - see the [LICENSE](file:///c:/Users/paz/LANAnnouncer/LICENSE) file for details.
