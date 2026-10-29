package dev.createpolymer.logistics.packager;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.packager.InventoryIdentifier;
import dev.createpolymer.foundation.item.handler.IItemHandler;

/**
 * An item inventory, possibly with an associated InventoryIdentifier.
 */
public record IdentifiedInventory(@Nullable InventoryIdentifier identifier, IItemHandler handler) {
}
