<div align="center">

![EndlessCoinflip](https://i.ibb.co/s9H4zM8t/089de5b2-8ff8-4e4c-9944-67505c5627eb.png)

# EndlessCoinflip
**PvP Coinflip with Bot Battles & Stats**

[![Spigot](https://img.shields.io/badge/Spigot-1.21+-orange)](https://www.spigotmc.org/resources/endlesscoinflip-pvp-coinflip-with-bot-battles-stats-1-21.137052)
[![Version](https://img.shields.io/badge/Version-0.1.0-blue)](https://github.com/ItsNotSkyeZ/EndlessCoinflip/releases)
[![Discord](https://img.shields.io/discord/1527399059428610288?label=Discord&logo=discord&color=5865F2)](https://discord.gg/c5q8rBZQ5A)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---

## Overview
EndlessCoinflip is a clean, feature-rich coinflip plugin built for Paper or Spigot 1.21+ servers. Challenge other players to a coinflip, battle the built-in bot, and track your stats — all through a sleek GUI. Fully configurable with **Vault** support for any economy plugin.

---

## Features
- **PvP Coinflip & Bot Battles** — Challenge players or the built-in bot at any wager amount
- **Private Matches** — Challenge a specific player directly with `/cf <player> <wager>`
- **Leaderboards** — Top players by wins, losses, wagered, biggest win and more via `/cf top`
- **Match History** — View your recent results in a paginated GUI
- **Player Stats** — Track wins, losses, total wagered, total won, biggest win, biggest loss, and current/best win streak
- **Server Tax** — Take a configurable percentage cut from the pot before paying out the winner, with an optional permission to exempt specific players
- **PlaceholderAPI Support** — Expose wins, losses, wagered, won, biggest win/loss, win streak, ratio and leaderboard rank as placeholders
- **Big Win & Win Streak Broadcasts** — Server-wide announcements when a payout meets a configurable threshold, or a player hits a win streak milestone
- **Per-Permission Wager Limits** — Give VIP/donor ranks a higher or lower wager limit than everyone else
- **Database Support** — Choose between FILE, SQLite or MySQL with automatic migration and backups
- **Cooldowns & Match Expiry** — Prevent spam and auto-cancel unjoined matches after a set time
- **Join Confirmation** — A confirm screen before committing your wager to join a match
- **Wager Shortcuts** — Type `all`, `half`, `10k`, `1m` etc, instead of the full number
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
| `/cf <player> <amount>` | Challenge a specific player to a private match |
| `/cf bot <amount>` | Start a bot battle instantly |
| `/cf accept` | Accept a pending private match invite |
| `/cf deny` | Deny a pending private match invite |
| `/cf cancel` | Cancel your active match or pending invite |
| `/cf toggle` | Toggle whether you receive private match invites |
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
| `endlesscoinflip.wager.<group>` | Sets a per-permission max wager override | `false` |
| `endlesscoinflip.notax` | Exempts you from the server tax when you win a coinflip | `false` |

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

### PlaceholderAPI
If PlaceholderAPI is installed, EndlessCoinflip registers these placeholders automatically — no setup needed:

| Placeholder | Description |
|---|---|
| `%coinflip_wins%` | Player's win count |
| `%coinflip_losses%` | Player's loss count |
| `%coinflip_total_wagered%` | Player's total amount wagered |
| `%coinflip_total_won%` | Player's total amount won |
| `%coinflip_biggest_win%` | Player's biggest single win |
| `%coinflip_biggest_loss%` | Player's biggest single loss |
| `%coinflip_streak%` | Player's current win streak |
| `%coinflip_best_streak%` | Player's best win streak |
| `%coinflip_ratio%` | Player's win:loss ratio |
| `%coinflip_rank%` | Player's leaderboard position, refreshed every `placeholders.rank-refresh-seconds` |
| `%coinflip_total_matches%` | Player's total matches played (wins + losses) |
| `%coinflip_net_profit%` | Player's total won minus total wagered (can be negative) |

These only resolve for online players. Test them with `/papi parse <player> <placeholder>`.

For holograms and scoreboards showing the overall leaderboard (not tied to a specific viewer), use the `top_<sort-stat>_<position>_<field>` placeholders, e.g. `%coinflip_top_wins_1_name%`, `%coinflip_top_total_won_1_total_won%`, `%coinflip_top_biggest_loss_3_name%`. `<field>` defaults to `name` if omitted (e.g. `%coinflip_top_wins_1%`).

Each stat (`wins`, `losses`, `total_wagered`, `total_won`, `biggest_win`, `biggest_loss`, `streak`) is its own independently-ranked leaderboard — a "top by losses" hologram and a "top by wins" hologram can run side by side without affecting each other or `/cf top`. `<field>` can be any of `name`, `wins`, `losses`, `total_wagered`, `total_won`, `biggest_win`, `biggest_loss`, `streak`, `ratio`. All rankings refresh together every `placeholders.rank-refresh-seconds`.

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
- [ ] Spectating — allow players to watch an ongoing match in real time

---

<div align="center">

Made by **ItsNotSkyeX** — feedback and suggestions welcome!

[Spigot Page](https://www.spigotmc.org/resources/endlesscoinflip-pvp-coinflip-with-bot-battles-stats-1-21.137052) • [Wiki](https://github.com/ItsNotSkyeZ/EndlessCoinflip/wiki) • [Discord](https://discord.gg/c5q8rBZQ5A)

</div>
