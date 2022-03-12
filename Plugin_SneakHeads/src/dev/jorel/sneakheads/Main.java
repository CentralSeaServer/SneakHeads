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
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		playerItemFrames = new HashMap<>();
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
				float newYaw = event.getPlayer().getLocation().getYaw();				
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
		}
	}
	
	@EventHandler
	public void onSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();
		Block block = event.getPlayer().getLocation().getBlock();
		
		if(event.isSneaking()) {
			if(block.getType() == Material.AIR && block.getRelative(BlockFace.DOWN).getType() != Material.AIR) {
				ItemStack is = new ItemStack(Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) is.getItemMeta();
				meta.setOwningPlayer(player);
				is.setItemMeta(meta);
				
				ItemFrame frame = player.getWorld().spawn(player.getLocation().getBlock().getLocation(), ItemFrame.class);
				frame.setFacingDirection(BlockFace.UP);
				frame.setItem(is);
				frame.setVisible(false);
				
				playerItemFrames.put(player.getUniqueId(), frame.getUniqueId());
				player.setGameMode(GameMode.SPECTATOR);
				block.setType(Material.BARRIER); 
				player.setFlySpeed(0);
				player.setSneaking(false);
				
				Location newLocation = block.getLocation();
				newLocation = newLocation.add(0.5, 0.1, 0.5);
				newLocation = newLocation.setDirection(player.getLocation().getDirection());
				player.teleport(newLocation);
			}
		} else {
			if(block.getType() == Material.BARRIER || player.getGameMode() == GameMode.SPECTATOR) {
				block.setType(Material.AIR);
				if(playerItemFrames.containsKey(player.getUniqueId())) {
					Bukkit.getEntity(playerItemFrames.get(player.getUniqueId())).remove();
					playerItemFrames.remove(playerItemFrames.get(player.getUniqueId()));
					player.setGameMode(GameMode.SURVIVAL);
					player.setFlySpeed(0.1f);
				}
			}
		}
	}
}