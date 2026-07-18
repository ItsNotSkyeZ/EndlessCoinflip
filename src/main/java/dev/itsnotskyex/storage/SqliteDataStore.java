package dev.itsnotskyex.storage;

import dev.itsnotskyex.EndlessCoinflip;
import org.sqlite.JDBC;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Backed by a single persistent SQLite connection. This connection must only ever be
 * touched by one thread at a time — callers are responsible for routing all access
 * through a single dedicated I/O thread (see PlayerDataManager) rather than the server's
 * main thread or any pool of threads.
 */
public class SqliteDataStore extends SqlDataStore {

    static {
        try {
            Class.forName(JDBC.class.getName());
        } catch (ClassNotFoundException ignored) {
        }
    }

    private final File dbFile;
    private Connection connection;

    public SqliteDataStore(EndlessCoinflip plugin) {
        super(plugin);
        this.dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "playerdata.db"));
    }

    @Override
    protected Connection acquireConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }

    @Override
    protected void releaseConnection(Connection connection) {
        // Kept open for reuse — this store's single connection is never returned to a pool.
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
        }
    }

    @Override
    protected String createPlayersTableSql() {
        return "CREATE TABLE IF NOT EXISTS cf_players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "wins INTEGER NOT NULL DEFAULT 0," +
                "losses INTEGER NOT NULL DEFAULT 0," +
                "total_wagered REAL NOT NULL DEFAULT 0," +
                "total_won REAL NOT NULL DEFAULT 0," +
                "biggest_win REAL NOT NULL DEFAULT 0," +
                "biggest_loss REAL NOT NULL DEFAULT 0," +
                "pending_payout REAL NOT NULL DEFAULT 0," +
                "pending_refund REAL NOT NULL DEFAULT 0," +
                "receiving_private_invites INTEGER NOT NULL DEFAULT 1)";
    }

    @Override
    protected String addReceivingPrivateInvitesColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN receiving_private_invites INTEGER NOT NULL DEFAULT 1";
    }

    @Override
    protected String addPendingRefundColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN pending_refund REAL NOT NULL DEFAULT 0";
    }

    @Override
    protected String createHistoryTableSql() {
        return "CREATE TABLE IF NOT EXISTS cf_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid VARCHAR(36) NOT NULL," +
                "opponent VARCHAR(64) NOT NULL," +
                "wager REAL NOT NULL," +
                "won INTEGER NOT NULL," +
                "vs_bot INTEGER NOT NULL," +
                "ts INTEGER NOT NULL)";
    }
}
