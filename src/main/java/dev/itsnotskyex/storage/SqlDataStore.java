package dev.itsnotskyex.storage;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.MatchHistoryEntry;
import dev.itsnotskyex.data.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class SqlDataStore implements PlayerDataStore {

    protected final EndlessCoinflip plugin;

    protected SqlDataStore(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    protected abstract String createPlayersTableSql();
    protected abstract String createHistoryTableSql();
    protected abstract String addReceivingPrivateInvitesColumnSql();
    protected abstract String addPendingRefundColumnSql();
    protected abstract String addCurrentStreakColumnSql();
    protected abstract String addBestStreakColumnSql();

    protected abstract Executor ioExecutor();

    protected abstract Connection acquireConnection() throws SQLException;

    protected abstract void releaseConnection(Connection connection) throws SQLException;

    private Connection connection() {
        try {
            return acquireConnection();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to open database connection: " + e.getMessage());
            return null;
        }
    }

    private void releaseQuietly(Connection conn) {
        try {
            releaseConnection(conn);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to release database connection: " + e.getMessage());
        }
    }

    @Override
    public void init() {
        Connection conn = connection();
        if (conn == null) return;
        try {
            initSchema(conn);
        } finally {
            releaseQuietly(conn);
        }
    }

    private void initSchema(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(createPlayersTableSql());
            st.executeUpdate(createHistoryTableSql());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialise database tables: " + e.getMessage());
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE INDEX cf_history_uuid_idx ON cf_history(uuid)");
        } catch (SQLException ignored) {
        }
        for (String col : new String[]{"wins", "losses", "total_wagered", "total_won", "biggest_win", "biggest_loss"}) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE INDEX cf_players_" + col + "_idx ON cf_players(" + col + ")");
            } catch (SQLException ignored) {
            }
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(addReceivingPrivateInvitesColumnSql());
        } catch (SQLException ignored) {
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(addPendingRefundColumnSql());
        } catch (SQLException ignored) {
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(addCurrentStreakColumnSql());
        } catch (SQLException ignored) {
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(addBestStreakColumnSql());
        } catch (SQLException ignored) {
        }
    }

    @Override
    public CompletableFuture<PlayerData> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> loadBlocking(uuid), ioExecutor());
    }

    private PlayerData loadBlocking(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        Connection conn = connection();
        if (conn == null) return data;

        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT wins, losses, total_wagered, total_won, biggest_win, biggest_loss, current_streak, best_streak, pending_payout, pending_refund, receiving_private_invites FROM cf_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setWins(rs.getInt("wins"));
                        data.setLosses(rs.getInt("losses"));
                        data.setTotalWagered(rs.getDouble("total_wagered"));
                        data.setTotalWon(rs.getDouble("total_won"));
                        data.setBiggestWin(rs.getDouble("biggest_win"));
                        data.setBiggestLoss(rs.getDouble("biggest_loss"));
                        data.setCurrentStreak(rs.getInt("current_streak"));
                        data.setBestStreak(rs.getInt("best_streak"));
                        data.setPendingPayout(rs.getDouble("pending_payout"));
                        data.setPendingRefund(rs.getDouble("pending_refund"));
                        data.setReceivingPrivateInvites(rs.getBoolean("receiving_private_invites"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load player data for " + uuid + ": " + e.getMessage());
                return data;
            }

            List<MatchHistoryEntry> history = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT opponent, wager, won, vs_bot, ts FROM cf_history WHERE uuid = ? ORDER BY ts DESC")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(new MatchHistoryEntry(
                                rs.getString("opponent"),
                                rs.getDouble("wager"),
                                rs.getBoolean("won"),
                                rs.getBoolean("vs_bot"),
                                rs.getLong("ts")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load history for " + uuid + ": " + e.getMessage());
            }
            data.loadHistory(history);
            return data;
        } finally {
            releaseQuietly(conn);
        }
    }

    @Override
    public CompletableFuture<Void> save(PlayerData data) {
        return CompletableFuture.runAsync(() -> saveBlocking(data), ioExecutor());
    }

    private void saveBlocking(PlayerData data) {
        Connection conn = connection();
        if (conn == null) return;

        try {
            conn.setAutoCommit(false);

            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM cf_players WHERE uuid = ?")) {
                ps.setString(1, data.getUuid().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE cf_players SET wins=?, losses=?, total_wagered=?, total_won=?, biggest_win=?, biggest_loss=?, current_streak=?, best_streak=?, pending_payout=?, pending_refund=?, receiving_private_invites=? WHERE uuid=?")) {
                    ps.setInt(1, data.getWins());
                    ps.setInt(2, data.getLosses());
                    ps.setDouble(3, data.getTotalWagered());
                    ps.setDouble(4, data.getTotalWon());
                    ps.setDouble(5, data.getBiggestWin());
                    ps.setDouble(6, data.getBiggestLoss());
                    ps.setInt(7, data.getCurrentStreak());
                    ps.setInt(8, data.getBestStreak());
                    ps.setDouble(9, data.getPendingPayout());
                    ps.setDouble(10, data.getPendingRefund());
                    ps.setBoolean(11, data.isReceivingPrivateInvites());
                    ps.setString(12, data.getUuid().toString());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO cf_players (uuid, wins, losses, total_wagered, total_won, biggest_win, biggest_loss, current_streak, best_streak, pending_payout, pending_refund, receiving_private_invites) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, data.getUuid().toString());
                    ps.setInt(2, data.getWins());
                    ps.setInt(3, data.getLosses());
                    ps.setDouble(4, data.getTotalWagered());
                    ps.setDouble(5, data.getTotalWon());
                    ps.setDouble(6, data.getBiggestWin());
                    ps.setDouble(7, data.getBiggestLoss());
                    ps.setInt(8, data.getCurrentStreak());
                    ps.setInt(9, data.getBestStreak());
                    ps.setDouble(10, data.getPendingPayout());
                    ps.setDouble(11, data.getPendingRefund());
                    ps.setBoolean(12, data.isReceivingPrivateInvites());
                    ps.executeUpdate();
                }
            }

            if (data.isHistoryDirty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cf_history WHERE uuid = ?")) {
                    ps.setString(1, data.getUuid().toString());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO cf_history (uuid, opponent, wager, won, vs_bot, ts) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (MatchHistoryEntry entry : data.getHistory()) {
                        ps.setString(1, data.getUuid().toString());
                        ps.setString(2, entry.opponentName);
                        ps.setDouble(3, entry.wager);
                        ps.setBoolean(4, entry.won);
                        ps.setBoolean(5, entry.vsBot);
                        ps.setLong(6, entry.timestamp);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            conn.commit();
            data.markHistoryClean();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save player data for " + data.getUuid() + ": " + e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
            releaseQuietly(conn);
        }
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(String sortBy, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> getLeaderboardBlocking(sortBy, limit, offset), ioExecutor());
    }

    private List<LeaderboardEntry> getLeaderboardBlocking(String sortBy, int limit, int offset) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        Connection conn = connection();
        if (conn == null) return entries;

        try {
            String column = switch (sortBy) {
                case "wins" -> "wins";
                case "losses" -> "losses";
                case "total-wagered" -> "total_wagered";
                case "biggest-win" -> "biggest_win";
                case "biggest-loss" -> "biggest_loss";
                case "streak" -> "best_streak";
                default -> "total_won";
            };

            String sql = "SELECT uuid, wins, losses, total_wagered, total_won, biggest_win, biggest_loss, best_streak FROM cf_players ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new LeaderboardEntry(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getInt("wins"),
                                rs.getInt("losses"),
                                rs.getDouble("total_wagered"),
                                rs.getDouble("total_won"),
                                rs.getDouble("biggest_win"),
                                rs.getDouble("biggest_loss"),
                                rs.getInt("best_streak")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load leaderboard: " + e.getMessage());
            }
            return entries;
        } finally {
            releaseQuietly(conn);
        }
    }

    @Override
    public CompletableFuture<Integer> getLeaderboardSize() {
        return CompletableFuture.supplyAsync(this::getLeaderboardSizeBlocking, ioExecutor());
    }

    private int getLeaderboardSizeBlocking() {
        Connection conn = connection();
        if (conn == null) return 0;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cf_players")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to count leaderboard size: " + e.getMessage());
        } finally {
            releaseQuietly(conn);
        }
        return 0;
    }

    @Override
    public List<UUID> getAllUuids() {
        List<UUID> uuids = new ArrayList<>();
        Connection conn = connection();
        if (conn == null) return uuids;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid FROM cf_players")) {
            while (rs.next()) {
                try {
                    uuids.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list players: " + e.getMessage());
        } finally {
            releaseQuietly(conn);
        }
        return uuids;
    }

    @Override
    public String backup() {
        Connection conn = connection();
        if (conn == null) return null;

        try {
            String suffix = "";
            int n = 1;
            while (tableExists(conn, "cf_players_backup" + suffix)) {
                suffix = "_" + n;
                n++;
            }
            String playersBackup = "cf_players_backup" + suffix;
            String historyBackup = "cf_history_backup" + suffix;

            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE cf_players RENAME TO " + playersBackup);
                st.executeUpdate("ALTER TABLE cf_history RENAME TO " + historyBackup);
                return playersBackup + "/" + historyBackup + " tables";
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to rename old tables for backup: " + e.getMessage());
                return null;
            }
        } finally {
            releaseQuietly(conn);
        }
    }

    private boolean tableExists(Connection conn, String table) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
}
