package org.kitteh.vanish.hooks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.event.spout.SpoutCraftEnableEvent;
import org.getspout.spoutapi.gui.*;
import org.getspout.spoutapi.player.SpoutPlayer;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

public class SpoutCraftHook implements Listener {

    private class PlayerData {
        public String skin, cloak, title;

        public PlayerData(String skin, String cloak, String title) {
            this.skin = skin;
            this.cloak = cloak;
            this.title = title;
        }
    }

    private class StatusBar {
        private final GenericLabel label;
        private final GenericGradient box;
        SpoutPlayer player;

        public StatusBar(SpoutPlayer player) {
            this.label = (GenericLabel) new GenericLabel(ChatColor.DARK_AQUA + "Invisible").setAnchor(WidgetAnchor.BOTTOM_LEFT).setX(20).setY(-20).setHeight(10).setWidth(40);
            this.box = (GenericGradient) new GenericGradient().setTopColor(SpoutCraftHook.this.boxColor).setBottomColor(SpoutCraftHook.this.boxColor).setX(18).setY(-22).setHeight(12).setWidth(45).setAnchor(WidgetAnchor.BOTTOM_LEFT).setPriority(RenderPriority.High);
            this.player = player;
        }

        public void assign() {
            this.player.getMainScreen().attachWidget(SpoutCraftHook.this.plugin, this.box).attachWidget(SpoutCraftHook.this.plugin, this.label);
        }

        public void remove() {
            this.player.getMainScreen().removeWidget(this.box).removeWidget(this.label);
        }
    }

    private boolean enabled;

    private final VanishPlugin plugin;

    private HashMap<String, String> cloaks;
    private HashMap<String, String> skins;

    private HashMap<String, String> titles;

    private HashMap<String, PlayerData> playerDataMap;

    private Color boxColor;

    private HashMap<String, StatusBar> bars;

    public SpoutCraftHook(VanishPlugin plugin) {
        this.plugin = plugin;
    }

    public void onPluginDisable() {
        if (!this.enabled) {
            return;
        }
        if (this.plugin.getServer().getPluginManager().isPluginEnabled("Spout")) {
            for (final SpoutPlayer player : SpoutManager.getOnlinePlayers()) {
                player.getMainScreen().removeWidgets(this.plugin);
            }
        }
    }

