package dev.itsnotskyex.storage;

import java.util.List;

public class LeaderboardPage {
    public final List<LeaderboardEntry> entries;
    public final int total;

    public LeaderboardPage(List<LeaderboardEntry> entries, int total) {
        this.entries = entries;
        this.total = total;
    }
}
