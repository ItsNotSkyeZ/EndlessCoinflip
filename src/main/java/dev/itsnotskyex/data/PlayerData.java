package dev.itsnotskyex.data;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerData {

    private final UUID uuid;
    private int wins;
    private int losses;
    private double totalWagered;
    private double totalWon;
    private double biggestWin;
    private double biggestLoss;
    private double pendingPayout;
    private double pendingRefund;
    private boolean receivingPrivateInvites = true;
    private final List<MatchHistoryEntry> history = new CopyOnWriteArrayList<>();

    public PlayerData(UUID uuid) { this.uuid = uuid; }

    public List<MatchHistoryEntry> getHistory() { return Collections.unmodifiableList(history); }

    public void addHistoryEntry(MatchHistoryEntry entry, int maxStored) {
        history.add(0, entry);
        while (history.size() > maxStored) history.remove(history.size() - 1);
    }

    public void loadHistory(List<MatchHistoryEntry> entries) {
        history.clear();
        history.addAll(entries);
    }

    public UUID getUuid()                  { return uuid; }
    public int getWins()                   { return wins; }
    public void setWins(int w)             { this.wins = w; }
    public int getLosses()                 { return losses; }
    public void setLosses(int l)           { this.losses = l; }
    public double getTotalWagered()        { return totalWagered; }
    public void setTotalWagered(double v)  { this.totalWagered = v; }
    public double getTotalWon()            { return totalWon; }
    public void setTotalWon(double v)      { this.totalWon = v; }
    public double getBiggestWin()          { return biggestWin; }
    public void setBiggestWin(double v)    { this.biggestWin = v; }
    public double getBiggestLoss()         { return biggestLoss; }
    public void setBiggestLoss(double v)   { this.biggestLoss = v; }
    public double getPendingPayout()       { return pendingPayout; }
    public void setPendingPayout(double v) { this.pendingPayout = v; }
    public double getPendingRefund()       { return pendingRefund; }
    public void setPendingRefund(double v) { this.pendingRefund = v; }
    public boolean isReceivingPrivateInvites()      { return receivingPrivateInvites; }
    public void setReceivingPrivateInvites(boolean v) { this.receivingPrivateInvites = v; }
}
