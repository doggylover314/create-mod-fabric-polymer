package dev.createpolymer.logistics.packagePort;

import com.mojang.serialization.MapCodec;

public interface PackagePortTargetType {
	MapCodec<? extends PackagePortTarget> codec();
}
