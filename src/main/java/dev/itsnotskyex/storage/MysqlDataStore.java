package dev.itsnotskyex.storage;

import com.mysql.cj.jdbc.Driver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.itsnotskyex.EndlessCoinflip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MysqlDataStore extends SqlDataStore {

    static {
        try {
            Class.forName(Driver.class.getName());
        } catch (ClassNotFoundException ignored) {
        }
    }

    private final HikariDataSource dataSource;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "EndlessCoinflip-MySQL-IO");
        thread.setDaemon(true);
        return thread;
    });

    public MysqlDataStore(EndlessCoinflip plugin) {
        super(plugin);

        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "coinflip");
        String username = plugin.getConfig().getString("storage.mysql.username", "root");
        String password = plugin.getConfig().getString("storage.mysql.password", "");
        boolean useSsl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
        String params = "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8";

        try (Connection bootstrap = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + params, username, password);
             Statement st = bootstrap.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "`");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to ensure MySQL database exists: " + e.getMessage());
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + params);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("EndlessCoinflip-MySQL");
        config.setMaximumPoolSize(plugin.getConfig().getInt("storage.mysql.pool-size", 10));
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    protected Executor ioExecutor() {
        return ioExecutor;
    }

    @Override
    protected Connection acquireConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    protected void releaseConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        dataSource.close();
    }

    @Override
    protected String createPlayersTableSql() {
        return "CREATE TABLE IF NOT EXISTS cf_players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "wins INT NOT NULL DEFAULT 0," +
                "losses INT NOT NULL DEFAULT 0," +
                "total_wagered DOUBLE NOT NULL DEFAULT 0," +
                "total_won DOUBLE NOT NULL DEFAULT 0," +
                "biggest_win DOUBLE NOT NULL DEFAULT 0," +
                "biggest_loss DOUBLE NOT NULL DEFAULT 0," +
                "current_streak INT NOT NULL DEFAULT 0," +
                "best_streak INT NOT NULL DEFAULT 0," +
                "pending_payout DOUBLE NOT NULL DEFAULT 0," +
                "pending_refund DOUBLE NOT NULL DEFAULT 0," +
                "receiving_private_invites BOOLEAN NOT NULL DEFAULT TRUE)";
    }

    @Override
    protected String addReceivingPrivateInvitesColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN receiving_private_invites BOOLEAN NOT NULL DEFAULT TRUE";
    }

    @Override
    protected String addPendingRefundColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN pending_refund DOUBLE NOT NULL DEFAULT 0";
    }

    @Override
    protected String addCurrentStreakColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN current_streak INT NOT NULL DEFAULT 0";
    }

    @Override
    protected String addBestStreakColumnSql() {
        return "ALTER TABLE cf_players ADD COLUMN best_streak INT NOT NULL DEFAULT 0";
    }

    @Override
    protected String createHistoryTableSql() {
        return "CREATE TABLE IF NOT EXISTS cf_history (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL," +
                "opponent VARCHAR(64) NOT NULL," +
                "wager DOUBLE NOT NULL," +
                "won BOOLEAN NOT NULL," +
                "vs_bot BOOLEAN NOT NULL," +
                "ts BIGINT NOT NULL)";
    }
}
