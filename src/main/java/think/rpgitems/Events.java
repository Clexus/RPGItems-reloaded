/*
 *  This file is part of RPG Items.
 *
 *  RPG Items is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RPG Items is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with RPG Items.  If not, see <http://www.gnu.org/licenses/>.
 */
package think.rpgitems;

import java.util.*;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import gnu.trove.map.hash.*;
import think.rpgitems.data.Locale;
import think.rpgitems.data.RPGMetadata;
import think.rpgitems.item.*;
import think.rpgitems.support.WorldGuard;

public class Events implements Listener {

    public static TIntByteHashMap removeArrows = new TIntByteHashMap();
    public static TIntIntHashMap rpgProjectiles = new TIntIntHashMap();
    public static TObjectIntHashMap<String> recipeWindows = new TObjectIntHashMap<String>();
    public static HashMap<String, Set<Integer>> drops = new HashMap<String, Set<Integer>>();
    public static boolean useLocaleInv = false;

    @EventHandler
    public void onItemEnchant(EnchantItemEvent e) {
        if (ItemManager.toRPGItem(e.getItem()) != null)
              e.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause().equals(RemoveCause.EXPLOSION))
            if (e.getEntity().hasMetadata("RPGItems.Rumble")) {
                e.getEntity().removeMetadata("RPGItems.Rumble", Plugin.plugin); // Allow the entity to be broken again
                e.setCancelled(true);
            }
    }

    @EventHandler
    public void onBreak(BlockPhysicsEvent e) { // Is not triggered when the block a torch is attached to is removed
        if (e.getChangedType().equals(Material.TORCH))
            if (e.getBlock().hasMetadata("RPGItems.Torch")) {
                e.setCancelled(true); // Cancelling this does not work
                e.getBlock().removeMetadata("RPGItems.Torch", Plugin.plugin);
                e.getBlock().setType(Material.AIR);
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getType().equals(Material.TORCH))
            if (e.getBlock().hasMetadata("RPGItems.Torch"))
                e.setCancelled(true);

        Player player = e.getPlayer();
        ItemStack item = player.getItemInHand();
        RPGItem rItem;
        if ((rItem = ItemManager.toRPGItem(item)) != null) {
            RPGMetadata meta = RPGItem.getMetadata(item);
            if (rItem.getMaxDurability() != -1) {
                int durability = meta.containsKey(RPGMetadata.DURABILITY) ? ((Number) meta.get(RPGMetadata.DURABILITY)).intValue() : rItem.getMaxDurability();
                durability--;
                if (durability <= 0) {
                    player.setItemInHand(null);
                }
                meta.put(RPGMetadata.DURABILITY, Integer.valueOf(durability));
            }
            RPGItem.updateItem(item, meta);
            player.updateInventory();
        }

    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        String type = e.getEntity().getType().toString();
        Random random = new Random();
        if (drops.containsKey(type)) {
            Set<Integer> items = drops.get(type);
            Iterator<Integer> it = items.iterator();
            while (it.hasNext()) {
                int id = it.next();
                RPGItem item = ItemManager.getItemById(id);
                if (item == null) {
                    it.remove();
                    continue;
                }
                double chance = item.dropChances.get(type);
                if (random.nextDouble() < chance / 100d) {
                    e.getDrops().add(item.toItemStack());
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        final Entity entity = e.getEntity();
        if (removeArrows.contains(entity.getEntityId())) {
            entity.remove();
            removeArrows.remove(entity.getEntityId());
        } else if (rpgProjectiles.contains(entity.getEntityId())) {
            RPGItem item = ItemManager.getItemById(rpgProjectiles.get(entity.getEntityId()));
            new BukkitRunnable() {
                @Override
                public void run() {
                    rpgProjectiles.remove(entity.getEntityId());
                }
            }.runTask(Plugin.plugin);
            if (item == null)
                return;
            item.projectileHit((Player) ((Projectile) entity).getShooter(), (Projectile) entity);
        }
    }

    @EventHandler
    public void onProjectileFire(ProjectileLaunchEvent e) {
        ProjectileSource shooter = e.getEntity().getShooter();
        if (shooter instanceof Player) {
            Player player = (Player) shooter;
            ItemStack item = player.getItemInHand();
            RPGItem rItem = ItemManager.toRPGItem(item);
            if (rItem == null)
                return;
            if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
                return;
            if (rItem.getHasPermission() == true && player.hasPermission(rItem.getPermission()) == false) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + String.format(Locale.get("message.error.permission")));
            }
            RPGMetadata meta = RPGItem.getMetadata(item);
            if (rItem.getMaxDurability() != -1) {
                int durability = meta.containsKey(RPGMetadata.DURABILITY) ? ((Number) meta.get(RPGMetadata.DURABILITY)).intValue() : rItem.getMaxDurability();
                durability--;
                if (durability <= 0) {
                    player.setItemInHand(null);
                }
                meta.put(RPGMetadata.DURABILITY, Integer.valueOf(durability));
            }
            RPGItem.updateItem(item, meta);
            player.updateInventory();
            rpgProjectiles.put(e.getEntity().getEntityId(), rItem.getID());
        }
    }

    @EventHandler
    public void onPlayerAction(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || (e.getAction() == Action.RIGHT_CLICK_BLOCK) && !e.isCancelled())) {
            ItemStack item = player.getItemInHand();

            if (item.getType() == Material.BOW || item.getType() == Material.SNOW_BALL || item.getType() == Material.EGG || item.getType() == Material.POTION)
                return;

            RPGItem rItem = ItemManager.toRPGItem(item);
            if (rItem == null)
                return;
            if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
                return;
            if (rItem.getHasPermission() == true && player.hasPermission(rItem.getPermission()) == false) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + String.format(Locale.get("message.error.permission")));
            }
            rItem.rightClick(player, e.getClickedBlock());
            if (!player.getItemInHand().getType().equals(Material.AIR))
                RPGItem.updateItem(item);
            else
                player.setItemInHand(null);
            player.updateInventory();
        } else if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            ItemStack item = player.getItemInHand();
            if (item.getType() == Material.BOW || item.getType() == Material.SNOW_BALL || item.getType() == Material.EGG || item.getType() == Material.POTION)
                return;

            RPGItem rItem = ItemManager.toRPGItem(item);
            if (rItem == null)
                return;
            if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
                return;
            if (rItem.getHasPermission() == true && player.hasPermission(rItem.getPermission()) == false) {
                e.setCancelled(true);
                player.sendMessage(ChatColor.RED + String.format(Locale.get("message.error.permission")));
            }
            rItem.leftClick(player, e.getClickedBlock());
            RPGItem.updateItem(item);
        }

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        ItemStack item = e.getPlayer().getItemInHand();
        if (item == null)
            return;

        RPGItem rItem = ItemManager.toRPGItem(item);
        if (rItem == null)
            return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        PlayerInventory in = player.getInventory();
        for (int i = 0; i < in.getSize(); i++) {
            ItemStack item = in.getItem(i);
            if (ItemManager.toRPGItem(item) != null)
                RPGItem.updateItem(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (ItemManager.toRPGItem(item) != null)
                RPGItem.updateItem(item);
        }
    }

    @EventHandler
    public void onPlayerPickup(PlayerPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (ItemManager.toRPGItem(item) != null) {
            RPGItem.updateItem(item);
            e.getItem().setItemStack(item);
        }
    }

    private HashSet<LocaleInventory> localeInventories = new HashSet<LocaleInventory>();

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (recipeWindows.containsKey(e.getPlayer().getName())) {
            int id = recipeWindows.remove(e.getPlayer().getName());
            RPGItem item = ItemManager.getItemById(id);
            if (item.recipe == null) {
                item.recipe = new ArrayList<ItemStack>();
            }
            item.recipe.clear();
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    int i = x + y * 9;
                    ItemStack it = e.getInventory().getItem(i);
                    item.recipe.add(it);
                }
            }
            item.hasRecipe = true;
            item.resetRecipe(true);
            ItemManager.save(Plugin.plugin);
            ((Player) e.getPlayer()).sendMessage(ChatColor.AQUA + "Recipe set for " + item.getName());
        } else if (useLocaleInv && e.getView() instanceof LocaleInventory) {
            localeInventories.remove(e.getView());
            ((LocaleInventory) e.getView()).getView().close();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (useLocaleInv && e.getView() instanceof LocaleInventory) {
            LocaleInventory inv = (LocaleInventory) e.getView();
            InventoryClickEvent clickEvent = new InventoryClickEvent(inv.getView(), e.getSlotType(), e.getSlot(), e.getClick(), e.getAction());
            Bukkit.getServer().getPluginManager().callEvent(clickEvent);
            if (clickEvent.isCancelled()) {
                e.setCancelled(true);
            } else {
                switch (clickEvent.getResult()) {
                case DEFAULT: // Can't really do this with current events
                case ALLOW:
                    System.out.println("ok...");
                    System.out.println(inv.getView().getItem(e.getRawSlot()));
                    inv.getView().setItem(e.getRawSlot(), clickEvent.getCursor());
                    System.out.println(inv.getView().getItem(e.getRawSlot()));
                    break;
                case DENY:
                    break;
                }
            }
            for (LocaleInventory localeInv : localeInventories) {
                if (localeInv != inv)
                    localeInv.reload();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryOpen(final InventoryOpenEvent e) {
        if (e.getView() instanceof LocaleInventory)
            return;
        if (e.getInventory().getType() != InventoryType.CHEST || !useLocaleInv) {
            Inventory in = e.getInventory();
            Iterator<ItemStack> it = in.iterator();
            try {
                while (it.hasNext()) {
                    ItemStack item = it.next();
                    if (ItemManager.toRPGItem(item) != null)
                        RPGItem.updateItem(item);
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                // Fix for the bug with anvils in craftbukkit
            }
        } else if (useLocaleInv) {
            LocaleInventory localeInv = new LocaleInventory((Player) e.getPlayer(), e.getView());
            e.setCancelled(true);
            e.getPlayer().openInventory(localeInv);
            localeInventories.add(localeInv);
        }
    }

    private Random random = new Random();

    private double playerDamager(EntityDamageByEntityEvent e, double damage) {
        Player player = (Player) e.getDamager();
        ItemStack item = player.getItemInHand();
        if (item.getType() == Material.BOW || item.getType() == Material.SNOW_BALL || item.getType() == Material.EGG || item.getType() == Material.POTION)
            return damage;

        RPGItem rItem = ItemManager.toRPGItem(item);
        if (rItem == null)
            return damage;
        if (!WorldGuard.canPvP(player) && !rItem.ignoreWorldGuard)
            return damage;
        if (rItem.getHasPermission() == true && player.hasPermission(rItem.getPermission()) == false) {
            damage = 0;
            e.setCancelled(true);
            player.sendMessage(ChatColor.RED + String.format(Locale.get("message.error.permission")));
        }
        damage = rItem.getDamageMin() != rItem.getDamageMax() ? (rItem.getDamageMin() + random.nextInt(rItem.getDamageMax() - rItem.getDamageMin())) : rItem.getDamageMin();
        if (e.getEntity() instanceof LivingEntity) {
            rItem.hit(player, (LivingEntity) e.getEntity(), e.getDamage());
        }
        RPGMetadata meta = RPGItem.getMetadata(item);
        if (rItem.getMaxDurability() != -1) {
            int durability = meta.containsKey(RPGMetadata.DURABILITY) ? ((Number) meta.get(RPGMetadata.DURABILITY)).intValue() : rItem.getMaxDurability();
            durability--;
            if (durability <= 0) {
                player.setItemInHand(null);
            }
            meta.put(RPGMetadata.DURABILITY, Integer.valueOf(durability));
        }
        RPGItem.updateItem(item, meta);
        player.updateInventory();
        return damage;
    }

    private double projectileDamager(EntityDamageByEntityEvent e, double damage) {
        Projectile entity = (Projectile) e.getDamager();
        if (rpgProjectiles.contains(entity.getEntityId())) {
            RPGItem rItem = ItemManager.getItemById(rpgProjectiles.get(entity.getEntityId()));
            if (rItem == null)
                return damage;
            damage = rItem.getDamageMin() != rItem.getDamageMax() ? (rItem.getDamageMin() + random.nextInt(rItem.getDamageMax() - rItem.getDamageMin())) : rItem.getDamageMin();
            if (e.getEntity() instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) e.getEntity();
                rItem.hit((Player) entity.getShooter(), le, e.getDamage());
            }
        }
        return damage;
    }

    private double playerHit(EntityDamageByEntityEvent e, double damage) {
        Player p = (Player) e.getEntity();
        if (e.isCancelled() || !WorldGuard.canPvP(p))
            return damage;
        ItemStack[] armour = p.getInventory().getArmorContents();
        for (int i = 0; i < armour.length; i++) {
            ItemStack pArmour = armour[i];
            RPGItem pRItem = ItemManager.toRPGItem(pArmour);
            if (pRItem == null)
                continue;
            if (!WorldGuard.canPvP(p) && !pRItem.ignoreWorldGuard)
                return damage;
            if (pRItem.getHasPermission() == true && p.hasPermission(pRItem.getPermission()) == false) {
                damage = 0;
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + String.format(Locale.get("message.error.permission")));
            }
            if (pRItem.getArmour() > 0) {
                damage -= Math.round(((double) damage) * (((double) pRItem.getArmour()) / 100d));
            }
            RPGMetadata meta = RPGItem.getMetadata(pArmour);
            if (pRItem.getMaxDurability() != -1) {
                int durability = meta.containsKey(RPGMetadata.DURABILITY) ? ((Number) meta.get(RPGMetadata.DURABILITY)).intValue() : pRItem.getMaxDurability();
                durability--;
                if (durability <= 0) {
                    armour[i] = null;
                }
                meta.put(RPGMetadata.DURABILITY, Integer.valueOf(durability));
            }
            RPGItem.updateItem(pArmour, meta);
        }
        p.getInventory().setArmorContents(armour);
        p.updateInventory();
        return damage;
    }

    private double playerHurt(Player e, double damage) {
        double ret = Double.MAX_VALUE;
        for (ItemStack item : e.getInventory().getArmorContents()) {
            RPGItem ri = ItemManager.toRPGItem(item);
            if (ri == null) continue;
            double d = ri.takeHit(e, null, damage);
            if (d < 0) continue;
            if (d < ret) ret = d;
        }
        for (ItemStack item : e.getInventory().getContents()) {
            RPGItem ri = ItemManager.toRPGItem(item);
            if (ri == null) continue;
            double d = ri.takeHit(e, null, damage);
            if (d < 0) continue;
            if (d < ret) ret = d;
        }
        return ret == Double.MAX_VALUE? damage : ret;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (ev instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) ev;
            double damage = e.getDamage();
            if (e.getDamager() instanceof Player) {
                damage = playerDamager(e, damage);
            } else if (e.getDamager() instanceof Projectile) {
                damage = projectileDamager(e, damage);
            }
            if (e.getEntity() instanceof Player) {
                damage = playerHit(e, damage);
            }
            e.setDamage(damage);
        }
        if (ev.getEntity() instanceof Player) {
            ev.setDamage(playerHurt((Player)ev.getEntity(), ev.getDamage()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemCraft(PrepareItemCraftEvent e) {
        if (ItemManager.toRPGItem(e.getInventory().getResult()) != null) {
            Random random = new Random();
            if (random.nextInt(ItemManager.toRPGItem(e.getInventory().getResult()).recipechance) != 0) {
                ItemStack baseitem = new ItemStack(e.getInventory().getResult().getType());
                e.getInventory().setResult(baseitem);
            }
        }
    }
}