package com.sweattypalms.skyblock.core.items.types.end.items;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.sweattypalms.skyblock.SkyBlock;
import com.sweattypalms.skyblock.core.events.def.SkyblockInteractEvent;
import com.sweattypalms.skyblock.core.items.builder.Rarity;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItem;
import com.sweattypalms.skyblock.core.items.builder.SkyblockItemType;
import com.sweattypalms.skyblock.core.items.builder.abilities.Ability;
import com.sweattypalms.skyblock.core.items.builder.abilities.IHasAbility;
import com.sweattypalms.skyblock.core.items.builder.abilities.TriggerType;
import com.sweattypalms.skyblock.core.items.builder.abilities.types.ITriggerableAbility;
import com.sweattypalms.skyblock.core.player.sub.stats.Stats;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.EntityRavager;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.phys.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.Event;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

public class SnowmanScepter extends SkyblockItem implements IHasAbility {
    public static final String ID = "snowman_scepter";

    private static final Map<Stats, Double> STATS = Map.of(
            Stats.DAMAGE, 80d,
            Stats.INTELLIGENCE, 220d
    );

    public SnowmanScepter() {
        super(
                ID,
                "Snowman Scepter",
                Material.BLAZE_ROD,
                List.of(
                        "$7Weapon shell for Snowman skill",
                        "$7migration."
                ),
                STATS,
                Rarity.LEGENDARY,
                SkyblockItemType.WAND
        );
    }

    @Override
    public List<Ability> getAbilities() {
        return List.of(
                new BeastCharge(),
                new CorrosiveShot()
        );
    }

    public static class BeastCharge implements ITriggerableAbility {
        private static final String PROXY_TAG = "SkyblockBeastRideProxy";
        private static final String BEAST_RAVAGER_TAG = "SkyblockBeastChargeRavager";
        private static final long RIDE_DURATION_TICKS = 20L * 8L;
        private static final long INPUT_STALE_NANOS = 500_000_000L;
        private static final double INPUT_DEADZONE = 0.03D;
        private static final double INPUT_SMOOTHING_FACTOR = 0.35D;
        private static final double BASE_SPEED = 0.50D;
        private static final double SPRINT_SPEED_MULTIPLIER = 1.2D;
        private static final double MAX_HORIZONTAL_SPEED = 0.65D;

        private static final ConcurrentMap<UUID, RideRuntime> activeRides = new ConcurrentHashMap<>();
        private static final ConcurrentMap<UUID, UUID> proxyByPlayer = new ConcurrentHashMap<>();
        private static final ConcurrentMap<UUID, UUID> proxyByRavager = new ConcurrentHashMap<>();
        private static final ConcurrentMap<UUID, DriveInput> inputByPlayer = new ConcurrentHashMap<>();

        private static final Field JUMP_FIELD = resolveBooleanField("jumping", "bk");
        private static final Field SIDE_INPUT_FIELD = resolveFloatField("xxa", "bl");
        private static final Field FORWARD_INPUT_FIELD = resolveFloatField("zza", "bn");

        private static volatile PacketAdapter steerPacketListener;
        private static volatile boolean inputBridgeEnabled;
        private static volatile boolean parseWarningLogged;

        private static final class RideRuntime {
            private final Ravager ravager;
            private final ArmorStand proxy;
            private final int moveTaskId;
            private final int expiryTaskId;

            private RideRuntime(Ravager ravager, ArmorStand proxy, int moveTaskId, int expiryTaskId) {
                this.ravager = ravager;
                this.proxy = proxy;
                this.moveTaskId = moveTaskId;
                this.expiryTaskId = expiryTaskId;
            }
        }

        private record DriveInput(float side, float forward, boolean jumping, boolean dismounting, long receivedAtNanos) {
        }

        private record ParsedInput(float side, float forward, boolean jumping, boolean dismounting) {
        }

        @Override
        public String getName() {
            return "Primal Charge";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Summon a ravager mount and",
                    "$7charge forward for $a8s$7."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player player = interactEvent.getSkyblockPlayer().getPlayer();
            UUID playerId = player.getUniqueId();

            ensureInputBridge();
            cleanupRideForPlayer(playerId);

            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }

            Ravager ravager = spawnControlledRavager(player.getLocation());
            if (ravager == null || !ravager.isValid()) {
                return;
            }

            ravager.setInvulnerable(true);
            ravager.setSilent(true);
            ravager.setPersistent(false);
            ravager.setRemoveWhenFarAway(true);
            ravager.getScoreboardTags().add(BEAST_RAVAGER_TAG);

