package dev.createpolymer.redstone.link;

import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;

public interface IRedstoneLinkable {

	public int getTransmittedStrength();

	public void setReceivedStrength(int power);

	public boolean isListening();

	public boolean isAlive();

	public Couple<Frequency> getNetworkKey();

	public BlockPos getLocation();

}
