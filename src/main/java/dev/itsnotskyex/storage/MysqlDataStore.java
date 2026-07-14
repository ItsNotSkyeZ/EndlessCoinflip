package dev.itsnotskyex.storage;

import com.mysql.cj.jdbc.Driver;
import dev.itsnotskyex.EndlessCoinflip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class MysqlDataStore extends SqlDataStore {

    static {
        try {
            Class.forName(Driver.class.getName());
        } catch (ClassNotFoundException ignored) {
        }
    }

    public MysqlDataStore(EndlessCoinflip plugin) {
        super(plugin);
    }

    @Override
    protected Connection openConnection() throws SQLException {
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
        }

        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + params, username, password);
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
                "pending_payout DOUBLE NOT NULL DEFAULT 0)";
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