    public void onPluginEnable(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            if (!this.plugin.getServer().getPluginManager().isPluginEnabled("Spout")) {
                this.enabled = false;
                this.plugin.getLogger().info("SpoutCraft not running but you wanted SpoutCraft features.");
            }
            this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
            this.boxColor = new Color(0.1f, 0.1f, 0.1f, 0.4f);
            this.cloaks = new HashMap<String, String>();
            this.skins = new HashMap<String, String>();
            this.titles = new HashMap<String, String>();
            this.bars = new HashMap<String, StatusBar>();
            this.init();
        }
    }
    
    @EventHandler
    public void onSpoutCraftEnable(SpoutCraftEnableEvent event) {
        final SpoutPlayer newPlayer=event.getPlayer();
        if (!VanishPerms.canSeeAll(newPlayer)) {
            return;
        }
        for (final SpoutPlayer p : SpoutManager.getOnlinePlayers()) {
            if (this.plugin.getManager().isVanished(p.getName())) {
                PlayerData data = this.playerDataMap.get(p.getName());
                if (data == null) {
                    data = this.initPlayer(p);
                }
                this.playerUpdate(p, data, newPlayer);
            }
        }
        if (this.plugin.getManager().isVanished(newPlayer.getName())) {
            this.vanish(newPlayer);
        }
    }

    public void playerQuit(Player player) {
        if (!this.enabled) {
            return;
        }
        final String name = player.getName();
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
            @Override
            public void run() {
                SpoutCraftHook.this.bars.remove(name);
            }
        }, 1);
    }

    public void unvanish(Player revealing) {
        if (!this.enabled) {
            return;
        }
        final SpoutPlayer revealingPlayer = SpoutManager.getPlayer(revealing);
        this.removeStatusBar(revealingPlayer);
        for (final SpoutPlayer player : SpoutManager.getOnlinePlayers()) {
            if ((player != null) && player.hasPermission("vanish.see") && player.isSpoutCraftEnabled()) {
                revealingPlayer.resetSkinFor(player);
                revealingPlayer.resetCapeFor(player);
                revealingPlayer.resetTitleFor(player);
            }
        }
    }

    public void vanish(Player vanishing) {
        if (!this.enabled) {
            return;
        }
        this.attachStatusBar(SpoutManager.getPlayer(vanishing));
        PlayerData data = this.playerDataMap.get(vanishing.getName());
        if (data == null) {
            data = this.initPlayer(vanishing);
        }
        if ((data.skin != null) && (data.cloak != null) && (data.title != null)) {
            return;
        }
        for (final SpoutPlayer player : SpoutManager.getOnlinePlayers()) {
            this.playerUpdate(SpoutManager.getPlayer(vanishing), data, player);
        }
    }

    private void attachStatusBar(SpoutPlayer player) {
        if (player.isSpoutCraftEnabled() && VanishPerms.canSeeSpoutStatus(player)) {
            this.getStatusBar(player).assign();
        }
    }

    private StatusBar getStatusBar(SpoutPlayer player) {
        StatusBar bar = this.bars.get(player.getName());
        if (bar == null) {
            bar = new StatusBar(player);
            this.bars.put(player.getName(), bar);
        }
        return bar;
    }

    private void init() {
        this.playerDataMap = new HashMap<String, PlayerData>();
        final File confFile = new File(this.plugin.getDataFolder(), "spoutcraft.yml");
        final FileConfiguration config = YamlConfiguration.loadConfiguration(confFile);
        config.options().copyDefaults(true);
        final InputStream stream = this.plugin.getResource("spoutcraft.yml");
        if (stream == null) {
            this.plugin.log("Defaults for spoutcraft.yml not loaded");
            this.plugin.log("The /reload command is not fully supported by this plugin or Spout");
            this.enabled = false;
            return;
        }
        config.setDefaults(YamlConfiguration.loadConfiguration(stream));
        try {
            config.save(confFile);
        } catch (final IOException e) {
            this.plugin.getServer().getLogger().log(Level.SEVERE, "Could not save spoutcraft.yml", e);
        }
        for (final String skinGroup : config.getConfigurationSection("skins").getKeys(false)) {
            this.skins.put(skinGroup, config.getString("skins." + skinGroup));
        }
        for (final String cloakGroup : config.getConfigurationSection("cloaks").getKeys(false)) {
            this.cloaks.put(cloakGroup, config.getString("cloaks." + cloakGroup));
        }
        for (final String titleGroup : config.getConfigurationSection("titles").getKeys(false)) {
            this.titles.put(titleGroup, config.getString("titles." + titleGroup).replace("%r", "\n").replace("&&", "�"));
        }
    }

    private PlayerData initPlayer(Player player) {
        String skin = null;
        String cloak = null;
        String title = null;
        for (final String skinGroup : new HashSet<String>(this.skins.keySet())) {
            if (player.hasPermission("vanish.spout.skin." + skinGroup)) {
                skin = this.skins.get(skinGroup);
                break;
            }
        }
        for (final String cloakGroup : new HashSet<String>(this.cloaks.keySet())) {
            if (player.hasPermission("vanish.spout.cloak." + cloakGroup)) {
                cloak = this.cloaks.get(cloakGroup);
                break;
            }
        }
        for (final String titleGroup : new HashSet<String>(this.titles.keySet())) {
            if (player.hasPermission("vanish.spout.title." + titleGroup)) {
                title = this.titles.get(titleGroup).replace("%n", player.getName());
                break;
            }
        }
        final PlayerData playerData = new PlayerData(skin, cloak, title);
        this.playerDataMap.put(player.getName(), playerData);
        return playerData;
    }

    /**
     * For player target, update spout vanishness with data.
     * 
     * @param vanished
     * @param data
     * @param target
     */
    private void playerUpdate(SpoutPlayer vanished, PlayerData data, SpoutPlayer target) {
        if ((target != null) && target.hasPermission("vanish.see") && target.isSpoutCraftEnabled()) {
            if (data.skin != null) {
                vanished.setSkinFor(target, data.skin);
            }
            if (data.cloak != null) {
                vanished.setCapeFor(target, data.cloak);
            }
            if (data.title != null) {
                vanished.setTitleFor(target, data.title);
            }
        }
    }

    private void removeStatusBar(SpoutPlayer player) {
        if (player.isSpoutCraftEnabled() && VanishPerms.canSeeSpoutStatus(player)) {
            this.getStatusBar(player).remove();
        }
    }

}