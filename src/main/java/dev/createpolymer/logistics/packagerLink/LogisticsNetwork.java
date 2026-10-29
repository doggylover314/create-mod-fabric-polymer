package dev.createpolymer.logistics.packagerLink;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.registry.feature.PackagesRegistry;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

public class LogisticsNetwork {
	public static final Codec<LogisticsNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(n -> n.id),
		Codec.list(RequestPromise.CODEC).optionalFieldOf("promises", List.of())
			.forGetter(n -> n.panelPromises.flatten(false)),
		Codec.list(GlobalPos.CODEC).optionalFieldOf("links", List.of()).forGetter(n -> List.copyOf(n.totalLinks)),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(n -> Optional.ofNullable(n.owner)),
		Codec.BOOL.optionalFieldOf("locked", false).forGetter(n -> n.locked)
	).apply(i, LogisticsNetwork::new));

	public UUID id;
	public RequestPromiseQueue panelPromises;

	public Set<GlobalPos> totalLinks;
	public Set<GlobalPos> loadedLinks;

	@Nullable
	public UUID owner;
	public boolean locked;

	public LogisticsNetwork(UUID networkId) {
		id = networkId;
		panelPromises = new RequestPromiseQueue(PackagesRegistry.LOGISTICS::markDirty);
		totalLinks = new HashSet<>();
		loadedLinks = new HashSet<>();
		owner = null;
		locked = false;
	}

	private LogisticsNetwork(UUID networkId, List<RequestPromise> promises, List<GlobalPos> links,
		Optional<UUID> owner, boolean locked) {
		this(networkId);
		this.panelPromises = RequestPromiseQueue.of(promises, PackagesRegistry.LOGISTICS::markDirty);
		this.totalLinks.addAll(links);
		this.owner = owner.orElse(null);
		this.locked = locked;
	}

}
