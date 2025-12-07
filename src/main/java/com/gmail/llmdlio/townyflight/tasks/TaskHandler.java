package com.gmail.llmdlio.townyflight.tasks;  
  
import com.gmail.llmdlio.townyflight.TownyFlight;  
import com.palmergames.bukkit.towny.scheduling.ScheduledTask;  
  
public class TaskHandler {  
  
	private static ScheduledTask tempFlightTask = null;  
	private static Runnable tempFlightRunnable = null;  
	private static ScheduledTask flightValidationTask = null;  
	private static Runnable flightValidationRunnable = null;  
  
	public static void toggleTempFlightTask(boolean on) {  
		if (on && !isTempFlightTaskRunning()) {  
			if (tempFlightRunnable == null)  
				tempFlightRunnable = new TempFlightTask();  
			tempFlightTask = TownyFlight.getPlugin().getScheduler().runRepeating(tempFlightRunnable, 60L, 20L);  
		} else if (!on && isTempFlightTaskRunning()) {  
			tempFlightTask.cancel();  
			tempFlightTask = null;  
			tempFlightRunnable = null;  
		}  
	}  
  
	public static boolean isTempFlightTaskRunning() {  
		return tempFlightTask != null && !tempFlightTask.isCancelled();  
	}  
  
	public static void toggleFlightValidationTask(boolean on) {  
		if (on && !isFlightValidationTaskRunning()) {  
			if (flightValidationRunnable == null)  
				flightValidationRunnable = new FlightValidationTask();  
			// 改为 20L (1秒) 以确保及时检测到传送
			flightValidationTask = TownyFlight.getPlugin().getScheduler().runRepeating(flightValidationRunnable, 20L, 20L);  
		} else if (!on && isFlightValidationTaskRunning()) {  
			flightValidationTask.cancel();  
			flightValidationTask = null;  
			flightValidationRunnable = null;  
		}  
	}  
  
	public static boolean isFlightValidationTaskRunning() {  
		return flightValidationTask != null && !flightValidationTask.isCancelled();  
	}  
}