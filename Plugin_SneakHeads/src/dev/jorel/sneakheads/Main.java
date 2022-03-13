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
	
	// Maps players to itemframe entities
	private Map<UUID, UUID> playerItemFrames;
	private Map<UUID, GameMode> playerGamemodes;
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		playerItemFrames = new HashMap<>();
		playerGamemodes = new HashMap<>();
	}
	
	@Override
	public void onDisable() {
		for(UUID uuid : playerItemFrames.values()) {
			Entity entity = Bukkit.getEntity(uuid);
			if(entity != null) {
				entity.remove();
			}
		}
	}
	
	@EventHandler
	public void spectatorPlayerMoveEvent(PlayerMoveEvent event) {
		if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
			if(!event.getFrom().toVector().equals(event.getTo().toVector())) {
				event.setCancelled(true);
				event.getPlayer().setFlySpeed(0);
			}
		}
	}
	
	@EventHandler
	public void onItemFrameBreak(HangingBreakEvent event) {
		if(event.getCause() == RemoveCause.OBSTRUCTION) {
			if(event.getEntity().getLocation().getBlock().getType() == Material.BARRIER) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onSpectatePlayer(PlayerInteractEvent event) {
		if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPlayerRotate(PlayerMoveEvent event) {
		if(playerItemFrames.containsKey(event.getPlayer().getUniqueId())) {
			ItemFrame itemFrame = ((ItemFrame) Bukkit.getEntity(playerItemFrames.get(event.getPlayer().getUniqueId())));
			if(itemFrame != null) {
				setRotation(itemFrame, event.getPlayer());
			}
		}
	}
	
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
	
	@EventHandler
	public void onSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();
		Block block = event.getPlayer().getLocation().getBlock();
		
		if(event.isSneaking()) {
			if(block.getType() == Material.HONEY_BLOCK || block.getType() == Material.DIRT_PATH || block.getType() == Material.SOUL_SAND) {
				block = block.getRelative(BlockFace.UP);
			}
			boolean isSuitableBlock = switch(block.getType()) {
				case AIR -> true;
				case WATER -> true;
				case LAVA -> true;
				default -> false;
			};
			if(isSuitableBlock && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
				ItemStack is = new ItemStack(Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) is.getItemMeta();
				meta.setOwningPlayer(player);
				is.setItemMeta(meta);

				Location newLocation = block.getLocation();
				newLocation = newLocation.add(0.5, 0.1, 0.5);
				
				ItemFrame frame = player.getWorld().spawn(newLocation, ItemFrame.class, iFrame -> {
					iFrame.setFacingDirection(BlockFace.UP, true);
					iFrame.setItem(is);
					iFrame.setVisible(false);
					setRotation(iFrame, player);
				});
				
				newLocation = newLocation.setDirection(player.getLocation().getDirection());
				player.teleport(newLocation);
				
				playerItemFrames.put(player.getUniqueId(), frame.getUniqueId());
				playerGamemodes.put(player.getUniqueId(), player.getGameMode());
				player.setGameMode(GameMode.SPECTATOR);
				block.setType(Material.BARRIER); 
				player.setFlySpeed(0);
				player.setSneaking(false);
			}
		} else {
			if(block.getType() == Material.BARRIER || player.getGameMode() == GameMode.SPECTATOR) {
				block.setType(Material.AIR);
				if(playerItemFrames.containsKey(player.getUniqueId())) {
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