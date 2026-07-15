package dev.itsnotskyex.data;

public class MatchHistoryEntry {

    public final String opponentName;
    public final double wager;
    public final boolean won;
    public final boolean vsBot;
    public final long timestamp;

    public MatchHistoryEntry(String opponentName, double wager, boolean won, boolean vsBot, long timestamp) {
        this.opponentName = opponentName;
        this.wager = wager;
        this.won = won;
        this.vsBot = vsBot;
        this.timestamp = timestamp;
    }
}