            ArmorStand proxy = spawnRideProxy(ravager, player);
            if (proxy == null) {
                ravager.remove();
                return;
            }

            if (!ravager.addPassenger(proxy) || !proxy.addPassenger(player)) {
                proxy.remove();
                ravager.remove();
                return;
            }

            BukkitRunnable moveTask = new BukkitRunnable() {
                @Override
                public void run() {
                    RideRuntime runtime = activeRides.get(playerId);
                    if (runtime == null) {
                        cancel();
                        return;
                    }

                    if (!isRuntimeValid(player, runtime)) {
                        cleanupRideForPlayer(playerId);
                        return;
                    }

                    DriveInput input = getFreshInput(playerId);
                    if (input != null && input.dismounting()) {
                        cleanupRideForPlayer(playerId);
                        return;
                    }
                }
            };

            int moveTaskId = moveTask.runTaskTimer(SkyBlock.getInstance(), 0L, 1L).getTaskId();
            int expiryTaskId = Bukkit.getScheduler()
                    .runTaskLater(SkyBlock.getInstance(), () -> cleanupRideForPlayer(playerId), RIDE_DURATION_TICKS)
                    .getTaskId();

            RideRuntime runtime = new RideRuntime(ravager, proxy, moveTaskId, expiryTaskId);
            activeRides.put(playerId, runtime);
            proxyByPlayer.put(playerId, proxy.getUniqueId());
            proxyByRavager.put(ravager.getUniqueId(), proxy.getUniqueId());

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
        }

        private Ravager spawnControlledRavager(Location location) {
            try {
                if (location.getWorld() == null) {
                    return null;
                }
                BeastChargeRavagerNms nms = new BeastChargeRavagerNms(location.getWorld());
                return nms.spawnAt(location);
            } catch (Throwable ex) {
                SkyBlock.getInstance().getLogger().warning("Failed spawning BeastCharge NMS ravager: " + ex.getMessage());
                return null;
            }
        }

        private boolean isRuntimeValid(Player player, RideRuntime runtime) {
            Ravager ravager = runtime.ravager;
            ArmorStand proxy = runtime.proxy;
            if (!player.isOnline() || player.isDead()) {
                return false;
            }
            if (ravager == null || proxy == null || !ravager.isValid() || ravager.isDead() || !proxy.isValid()) {
                return false;
            }
            return isRiderControllingRavager(player, ravager, proxy);
        }

        private boolean isRiderControllingRavager(Player rider, Ravager ravager, ArmorStand proxy) {
            if (ravager.getPassengers().contains(rider)) {
                return true;
            }
            Entity vehicle = rider.getVehicle();
            if (!(vehicle instanceof ArmorStand stand)) {
                return false;
            }
            if (!stand.getUniqueId().equals(proxy.getUniqueId())) {
                return false;
            }
            if (!stand.getPassengers().contains(rider)) {
                return false;
            }
            return ravager.getPassengers().contains(stand);
        }

        private static double resolveHorizontalSpeed(Player rider) {
            double speed = BASE_SPEED;
            if (rider.isSprinting()) {
                speed *= SPRINT_SPEED_MULTIPLIER;
            }
            return Math.min(speed, MAX_HORIZONTAL_SPEED);
        }

        private static double clampInput(float value) {
            if (value > 1.0F) return 1.0D;
            if (value < -1.0F) return -1.0D;
            return value;
        }

        private static float resolveSideInput(Player rider, DriveInput input) {
            if (input != null) {
                return input.side();
            }
            return getSideInput(((CraftPlayer) rider).getHandle());
        }

        private static float resolveForwardInput(Player rider, DriveInput input) {
            if (input != null) {
                return input.forward();
            }
            return getForwardInput(((CraftPlayer) rider).getHandle());
        }

        private static boolean resolveJumping(Player rider, DriveInput input) {
            if (input != null) {
                return input.jumping();
            }
            return isJumping(((CraftPlayer) rider).getHandle());
        }

        private static final class BeastChargeRavagerNms extends EntityRavager {
            private double smoothedSideInput;
            private double smoothedForwardInput;

            private BeastChargeRavagerNms(World world) {
                super(EntityTypes.aD, ((CraftWorld) world).getHandle());
                this.persist = false;
            }

