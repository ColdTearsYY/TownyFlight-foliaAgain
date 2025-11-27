package com.gmail.llmdlio.townyflight.tasks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import com.gmail.llmdlio.townyflight.TownyFlight;
import com.gmail.llmdlio.townyflight.TownyFlightAPI;
import com.gmail.llmdlio.townyflight.config.Settings;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;

public class FlightValidationTask implements Runnable {

	@Override
	public void run() {
		try {
			// 每次运行时清理离线玩家的标记,防止内存泄漏
			TownyFlightAPI.getInstance().cleanupOfflinePlayersFromHandledList();

			for (Player player : Bukkit.getOnlinePlayers()) {
				// 检查是否是 Towny 世界
				if (!TownyAPI.getInstance().isTownyWorld(player.getWorld()))
					continue;

				// 跳过创造/旁观模式
				if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
					continue;

				if (player.hasPermission("townyflight.bypass"))
					continue;

				// 如果玩家已经被事件监听器处理,跳过(避免与宽限期冲突)
				if (TownyFlightAPI.getInstance().playersHandledByListener.contains(player.getUniqueId()))
					continue;

				Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
				if (resident == null)
					continue;

				boolean currentlyFlying = player.getAllowFlight();

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
			}
		} catch (Exception e) {
			Bukkit.getLogger().warning("FlightValidationTask error: " + e.getMessage());
		}
	}
}