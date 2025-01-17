/*
 * VanishNoPacket
 * Copyright (C) 2011-2022 Matt Baxter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.kitteh.vanish.listeners;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.LazyMetadataValue;
import org.bukkit.metadata.LazyMetadataValue.CacheStrategy;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.vanish.VanishCheck;
import org.kitteh.vanish.VanishPerms;
import org.kitteh.vanish.VanishPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ListenPlayerJoin implements Listener {
    private final VanishPlugin plugin;
    private List<UUID> redisVanished = new ArrayList<>();

    public ListenPlayerJoin(@NonNull VanishPlugin instance) {
        this.plugin = instance;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(@NonNull AsyncPlayerPreLoginEvent event) {
        if(plugin.getRedis().exists("vanished-" + event.getUniqueId())) {
            if((boolean) plugin.getRedis().get("vanished-" + event.getUniqueId())) {
                redisVanished.add(event.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoinEarly(@NonNull PlayerJoinEvent event) {
        event.getPlayer().setMetadata("vanished", new LazyMetadataValue(this.plugin, CacheStrategy.NEVER_CACHE, new VanishCheck(this.plugin.getManager(), event.getPlayer().getName())));
        this.plugin.getManager().resetSeeing(event.getPlayer());
        if (redisVanished.contains(event.getPlayer().getUniqueId())) {
            this.plugin.getManager().toggleVanishQuiet(event.getPlayer(), false);
            this.plugin.hooksVanish(event.getPlayer());
        }
        redisVanished.remove(event.getPlayer().getUniqueId());
        this.plugin.hooksJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoinLate(@NonNull PlayerJoinEvent event) {
        final StringBuilder statusUpdate = new StringBuilder();
        if (redisVanished.contains(event.getPlayer().getUniqueId())) {
            String message = ChatColor.DARK_AQUA + "You have joined vanished.";
            if (VanishPerms.canVanish(event.getPlayer())) {
                message += " To appear: /vanish";
            }
            event.getPlayer().sendMessage(message);
            statusUpdate.append("vanished");
        }
        if (VanishPerms.joinWithoutAnnounce(event.getPlayer())) {
            this.plugin.getManager().getAnnounceManipulator().addToDelayedAnnounce(event.getPlayer().getName());
            if (this.plugin.isPaper()) {
                event.joinMessage(null);
            } else {
                //noinspection deprecation
                event.setJoinMessage(null);
            }
            if (statusUpdate.length() != 0) {
                statusUpdate.append(" and ");
            }
            statusUpdate.append("silently");
        }
        if (statusUpdate.length() != 0) {
            this.plugin.messageStatusUpdate(ChatColor.DARK_AQUA + event.getPlayer().getName() + " has joined " + statusUpdate.toString());
        }
    }
}
