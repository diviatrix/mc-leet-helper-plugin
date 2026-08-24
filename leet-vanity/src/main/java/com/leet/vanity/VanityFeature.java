package com.leet.vanity;

import com.leet.core.CoreApi;
import com.leet.core.feature.AbstractFeature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class VanityFeature extends AbstractFeature {

    private static final String FEATURE_ID = "vanity";

    private boolean connectedEnabled;
    private String connectedPermission;
    private Set<Material> connectedTypes = EnumSet.noneOf(Material.class);

    private boolean sitEnabled;
    private String sitPermission;
    private double seatHeight;
    private Set<Material> seatBlocks = EnumSet.noneOf(Material.class);

    private boolean danceEnabled;
    private String dancePermission;

    public VanityFeature(CoreApi core, JavaPlugin owner) {
        super(core, owner);
    }

    @Override
    public String featureId() {
        return FEATURE_ID;
    }

    @Override
    protected void loadFeatureConfig(YamlConfiguration cfg) {
        loadConnected(cfg.getConfigurationSection("feature.connected"));
        loadSit(cfg.getConfigurationSection("feature.sit"));
        loadDance(cfg.getConfigurationSection("feature.dance"));
    }

    private void loadConnected(ConfigurationSection connected) {
        connectedEnabled = connected != null && connected.getBoolean("enabled", true);
        connectedPermission = connected == null ? "leet.vanity.connected" : connected.getString("permission", "leet.vanity.connected");
        registerCapabilityPermission(connectedPermission, connected == null ? "false" : connected.getString("default-permission", "false"));
        connectedTypes = EnumSet.noneOf(Material.class);
        if (connected == null) return;
        for (String name : connected.getStringList("openable-types")) {
            try {
                connectedTypes.add(Material.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                owner.getLogger().warning("Invalid material in connected openable-types: " + name);
            }
        }
    }

    private void loadSit(ConfigurationSection sit) {
        sitEnabled = sit != null && sit.getBoolean("enabled", true);
        sitPermission = sit == null ? "leet.vanity.sit" : sit.getString("permission", "leet.vanity.sit");
        registerCapabilityPermission(sitPermission, sit == null ? "false" : sit.getString("default-permission", "false"));
        seatHeight = sit != null ? sit.getDouble("seat-height", 0) : 0;
        seatBlocks = EnumSet.noneOf(Material.class);
        if (sit == null) return;
        for (String name : sit.getStringList("seat-blocks")) {
            try {
                seatBlocks.add(Material.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                owner.getLogger().warning("Invalid material in sit seat-blocks: " + name);
            }
        }
    }

    private void loadDance(ConfigurationSection dance) {
        danceEnabled = dance == null || dance.getBoolean("enabled", true);
        dancePermission = dance == null ? "leet.vanity.dance" : dance.getString("permission", "leet.vanity.dance");
        registerCapabilityPermission(dancePermission, dance == null ? "false" : dance.getString("default-permission", "false"));
    }

    private void registerCapabilityPermission(String node, String def) {
        org.bukkit.permissions.PermissionDefault pd = switch (def.toLowerCase(Locale.ROOT)) {
            case "true" -> org.bukkit.permissions.PermissionDefault.TRUE;
            case "op" -> org.bukkit.permissions.PermissionDefault.OP;
            default -> org.bukkit.permissions.PermissionDefault.FALSE;
        };
        try {
            Bukkit.getPluginManager().addPermission(new org.bukkit.permissions.Permission(node, pd));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public boolean danceAppliesTo(Player player) {
        return danceEnabled && check(player) && player.hasPermission(dancePermission);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!enabled) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!check(player)) return;

        if (sitEnabled && player.hasPermission(sitPermission) && seatBlocks.contains(block.getType())) {
            event.setCancelled(true);
            sit(player, block);
            return;
        }

        if (connectedEnabled && player.hasPermission(connectedPermission) && connectedTypes.contains(block.getType())) {
            syncLater(block);
        }
    }

    private void syncLater(Block block) {
        Bukkit.getScheduler().runTask(owner, () -> syncNeighbour(block));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!connectedEnabled || !enabled) return;
        if (event.getOldCurrent() == event.getNewCurrent()) return;
        if (connectedTypes.contains(event.getBlock().getType())) {
            syncLater(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        Entity stand = event.getDismounted();
        if (!(stand instanceof ArmorStand as)) return;
        if (!as.isMarker() || !as.isInvisible()) return;
        Entity rider = event.getEntity();
        rider.teleport(stand.getLocation().clone().add(0, 0.5, 0));
        stand.remove();
    }

    private void sit(Player player, Block block) {
        if (player.isInsideVehicle()) return;

        for (Entity e : block.getWorld().getNearbyEntities(block.getLocation(), 2, 2, 2)) {
            if (e instanceof ArmorStand stand && stand.getPassengers().contains(player)) {
                return;
            }
        }

        Location seat = block.getLocation().add(0.5, 0, 0.5);
        seat.setY(block.getY() + topOf(block) + seatHeight);

        ArmorStand stand = block.getWorld().spawn(seat, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setInvulnerable(true);
            s.setGravity(false);
            s.setMarker(true);
            s.setSmall(false);
            s.setSilent(true);
            s.setCollidable(false);
            s.setCanTick(false);
        });

        float yaw = player.getLocation().getYaw();
        if (block.getBlockData() instanceof Directional dir) {
            yaw = angleFor(dir.getFacing());
        }
        stand.getLocation().setYaw(yaw);
        stand.addPassenger(player);
    }

    private double topOf(Block block) {
        if (block.getBlockData() instanceof Slab slab
            && slab.getType() == Slab.Type.BOTTOM) {
            return 0.5;
        }
        return 1.0;
    }

    private static float angleFor(BlockFace face) {
        return switch (face) {
            case NORTH -> 180;
            case EAST -> 270;
            case SOUTH -> 0;
            case WEST -> 90;
            default -> 0;
        };
    }

    private void syncNeighbour(Block block) {
        Block other = partner(block);
        if (other == null) return;
        if (!connectedTypes.contains(other.getType())) return;
        if (!(block.getBlockData() instanceof Openable target)) return;
        setDoorOpen(other, target.isOpen());
    }

    private void setDoorOpen(Block door, boolean open) {
        Block bottom = door;
        if (door.getBlockData() instanceof Bisected b
            && b.getHalf() == Bisected.Half.TOP) {
            bottom = door.getRelative(BlockFace.DOWN);
        }
        if (!(bottom.getBlockData() instanceof Openable d)) return;
        BlockData db = bottom.getBlockData().clone();
        ((Openable) db).setOpen(open);
        bottom.setBlockData(db, false);
        Block top = bottom.getRelative(BlockFace.UP);
        if (top.getBlockData() instanceof Openable) {
            BlockData tb = top.getBlockData().clone();
            ((Openable) tb).setOpen(open);
            top.setBlockData(tb, false);
        }
    }

    private Block partner(Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Door ? doorPartner(block) : null;
    }

    private Block doorPartner(Block block) {
        Block base = block;
        if (block.getBlockData() instanceof Bisected b
            && b.getHalf() == Bisected.Half.TOP) {
            base = block.getRelative(BlockFace.DOWN);
            if (!(base.getBlockData() instanceof Door)) {
                return null;
            }
        }
        Directional dir = (Directional) base.getBlockData();
        for (BlockFace sideways : new BlockFace[] {
            rotateCw(dir.getFacing()), rotateCcw(dir.getFacing()) }) {
            Block side = base.getRelative(sideways);
            if (sameDoor(base, side)) {
                return side;
            }
        }
        return null;
    }

    private boolean sameDoor(Block a, Block b) {
        return b.getType() == a.getType()
            && b.getBlockData() instanceof Directional dirB
            && a.getBlockData() instanceof Directional dirA
            && dirA.getFacing() == dirB.getFacing();
    }

    private static BlockFace rotateCw(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> f;
        };
    }

    private static BlockFace rotateCcw(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> f;
        };
    }
}