            private Ravager spawnAt(Location location) {
                this.e(location.getX(), location.getY(), location.getZ());
                ((CraftWorld) location.getWorld()).getHandle().addFreshEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
                return (Ravager) this.getBukkitEntity();
            }

            @Override
            public EntityLiving cL() {
                net.minecraft.world.entity.Entity passenger = this.cO();
                if (passenger instanceof EntityPlayer player) {
                    return player;
                }
                if (passenger != null) {
                    net.minecraft.world.entity.Entity nestedPassenger = passenger.cO();
                    if (nestedPassenger instanceof EntityPlayer player) {
                        return player;
                    }
                }
                return null;
            }

            @Override
            public boolean cU() {
                return this.cL() instanceof EntityPlayer || super.cU();
            }

            @Override
            public void b_() {
                EntityPlayer rider = null;
                if (this.cL() instanceof EntityPlayer player) {
                    rider = player;
                    this.setTarget(null, EntityTargetEvent.TargetReason.CUSTOM, true);
                    this.J().n();
                    Player bukkitRider = (Player) player.getBukkitEntity();
                    this.bk = resolveJumping(bukkitRider, getFreshInput(bukkitRider.getUniqueId()));
                }

                super.b_();

                if (rider == null) {
                    smoothedSideInput = 0.0D;
                    smoothedForwardInput = 0.0D;
                }
            }

            @Override
            protected void a(EntityHuman rider, Vec3D input) {
                Location riderLoc = rider.getBukkitEntity().getLocation();
                float yaw = riderLoc.getYaw();
                float pitch = riderLoc.getPitch() * 0.5F;
                this.a(yaw, pitch);
                this.o(yaw);
                this.n(yaw);
                this.aV = yaw;
                this.aX = yaw;
                this.M = yaw;
            }

            @Override
            protected Vec3D b(EntityHuman rider, Vec3D input) {
                if (!(rider instanceof EntityPlayer player)) {
                    return Vec3D.b;
                }

                Player bukkitRider = (Player) player.getBukkitEntity();
                DriveInput driveInput = getFreshInput(bukkitRider.getUniqueId());
                float forwardInput = resolveForwardInput(bukkitRider, driveInput);
                float sideInput = resolveSideInput(bukkitRider, driveInput);

                if (Math.abs(sideInput) < INPUT_DEADZONE) {
                    sideInput = 0.0F;
                }
                if (Math.abs(forwardInput) < INPUT_DEADZONE) {
                    forwardInput = 0.0F;
                }

                if (forwardInput <= 0.0F) {
                    forwardInput *= 0.25F;
                }

                double targetSide = clampInput(sideInput);
                double targetForward = clampInput(forwardInput);
                double lenSq = targetSide * targetSide + targetForward * targetForward;
                if (lenSq > 1.0D) {
                    double invLen = 1.0D / Math.sqrt(lenSq);
                    targetSide *= invLen;
                    targetForward *= invLen;
                }

                smoothedSideInput += (targetSide - smoothedSideInput) * INPUT_SMOOTHING_FACTOR;
                smoothedForwardInput += (targetForward - smoothedForwardInput) * INPUT_SMOOTHING_FACTOR;

                return new Vec3D(smoothedSideInput, 0.0D, smoothedForwardInput);
            }

            @Override
            protected float e(EntityHuman rider) {
                double speed = BASE_SPEED;
                if (rider != null && rider.getBukkitEntity() instanceof Player player) {
                    speed = resolveHorizontalSpeed(player);
                }
                return (float) Math.min(speed, MAX_HORIZONTAL_SPEED);
            }
        }

        private ArmorStand spawnRideProxy(Ravager ravager, Player player) {
            try {
                ArmorStand stand = (ArmorStand) ravager.getWorld().spawnEntity(
                        ravager.getLocation().add(new Vector(0.0D, 0.05D, 0.0D)),
                        EntityType.ARMOR_STAND
                );
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setPersistent(false);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.setSmall(true);
                stand.setCustomNameVisible(false);
                stand.setCollidable(false);
                stand.getScoreboardTags().add(PROXY_TAG);

                stand.getPersistentDataContainer().set(proxyOwnerKey(), PersistentDataType.STRING, player.getUniqueId().toString());
                stand.getPersistentDataContainer().set(proxyRavagerKey(), PersistentDataType.STRING, ravager.getUniqueId().toString());
                return stand;
            } catch (Throwable ex) {
                SkyBlock.getInstance().getLogger().warning("Failed creating BeastCharge ride proxy: " + ex.getMessage());
                return null;
            }
        }

