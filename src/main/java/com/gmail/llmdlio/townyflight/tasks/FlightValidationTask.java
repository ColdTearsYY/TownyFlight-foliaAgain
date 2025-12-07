package com.gmail.llmdlio.townyflight.tasks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import com.gmail.llmdlio.townyflight.TownyFlight;
import com.gmail.llmdlio.townyflight.TownyFlightAPI;
import com.gmail.llmdlio.townyflight.config.Settings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;

public class FlightValidationTask implements Runnable {

	private static Map<UUID, Location> lastLocationMap = new ConcurrentHashMap<>();
	private static Map<UUID, Long> lastValidationTime = new ConcurrentHashMap<>();

	public static void removePlayer(UUID uuid) {
		lastLocationMap.remove(uuid);
		lastValidationTime.remove(uuid);
	}

	public static void clear() {
		lastLocationMap.clear();
		lastValidationTime.clear();
	}

	@Override
	public void run() {
		try {
			for (Player player : Bukkit.getOnlinePlayers()) {
				// Folia 修复：将所有逻辑调度到玩家线程执行，确保 getLocation() 安全
				TownyFlight.getPlugin().getScheduler().run(player, () -> {
					try {
						// 跳过创造/旁观模式
						if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
							return;

						if (player.hasPermission("townyflight.bypass"))
							return;

						// 如果玩家已经被事件监听器处理,跳过(避免与宽限期冲突)
						if (TownyFlightAPI.getInstance().playersHandledByListener.contains(player.getUniqueId()))
							return;

						Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
						if (resident == null)
							return;

						boolean currentlyFlying = player.getAllowFlight();
						Location currentLoc = player.isInsideVehicle() ? player.getVehicle().getLocation() : player.getLocation();
						Location lastLoc = lastLocationMap.get(player.getUniqueId());

						// 检测传送（跨世界 或 距离>200格）
						boolean isTeleport = false;
						if (lastLoc != null) {
							long timeSinceLastValidation = System.currentTimeMillis()
									- lastValidationTime.getOrDefault(player.getUniqueId(), 0L);
							boolean sameWorld = lastLoc.getWorld().equals(currentLoc.getWorld());
							boolean longDistance = sameWorld && lastLoc.distanceSquared(currentLoc) > 40000; // 200格

							if ((!sameWorld || longDistance) && timeSinceLastValidation > 1000) { // 至少1秒间隔
								isTeleport = true;
								lastValidationTime.put(player.getUniqueId(), System.currentTimeMillis());
							}
						}
						lastLocationMap.put(player.getUniqueId(), currentLoc);

						if (isTeleport) {
							if (currentlyFlying) {
								// 如果有临时飞行，不处理
								if (TempFlightTask.getSeconds(player.getUniqueId()) > 0L) {
									return;
								}
								if (player.getAllowFlight() && !TownyFlightAPI.getInstance().canFly(player, true)) {
									TownyFlightAPI.getInstance().removeFlight(player, true, true, "");
								}
							}
						}

						// 检查是否由 TownyFlight 管理
						NamespacedKey key = new NamespacedKey(TownyFlight.getPlugin(), "townyflight_managed");
						boolean isManagedByTownyFlight = player.getPersistentDataContainer()
								.getOrDefault(key, PersistentDataType.BYTE, (byte) 0) == 1;

						boolean shouldFly = TownyFlightAPI.allowedLocation(player, player.getLocation(), resident);

						// 情况1: 正在飞行且由 TownyFlight 管理,但不应该飞行 -> 移除飞行
						if (currentlyFlying && isManagedByTownyFlight && !shouldFly) {
							TownyFlightAPI.getInstance().removeFlight(player, true, true, "");
						}
						// 情况2: 没有飞行但应该飞行 -> 给予飞行(如果启用了自动飞行)
						else if (!currentlyFlying && shouldFly && Settings.autoEnableFlight) {
							// 使用完整的 canFly() 检查,包括战争状态
							if (TownyFlightAPI.getInstance().canFly(player, true)) {
								// 检查是否有临时飞行,避免冲突
								if (TempFlightTask.getSeconds(player.getUniqueId()) <= 0L) {
									TownyFlightAPI.getInstance().addFlight(player, true);
									TownyFlightAPI.cachePlayerFlight(player, true);
								}
							}
						}
					} catch (Exception e) {
						// 捕获单个玩家处理中的异常，避免影响其他玩家
						Bukkit.getLogger().warning("FlightValidationTask error for player " + player.getName() + ": " + e.getMessage());
					}
				});
			}
		} catch (IllegalArgumentException e) {
			Bukkit.getLogger().warning("FlightValidationTask location error: " + e.getMessage());
		} catch (Exception e) {
			Bukkit.getLogger().warning("FlightValidationTask unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}
}