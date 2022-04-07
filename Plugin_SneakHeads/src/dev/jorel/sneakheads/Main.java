package dev.jorel.sneakheads;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {
	
	private static final long COOLDOWN_IN_TICKS = 5L;

	private Map<UUID, UUID> playerItemFrames; // Maps players to itemframe entities
	private Map<UUID, GameMode> playerGamemodes; // Maps players to their gamemodes
	private boolean enabled = false;
	private Map<UUID, Boolean> playerOnCooldown; // Maps players to whether they are on cooldown (true = on cooldown)
	// If a player is on cooldown, they CANNOT turn back into a person until the cooldown is over
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		playerItemFrames = new HashMap<>();
		playerGamemodes = new HashMap<>();
		enabled = false;
		playerOnCooldown = new HashMap<>();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(label.equalsIgnoreCase("sneakheads")) {
			if(sender.isOp() || sender instanceof ConsoleCommandSender) {
				enabled = !enabled;
				sender.sendMessage("[SneakHeads] Enabled status changed to: " + enabled);
			}
			return true;
		}
		return false;
	}
	
	@Override
	public void onDisable() {
		// Remove all existing heads
		for(UUID uuid : playerItemFrames.values()) {
			Entity entity = Bukkit.getEntity(uuid);
			if(entity != null) {
				entity.getLocation().getBlock().setType(Material.AIR);
				entity.remove();
			}
		}
		enabled = false;
	}
	
	/**
	 * Prevent transformed players moving while in spectator mode due to scrolling.
	 * If a player is transformed, they can't move (flight speed is set to 0), but
	 * we need to prevent them from changing their speed (using the scroll wheel) to
	 * prevent them from "floating down".
	 */
	@EventHandler
	public void spectatorPlayerMoveEvent(PlayerMoveEvent event) {
		if(enabled) {
			if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
				if(!event.getFrom().toVector().equals(event.getTo().toVector())) {
					event.setCancelled(true);
					event.getPlayer().setFlySpeed(0);
				}
			}
		}
	}
	
	/**
	 * Prevent item frames breaking because barriers are on top of them
	 */
	@EventHandler
	public void onItemFrameBreak(HangingBreakEvent event) {
		if(event.getCause() == RemoveCause.OBSTRUCTION && event.getEntity().getLocation().getBlock().getType() == Material.BARRIER) {
			event.setCancelled(true);
		}
	}
	
	/**
	 * Prevent transformed players interacting with stuff. When a player is
	 * transformed, they are in spectator which can allow them to spectate other
	 * entities or players (and thus, skew their location)
	 */
	@EventHandler
	public void onSpectatePlayer(PlayerInteractEvent event) {
		if(enabled) {
			if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
				event.setCancelled(true);
			}
		}
	}
	
	/**
	 * Rotate a player's block head when the player rotates
	 */
	@EventHandler
	public void onPlayerRotate(PlayerMoveEvent event) {
		if(enabled) {
			if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
				ItemFrame itemFrame = ((ItemFrame) Bukkit.getEntity(playerItemFrames.get(event.getPlayer().getUniqueId())));
				if(itemFrame != null) {
					setRotation(itemFrame, event.getPlayer());
				}
			}
		}
	}
	
	/**
	 * Sets the rotation for an item frame based on a player's rotation
	 * @param itemFrame the item frame to rotate
	 * @param player the player's rotation to use to rotate the itemframe
	 */
	private void setRotation(ItemFrame itemFrame, Player player) {
		float newYaw = player.getLocation().getYaw();
		Rotation newRotation = Rotation.NONE;
		if((newYaw < 180 && newYaw >= 135) || (newYaw < -135 && newYaw >= -180)) {
			newRotation = Rotation.CLOCKWISE;
		} else if(newYaw >= -135 && newYaw < -45) {
			newRotation = Rotation.FLIPPED;
		} else if(newYaw >= -45 && newYaw < 45) {
			newRotation = Rotation.COUNTER_CLOCKWISE;
		} else if(newYaw >= 45 && newYaw < 135) {
			newRotation = Rotation.NONE;
		}
		
		itemFrame.setRotation(newRotation);
	}
	
	/**
	 * Toggle a player's transformed state. If they sneak, they turn into a block.
	 * If they stop sneaking, they revert back to normal.
	 */
	@EventHandler
	public void onSneak(PlayerToggleSneakEvent event) {
		if(enabled) {
			Player player = event.getPlayer();
			Block block = event.getPlayer().getLocation().getBlock();
			
			if(event.isSneaking()) {
				// Blocks which are not full height when standing on them need to
				// reference the block above them
				boolean isSpecial = switch(block.getRelative(BlockFace.DOWN).getType()) {
					case AZALEA, FLOWERING_AZALEA, SCAFFOLDING, POWDER_SNOW, BIG_DRIPLEAF -> true;
					default -> false;
				};
				
				block = switch(block.getType()) {
					case HONEY_BLOCK:
					case DIRT_PATH:
					case SOUL_SAND:
					case FARMLAND:
					case CACTUS:
					case CHEST:
					case ENDER_CHEST:
					case TRAPPED_CHEST:
						yield block.getRelative(BlockFace.UP);
					case BIG_DRIPLEAF:
						isSpecial = true;
						yield block.getRelative(BlockFace.UP);
					default: 
						yield block;
				};
				
				// The block the player is currently at must be suitable to be replaced
				// with their "block head". We also allow water and lava for fun fluid
				// puzzles
				boolean isSuitableBlock = switch(block.getType()) {
					case AIR, WATER, LAVA, FIRE -> true;
					default -> false;
				};
				
				if(isSuitableBlock && (block.getRelative(BlockFace.DOWN).getType().isSolid() || isSpecial)) {
					// Create the player's head itemstack
					ItemStack is = new ItemStack(Material.PLAYER_HEAD);
					SkullMeta meta = (SkullMeta) is.getItemMeta();
					meta.setOwningPlayer(player);
					is.setItemMeta(meta);
	
					// Tweak the location to be the centre of the block (raised a bit)
					Location newLocation = block.getLocation();
					newLocation = newLocation.add(0.5, 0.1, 0.5);
					
					// Spawn the item frame. We use the consumer method to also set up
					// the item frame before spawning it in (setting its direction, item,
					// rotation and visibility). If we don't do this here, the item frame
					// can be assigned to the wrong block and effectively "teleported" to
					// a completely unexpected location
					ItemFrame frame = player.getWorld().spawn(newLocation, ItemFrame.class, iFrame -> {
						iFrame.setFacingDirection(BlockFace.UP, true);
						iFrame.setItem(is);
						iFrame.setVisible(false);
						iFrame.setInvulnerable(true);
						iFrame.setGravity(false);
						setRotation(iFrame, player);
					});
					
					// Teleport the player to the centre of the block (and set their
					// direction so their direction isn't reset)
					newLocation = newLocation.setDirection(player.getLocation().getDirection());
					player.teleport(newLocation);
					
					// Store the player's game state
					playerItemFrames.put(player.getUniqueId(), frame.getUniqueId());
					playerGamemodes.put(player.getUniqueId(), player.getGameMode());
					
					// Set the player's new state
					player.setGameMode(GameMode.SPECTATOR);
					block.setType(Material.BARRIER); 
					player.setFlySpeed(0);
					player.setSneaking(false);

					playerOnCooldown.put(player.getUniqueId(), true);
					Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
						playerOnCooldown.put(player.getUniqueId(), false);
					}, COOLDOWN_IN_TICKS);
				}
			} else {
				if(block.getType() == Material.BARRIER || player.getGameMode() == GameMode.SPECTATOR) {

					if(playerOnCooldown.get(player.getUniqueId())) {
						player.setSneaking(true);
						event.setCancelled(true);
						return;
					}
					
					block.setType(Material.AIR);
					if(playerItemFrames.containsKey(player.getUniqueId())) {
						// Remove the item, reset the player's state
						Bukkit.getEntity(playerItemFrames.get(player.getUniqueId())).remove();
						playerItemFrames.remove(player.getUniqueId());
						player.setGameMode(playerGamemodes.get(player.getUniqueId()));
						player.setFlySpeed(0.1f);
						player.setFlying(false);
					}
				}
			}
		}
	}
}