        public static void cleanupRideForPlayer(UUID playerId) {
            RideRuntime runtime = activeRides.remove(playerId);
            if (runtime != null) {
                destroyRuntime(runtime);
            }

            UUID proxyId = proxyByPlayer.remove(playerId);
            if (proxyId != null) {
                Entity mapped = findEntity(proxyId);
                if (mapped != null) {
                    cleanupProxyEntity(mapped);
                }
            } else {
                Entity fallback = findProxyByOwner(playerId);
                if (fallback != null) {
                    cleanupProxyEntity(fallback);
                }
            }
            inputByPlayer.remove(playerId);
        }

        public static void cleanupRideForRavager(Ravager ravager) {
            if (ravager == null) return;

            UUID mappedProxyId = proxyByRavager.remove(ravager.getUniqueId());
            if (mappedProxyId != null) {
                Entity mapped = findEntity(mappedProxyId);
                if (mapped != null) {
                    cleanupProxyEntity(mapped);
                }
            }

            for (Entity passenger : List.copyOf(ravager.getPassengers())) {
                if (isRideProxy(passenger)) {
                    cleanupProxyEntity(passenger);
                }
            }

            for (Map.Entry<UUID, RideRuntime> entry : activeRides.entrySet()) {
                RideRuntime runtime = entry.getValue();
                if (runtime.ravager != null && runtime.ravager.getUniqueId().equals(ravager.getUniqueId())) {
                    activeRides.remove(entry.getKey(), runtime);
                    destroyRuntime(runtime);
                    inputByPlayer.remove(entry.getKey());
                }
            }
        }

        public static void cleanupAllRides() {
            for (UUID playerId : List.copyOf(activeRides.keySet())) {
                cleanupRideForPlayer(playerId);
            }
            inputByPlayer.clear();
            disableInputBridge();
        }

        public static boolean isRideProxy(Entity entity) {
            if (!(entity instanceof ArmorStand stand)) {
                return false;
            }
            return stand.getScoreboardTags().contains(PROXY_TAG);
        }

        public static boolean isBeastRavager(Entity entity) {
            if (!(entity instanceof Ravager ravager)) {
                return false;
            }
            return ravager.getScoreboardTags().contains(BEAST_RAVAGER_TAG);
        }

        private static void destroyRuntime(RideRuntime runtime) {
            Bukkit.getScheduler().cancelTask(runtime.moveTaskId);
            Bukkit.getScheduler().cancelTask(runtime.expiryTaskId);

            if (runtime.proxy != null) {
                proxyByPlayer.values().removeIf(id -> id.equals(runtime.proxy.getUniqueId()));
            }

            if (runtime.ravager != null) {
                proxyByRavager.remove(runtime.ravager.getUniqueId());
            }

            if (runtime.proxy != null && runtime.proxy.isValid()) {
                runtime.proxy.remove();
            }

            if (runtime.ravager != null && runtime.ravager.isValid()) {
                runtime.ravager.remove();
            }
        }

        private static void cleanupProxyEntity(Entity proxyEntity) {
            if (!(proxyEntity instanceof ArmorStand stand) || !isRideProxy(stand)) {
                return;
            }

            String ownerRaw = stand.getPersistentDataContainer().get(proxyOwnerKey(), PersistentDataType.STRING);
            if (ownerRaw != null) {
                try {
                    proxyByPlayer.remove(UUID.fromString(ownerRaw), stand.getUniqueId());
                } catch (IllegalArgumentException ignored) {
                }
            }

            String ravagerRaw = stand.getPersistentDataContainer().get(proxyRavagerKey(), PersistentDataType.STRING);
            if (ravagerRaw != null) {
                try {
                    proxyByRavager.remove(UUID.fromString(ravagerRaw), stand.getUniqueId());
                } catch (IllegalArgumentException ignored) {
                }
            }

            stand.remove();
        }

