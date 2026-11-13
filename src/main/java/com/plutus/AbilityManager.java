package com.plutus;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityManager {

    private final PlutusMacePlugin plugin;
    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    private final Map<UUID, Long> pactCooldowns = new HashMap<>();
    private final Map<UUID, Set<UUID>> activePacts = new HashMap<>(); // Owner -> Targets
    private final Map<UUID, Integer> hitCounters = new HashMap<>();

    public AbilityManager(PlutusMacePlugin plugin) {
        this.plugin = plugin;
        startVisualTask();
    }

    public void handleAbility(Player player, boolean isSneaking) {
        if (isSneaking) {
            triggerPact(player);
        } else {
            triggerDash(player);
        }
    }

    private void triggerDash(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        int cd = plugin.getConfig().getInt("dash.cooldown-seconds") * 1000;

        if (dashCooldowns.getOrDefault(id, 0L) > now) {
            long remaining = (dashCooldowns.get(id) - now) / 1000;
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Dash Cooldown: " + remaining + "s"));
            return;
        }

        dashCooldowns.put(id, now + cd);

        // Physics
        Vector dir = player.getLocation().getDirection();
        double forward = plugin.getConfig().getDouble("dash.forward-impulse");
        double up = plugin.getConfig().getDouble("dash.upward-impulse");
        player.setVelocity(dir.multiply(forward).setY(up));

        // Effects
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.dash")), 1f, (float) plugin.getConfig().getDouble("dash.sound-pitch"));
        spawnRedBurst(player.getLocation());

        // Damage nearby
        double radius = plugin.getConfig().getDouble("dash.damage-radius");
        double dmg = plugin.getConfig().getDouble("dash.damage-amount");
        player.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && !e.equals(player))
                .forEach(e -> ((Player) e).damage(dmg, player));
    }

    private void triggerPact(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        int cd = plugin.getConfig().getInt("pact.cooldown-seconds") * 1000;

        if (pactCooldowns.getOrDefault(id, 0L) > now) {
            long remaining = (pactCooldowns.get(id) - now) / 1000;
            player.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<dark_red>Pact Cooldown: " + remaining + "s"));
            return;
        }

        double radius = plugin.getConfig().getDouble("pact.link-radius");
        int max = plugin.getConfig().getInt("pact.max-targets");
        Set<UUID> targets = new HashSet<>();

        player.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player && !e.equals(player))
                .limit(max)
                .forEach(e -> {
                    targets.add(e.getUniqueId());
                    e.getWorld().playSound(e.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.pact-link")), 1f, 1f);
                });

        if (targets.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>No targets nearby to link!"));
            return;
        }

        pactCooldowns.put(id, now + cd);
        activePacts.put(id, targets);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.pact-cast")), 1f, 0.5f);

        // Expiry task
        int durationTicks = plugin.getConfig().getInt("pact.duration-seconds") * 20;
        new BukkitRunnable() {
            @Override
            public void run() {
                activePacts.remove(id);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Your Blood Pact has faded."));
            }
        }.runTaskLater(plugin, durationTicks);
    }

    public void handleDamageSharing(Player victim, double damage) {
        UUID victimId = victim.getUniqueId();
        for (Map.Entry<UUID, Set<UUID>> entry : activePacts.entrySet()) {
            Set<UUID> targets = entry.getValue();
            if (targets.contains(victimId)) {
                // Share with others in the same pact
                for (UUID otherId : targets) {
                    if (otherId.equals(victimId)) continue;
                    Player other = plugin.getServer().getPlayer(otherId);
                    if (other != null && other.isOnline()) {
                        other.damage(damage); // Note: Simple damage call to avoid infinite loops if not careful
                        other.getWorld().spawnParticle(Particle.CRIMSON_SPORE, other.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.01);
                    }
                }
                break;
            }
        }
    }

    public void handleBloodToll(Player attacker) {
        String mode = plugin.getConfig().getString("self-damage-mode");
        double dmg = plugin.getConfig().getDouble("self-damage-amount");

        if ("EVERY_HIT".equalsIgnoreCase(mode)) {
            attacker.damage(dmg);
            playTollEffect(attacker);
        } else {
            UUID id = attacker.getUniqueId();
            int hits = hitCounters.getOrDefault(id, 0) + 1;
            if (hits >= plugin.getConfig().getInt("self-damage-hit-count")) {
                attacker.damage(dmg);
                playTollEffect(attacker);
                hitCounters.put(id, 0);
            } else {
                hitCounters.put(id, hits);
            }
        }
    }

    private void playTollEffect(Player p) {
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.toll-trigger")), 0.8f, 0.8f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
    }

    private void startVisualTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Set<UUID>> entry : activePacts.entrySet()) {
                    Player owner = plugin.getServer().getPlayer(entry.getKey());
                    if (owner == null || !owner.isOnline()) continue;

                    List<Player> targets = new ArrayList<>();
                    for (UUID tid : entry.getValue()) {
                        Player t = plugin.getServer().getPlayer(tid);
                        if (t != null && t.isOnline()) targets.add(t);
                    }

                    // Draw lines between targets (Pact formation)
                    for (int i = 0; i < targets.size(); i++) {
                        for (int j = i + 1; j < targets.size(); j++) {
                            drawLink(targets.get(i), targets.get(j));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void drawLink(Player a, Player b) {
        Location start = a.getLocation().add(0, 1, 0);
        Location end = b.getLocation().add(0, 1, 0);
        Vector vec = end.toVector().subtract(start.toVector());
        double dist = vec.length();
        vec.normalize().multiply(0.5);

        for (double d = 0; d < dist; d += 0.5) {
            Location point = start.clone().add(vec.clone().multiply(d));
            point.getWorld().spawnParticle(Particle.CRIMSON_SPORE, point, 1, 0, 0, 0, 0);
            if (Math.random() > 0.7) point.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0, 0, 0, 0.01);
        }
    }

    private void spawnRedBurst(Location loc) {
        loc.getWorld().spawnParticle(Particle.CRIMSON_SPORE, loc.add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 20, 0.3, 0.3, 0.3, 0.05);
    }
}
