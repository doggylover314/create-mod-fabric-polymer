package dev.createpolymer.logistics.packagerLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The logistics networks of the world, saved in the overworld's data storage ({@code data/create/logistics.dat}).
 */
public class LogisticsNetworkSavedData extends SavedData {
	public static final Codec<LogisticsNetworkSavedData> CODEC = Codec.list(LogisticsNetwork.CODEC)
		.xmap(list -> {
			LogisticsNetworkSavedData sd = new LogisticsNetworkSavedData();
			for (LogisticsNetwork network : list)
				sd.logisticsNetworks.put(network.id, network);
			return sd;
		}, sd -> new ArrayList<>(sd.logisticsNetworks.values()));

	// Create's data has no vanilla data fixer; this type keeps the tag away from the ones that change with Minecraft.
	public static final SavedDataType<LogisticsNetworkSavedData> TYPE = new SavedDataType<>(
		CreatePolymer.id("logistics"), LogisticsNetworkSavedData::new, CODEC, DataFixTypes.SAVED_DATA_STOPWATCHES);

	private Map<UUID, LogisticsNetwork> logisticsNetworks = new HashMap<>();

	public Map<UUID, LogisticsNetwork> getLogisticsNetworks() {
		return logisticsNetworks;
	}

	private LogisticsNetworkSavedData() {}

	public static LogisticsNetworkSavedData load(MinecraftServer server) {
		return server.overworld()
			.getDataStorage()
			.computeIfAbsent(TYPE);
	}

}
