<div align="center">

![EndlessCoinflip](https://i.ibb.co/s9H4zM8t/089de5b2-8ff8-4e4c-9944-67505c5627eb.png)

# EndlessCoinflip
**PvP Coinflip with Bot Battles & Stats**

[![Spigot](https://img.shields.io/badge/Spigot-1.21+-orange)](https://www.spigotmc.org/resources/endlesscoinflip-pvp-coinflip-with-bot-battles-stats-1-21.137052)
[![Version](https://img.shields.io/badge/Version-0.0.6-blue)](https://github.com/ItsNotSkyeZ/EndlessCoinflip/releases)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---

## Overview
EndlessCoinflip is a clean, feature-rich coinflip plugin built for Paper or Spigot 1.21+ servers. Challenge other players to a coinflip, battle the built-in bot, and track your stats — all through a sleek GUI. Fully configurable with **Vault** support for any economy plugin.

---

## Features
- **PvP Coinflip & Bot Battles** — Challenge players or the built-in bot at any wager amount
- **Leaderboards** — Top players by wins, losses, wagered, biggest win and more via `/cf top`
- **Match History** — View your recent results in a paginated GUI
- **Player Stats** — Track wins, losses, total wagered, total won, biggest win and biggest loss
- **Server Tax** — Take a configurable percentage cut from the pot before paying out the winner
- **Big Win Broadcasts** — Server-wide announcements when a payout meets a configurable threshold
- **Database Support** — Choose between FILE, SQLite or MySQL with automatic migration and backups
- **Cooldowns & Match Expiry** — Prevent spam and auto-cancel unjoined matches after a set time
- **Join Confirmation** — A confirm screen before committing your wager to join a match
- **Wager Shortcuts** — Type `all`, `half`, `10k`, `1m` etc. instead of the full number
- **Animated Results** — Countdown timer and colour flip animation before the winner is revealed
- **Sound Effects** — Configurable sounds for winning, losing, rolling and the countdown
- **Disconnect Protection** — Matches cancelled and winnings held until next login if offline
- **Vault Economy** — Works with any Vault-compatible economy plugin
- **Fully Configurable** — Messages, sounds, wager limits, GUI text and feature toggles all in config

---

## Requirements
- **Paper/Spigot** 1.21+
- **Java** 21+
- **Vault** + a compatible economy plugin (e.g. EssentialsX)

---

## Installation
1. Download the latest jar from [Releases](https://github.com/ItsNotSkyeZ/EndlessCoinflip/releases)
2. Drop it into your server's `plugins/` folder
3. Restart your server
4. Configure `plugins/EndlessCoinflip/config.yml` and `messages.yml` to your liking
5. Run `/cf reload` to apply any config changes without restarting

---

## Commands
| Command | Description |
|---|---|
| `/cf` | Open the coinflip lobby |
| `/cf <amount>` | Create a new match with the specified wager |
| `/cf bot <amount>` | Start a bot battle instantly |
| `/cf cancel` | Cancel your active match and get your wager refunded |
| `/cf stats` | View your coinflip statistics |
| `/cf history` | View your recent coinflip results |
| `/cf top [page]` | View the leaderboard |
| `/cf help` | Show the help menu |
| `/cf reload` | Reload the config live *(admin only)* |

---

## Permissions
| Permission | Description | Default |
|---|---|---|
| `endlesscoinflip.use` | Allows use of all coinflip commands | `true` |
| `endlesscoinflip.admin` | Allows use of admin commands such as `/cf reload` | `op` |

---

## Configuration
EndlessCoinflip uses multiple config files for organisation:

- **`config.yml`** — General settings, wager limits, cooldowns, match expiry, database, feature toggles and GUI text
- **`messages.yml`** — All player-facing messages, fully configurable with `&` colour codes

Run `/cf reload` after making changes — no restart needed. New config options added in updates are automatically merged into your existing files without overwriting anything you've set.

### Database
Three storage options are available via `config.yml`:

```yaml
storage:
  type: FILE  # FILE, SQLITE, or MYSQL
```

Switching storage type automatically migrates your existing data and backs up the old storage — nothing is ever deleted.

---

## Building
```bash
git clone https://github.com/ItsNotSkyeZ/EndlessCoinflip.git
cd EndlessCoinflip
mvn clean package
```
The built jar will be in `target/`.

---

## Roadmap
- [ ] PlaceholderAPI — expose stats as placeholders for scoreboards, holograms and more
- [ ] Custom Economy — built-in economy option for servers without Vault
- [ ] Per-permission Wager Limits — different min/max wagers per rank or permission group
- [ ] Win Streak Tracking — track and display current and best win streaks
- [ ] Private Matches — invite a specific player instead of posting to the open lobby
- [ ] Spectating — allow players to watch an ongoing match in real time

---

<div align="center">

Made by **ItsNotSkyeX** — feedback and suggestions welcome!

[Spigot Page](https://www.spigotmc.org/resources/endlesscoinflip-pvp-coinflip-with-bot-battles-stats-1-21.137052) • [Wiki](https://github.com/ItsNotSkyeZ/EndlessCoinflip/wiki)

</div>
