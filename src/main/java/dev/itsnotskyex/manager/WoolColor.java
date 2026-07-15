package dev.itsnotskyex.manager;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum WoolColor {
    RED       ("Red",        ChatColor.RED + "",          Material.RED_WOOL,         Material.RED_STAINED_GLASS_PANE),
    ORANGE    ("Orange",     ChatColor.GOLD + "",         Material.ORANGE_WOOL,      Material.ORANGE_STAINED_GLASS_PANE),
    YELLOW    ("Yellow",     ChatColor.YELLOW + "",       Material.YELLOW_WOOL,      Material.YELLOW_STAINED_GLASS_PANE),
    LIME      ("Lime",       ChatColor.GREEN + "",        Material.LIME_WOOL,        Material.LIME_STAINED_GLASS_PANE),
    LIGHT_BLUE("Light Blue", ChatColor.AQUA + "",        Material.LIGHT_BLUE_WOOL,  Material.LIGHT_BLUE_STAINED_GLASS_PANE),
    PURPLE    ("Purple",     ChatColor.DARK_PURPLE + "",  Material.PURPLE_WOOL,      Material.PURPLE_STAINED_GLASS_PANE),
    PINK      ("Pink",       ChatColor.LIGHT_PURPLE + "", Material.PINK_WOOL,        Material.PINK_STAINED_GLASS_PANE),
    GRAY      ("Gray",       ChatColor.GRAY + "",         Material.GRAY_WOOL,        Material.GRAY_STAINED_GLASS_PANE),
    WHITE     ("White",      ChatColor.WHITE + "",        Material.WHITE_WOOL,       Material.WHITE_STAINED_GLASS_PANE);

    public final String displayName;
    public final String chatColor;
    public final Material woolMaterial;
    public final Material paneMaterial;

    WoolColor(String displayName, String chatColor, Material woolMaterial, Material paneMaterial) {
        this.displayName  = displayName;
        this.chatColor    = chatColor;
        this.woolMaterial = woolMaterial;
        this.paneMaterial = paneMaterial;
    }
}