        private static Entity findProxyByOwner(UUID ownerId) {
            String ownerRaw = ownerId.toString();
            for (World world : Bukkit.getWorlds()) {
                for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                    if (!isRideProxy(stand)) {
                        continue;
                    }

                    String foundOwner = stand.getPersistentDataContainer().get(proxyOwnerKey(), PersistentDataType.STRING);
                    if (ownerRaw.equals(foundOwner)) {
                        return stand;
                    }
                }
            }
            return null;
        }

        private static Entity findEntity(UUID entityId) {
            return Bukkit.getEntity(entityId);
        }

        private static NamespacedKey proxyOwnerKey() {
            return new NamespacedKey(SkyBlock.getInstance(), "beast-proxy-owner");
        }

        private static NamespacedKey proxyRavagerKey() {
            return new NamespacedKey(SkyBlock.getInstance(), "beast-proxy-ravager");
        }

        private static DriveInput getFreshInput(UUID playerId) {
            DriveInput input = inputByPlayer.get(playerId);
            if (input == null) return null;
            if (System.nanoTime() - input.receivedAtNanos() > INPUT_STALE_NANOS) {
                return null;
            }
            return input;
        }

        private static void ensureInputBridge() {
            if (inputBridgeEnabled) {
                return;
            }

            synchronized (BeastCharge.class) {
                if (inputBridgeEnabled) {
                    return;
                }
                try {
                    ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
                    steerPacketListener = new PacketAdapter(
                            SkyBlock.getInstance(),
                            ListenerPriority.NORMAL,
                            PacketType.Play.Client.STEER_VEHICLE
                    ) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            if (event.getPlayer() == null) return;

                            ParsedInput parsed = parseSteerInput(event.getPacket());
                            if (parsed == null) {
                                return;
                            }

                            UUID playerId = event.getPlayer().getUniqueId();
                            if (parsed.dismounting()) {
                                inputByPlayer.remove(playerId);
                                return;
                            }

                            inputByPlayer.put(playerId, new DriveInput(
                                    parsed.side(),
                                    parsed.forward(),
                                    parsed.jumping(),
                                    parsed.dismounting(),
                                    System.nanoTime()
                            ));
                        }
                    };
                    protocolManager.addPacketListener(steerPacketListener);
                    inputBridgeEnabled = true;
                } catch (Throwable ex) {
                    SkyBlock.getInstance().getLogger().warning(
                            "Failed enabling BeastCharge rider input bridge, fallback control will be limited: " + ex.getMessage()
                    );
                }
            }
        }

        private static void disableInputBridge() {
            if (steerPacketListener == null) {
                inputBridgeEnabled = false;
                return;
            }

            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(steerPacketListener);
            } catch (Throwable ignored) {
            } finally {
                steerPacketListener = null;
                inputBridgeEnabled = false;
                parseWarningLogged = false;
            }
        }

        private static ParsedInput parseSteerInput(PacketContainer packet) {
            try {
                float side = 0.0F;
                float forward = 0.0F;
                boolean jump = false;
                boolean dismount = false;

                if (packet.getFloat().size() >= 2) {
                    side = packet.getFloat().read(0);
                    forward = packet.getFloat().read(1);
                }

                if (packet.getBooleans().size() >= 2) {
                    jump = packet.getBooleans().read(0);
                    dismount = packet.getBooleans().read(1);
                } else if (packet.getBytes().size() >= 1) {
                    byte flags = packet.getBytes().read(0);
                    jump = (flags & 0x1) != 0;
                    dismount = (flags & 0x2) != 0;
                }

                return new ParsedInput(side, forward, jump, dismount);
            } catch (Throwable ex) {
                if (!parseWarningLogged) {
                    parseWarningLogged = true;
                    SkyBlock.getInstance().getLogger().warning(
                            "Failed parsing STEER_VEHICLE packet for BeastCharge rider input bridge."
                    );
                }
                return null;
            }
        }

        private static Field resolveBooleanField(String... names) {
            for (String name : names) {
                try {
                    Field field = EntityLiving.class.getDeclaredField(name);
                    if (field.getType() == boolean.class) {
                        field.setAccessible(true);
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            return null;
        }

        private static Field resolveFloatField(String... names) {
            for (String name : names) {
                try {
                    Field field = EntityLiving.class.getDeclaredField(name);
                    if (field.getType() == float.class) {
                        field.setAccessible(true);
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            return null;
        }

        private static boolean isJumping(EntityPlayer rider) {
            if (JUMP_FIELD == null) {
                return false;
            }
            try {
                return JUMP_FIELD.getBoolean(rider);
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }

        private static float getSideInput(EntityPlayer rider) {
            if (SIDE_INPUT_FIELD == null) {
                return 0.0F;
            }
            try {
                return SIDE_INPUT_FIELD.getFloat(rider);
            } catch (IllegalAccessException ignored) {
                return 0.0F;
            }
        }

        private static float getForwardInput(EntityPlayer rider) {
            if (FORWARD_INPUT_FIELD == null) {
                return 0.0F;
            }
            try {
                return FORWARD_INPUT_FIELD.getFloat(rider);
            } catch (IllegalAccessException ignored) {
                return 0.0F;
            }
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.RIGHT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent interactEvent
                    && interactEvent.getInteractType() == getTriggerType();
        }
    }

    public static class CorrosiveShot implements ITriggerableAbility {
        @Override
        public String getName() {
            return "Corrosion";
        }

        @Override
        public List<String> getDescription() {
            return List.of(
                    "$7Fire a corrosive arrow that",
                    "$7spreads and destroys nearby blocks",
                    "$7with cracking animation."
            );
        }

        @Override
        public void apply(Event event) {
            if (!(event instanceof SkyblockInteractEvent interactEvent)) return;
            Player player = interactEvent.getSkyblockPlayer().getPlayer();

            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setVelocity(player.getLocation().getDirection().multiply(2.35));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (arrow.isDead() || arrow.isOnGround()) {
                        Location impact = arrow.getLocation().clone();
                        performRandomCorrosion(impact, player);
                        if (arrow.isValid()) {
                            arrow.remove();
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(SkyBlock.getInstance(), 0L, 20L);

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.9f);
        }

        private void performRandomCorrosion(Location impact, Player player) {
            Queue<Location> corrosionQueue = new LinkedList<>();
            corrosionQueue.add(impact);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (corrosionQueue.isEmpty()) {
                        cancel();
                        return;
                    }

                    Location current = corrosionQueue.poll();
                    spreadCorrosion(current, corrosionQueue, impact, player);
                }
            }.runTaskTimer(SkyBlock.getInstance(), 0L, 40L);
        }

        private void spreadCorrosion(Location center, Queue<Location> corrosionQueue, Location origin, Player player) {
            int radius = (int) center.distance(origin);
            if (radius >= 3) return;

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Location neighbor = center.clone().add(x, y, z);
                        Block block = neighbor.getBlock();

                        if (!block.getType().isSolid()) continue;
                        if (block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER || block.getType() == Material.END_PORTAL_FRAME)
                            continue;

                        destroyBlockWithAnimation(neighbor, player);
                        corrosionQueue.add(neighbor);
                    }
                }
            }
        }

        private void destroyBlockWithAnimation(Location location, Player player) {
            Block block = location.getBlock();
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            int animationEntityId = player.getEntityId() + block.getLocation().hashCode();

            new BukkitRunnable() {
                int stage = 0;

                @Override
                public void run() {
                    if (stage < 10) {
                        Block liveBlock = location.getBlock();
                        if (liveBlock.getType() == Material.AIR) {
                            sendBlockBreakAnimation(protocolManager, player, block, -1, animationEntityId);
                            cancel();
                            return;
                        }

                        sendBlockBreakAnimation(protocolManager, player, block, stage, animationEntityId);
                        player.getWorld().playSound(location, Sound.BLOCK_SLIME_BLOCK_HIT, 0.5f, 0.6f + stage * 0.05f);
                        stage += 3;
                        return;
                    }

                    Block liveBlock = location.getBlock();
                    if (liveBlock.getType().isSolid() && liveBlock.getType() != Material.BEDROCK && liveBlock.getType() != Material.BARRIER) {
                        liveBlock.setType(Material.AIR);
                        player.getWorld().spawnParticle(Particle.BLOCK_CRACK, location.clone().add(0.5, 0.5, 0.5), 10, 0.25, 0.25, 0.25, 0.02, block.getBlockData());
                    }
                    sendBlockBreakAnimation(protocolManager, player, block, -1, animationEntityId);
                    cancel();
                }
            }.runTaskTimer(SkyBlock.getInstance(), 0L, 40L);
        }

        private void sendBlockBreakAnimation(ProtocolManager protocolManager, Player player, Block block, int stage, int entityId) {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
            packet.getIntegers().write(0, entityId).write(1, stage);
            packet.getBlockPositionModifier().write(0, new BlockPosition(block.getX(), block.getY(), block.getZ()));
            try {
                protocolManager.sendServerPacket(player, packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public TriggerType getTriggerType() {
            return TriggerType.LEFT_CLICK;
        }

        @Override
        public boolean trigger(Event event) {
            return event instanceof SkyblockInteractEvent interactEvent
                    && interactEvent.getInteractType() == getTriggerType();
        }
    }
}
