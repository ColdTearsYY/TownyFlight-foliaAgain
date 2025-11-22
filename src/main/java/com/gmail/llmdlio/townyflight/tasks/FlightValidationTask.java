package com.gmail.llmdlio.townyflight.tasks;  
  
import org.bukkit.Bukkit;  
import org.bukkit.entity.Player;  
  
import com.gmail.llmdlio.townyflight.TownyFlightAPI;  
import com.gmail.llmdlio.townyflight.config.Settings;  
import com.palmergames.bukkit.towny.TownyUniverse;  
import com.palmergames.bukkit.towny.object.Resident;  
  
public class FlightValidationTask implements Runnable {  
  
    @Override  
    public void run() {  
        try {  
            // 每次运行时清理离线玩家的标记,防止内存泄漏  
            TownyFlightAPI.getInstance().cleanupOfflinePlayersFromHandledList();  
              
            for (Player player : Bukkit.getOnlinePlayers()) {  
                if (player.hasPermission("townyflight.bypass"))  
                    continue;  
                  
                // 如果玩家已经被事件监听器处理,跳过(避免与宽限期冲突)  
                if (TownyFlightAPI.getInstance().playersHandledByListener.contains(player.getUniqueId()))  
                    continue;  
                  
                Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());  
                if (resident == null)  
                    continue;  
                  
                boolean currentlyFlying = player.getAllowFlight();  
                boolean shouldFly = TownyFlightAPI.allowedLocation(player, player.getLocation(), resident);  
                  
                // 情况1: 正在飞行但不应该飞行 -> 移除飞行  
                if (currentlyFlying && !shouldFly) {  
                    TownyFlightAPI.getInstance().removeFlight(player, true, true, "");  
                }  
                // 情况2: 没有飞行但应该飞行 -> 给予飞行(如果启用了自动飞行)  
                else if (!currentlyFlying && shouldFly && Settings.autoEnableFlight) {  
                    // 检查是否有临时飞行,避免冲突  
                    if (TempFlightTask.getSeconds(player.getUniqueId()) <= 0L) {  
                        TownyFlightAPI.getInstance().addFlight(player, true);  
                        TownyFlightAPI.cachePlayerFlight(player, true);  
                    }  
                }  
            }  
        } catch (Exception e) {  
            Bukkit.getLogger().warning("FlightValidationTask error: " + e.getMessage());  
        }  
    }  
}