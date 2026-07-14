package dev.itsnotskyex.storage;

import java.util.UUID;

public class LeaderboardEntry {

    public final UUID uuid;
    public final int wins;
    public final int losses;
    public final double totalWagered;
    public final double totalWon;
    public final double biggestWin;
    public final double biggestLoss;

    public LeaderboardEntry(UUID uuid, int wins, int losses, double totalWagered, double totalWon, double biggestWin, double biggestLoss) {
        this.uuid = uuid;
        this.wins = wins;
        this.losses = losses;
        this.totalWagered = totalWagered;
        this.totalWon = totalWon;
        this.biggestWin = biggestWin;
        this.biggestLoss = biggestLoss;
    }

    public double valueFor(String sortBy) {
        return switch (sortBy) {
            case "wins" -> wins;
            case "losses" -> losses;
            case "total-wagered" -> totalWagered;
            case "biggest-win" -> biggestWin;
            case "biggest-loss" -> biggestLoss;
            default -> totalWon;
        };
    }
}
