package dev.createpolymer.logistics.packagerLink;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class RequestPromiseQueue {
	private static final Codec<List<RequestPromise>> LIST_CODEC = Codec.list(RequestPromise.CODEC);

	private Map<Item, List<RequestPromise>> promisesByItem;
	private Runnable onChanged;

	public RequestPromiseQueue(Runnable onChanged) {
		promisesByItem = new IdentityHashMap<>();
		this.onChanged = onChanged;
	}

	public void add(RequestPromise promise) {
		promisesByItem.computeIfAbsent(promise.promisedStack.stack.getItem(), $ -> new LinkedList<>())
			.add(promise);
		onChanged.run();
	}

	public void setOnChanged(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	public int getTotalPromisedAndRemoveExpired(ItemStack stack, int expiryTime) {
		int promised = 0;
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return promised;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise promise = iterator.next();
			if (!ItemStack.isSameItemSameComponents(promise.promisedStack.stack, stack))
				continue;
			if (expiryTime != -1 && promise.ticksExisted >= expiryTime) {
				iterator.remove();
				onChanged.run();
				continue;
			}

			promised += promise.promisedStack.count;
		}
		return promised;
	}

	public void forceClear(ItemStack stack) {
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise promise = iterator.next();
			if (!ItemStack.isSameItemSameComponents(promise.promisedStack.stack, stack))
				continue;
			iterator.remove();
			onChanged.run();
		}

		if (list.isEmpty())
			promisesByItem.remove(stack.getItem());
	}

	public void itemEnteredSystem(ItemStack stack, int amount) {
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise requestPromise = iterator.next();
			if (!ItemStack.isSameItemSameComponents(requestPromise.promisedStack.stack, stack))
				continue;

			int toSubtract = Math.min(amount, requestPromise.promisedStack.count);
			amount -= toSubtract;
			requestPromise.promisedStack.count -= toSubtract;

			if (requestPromise.promisedStack.count <= 0) {
				iterator.remove();
				onChanged.run();
			}
			if (amount <= 0)
				break;
		}

		if (list.isEmpty())
			promisesByItem.remove(stack.getItem());
	}

	public List<RequestPromise> flatten(boolean sorted) {
		List<RequestPromise> all = new ArrayList<>();
		promisesByItem.forEach((key, list) -> all.addAll(list));
		if (sorted)
			all.sort(RequestPromise.ageComparator());
		return all;
	}

	public void write(ValueOutput output) {
		output.store("List", LIST_CODEC, flatten(false));
	}

	public static RequestPromiseQueue read(ValueInput input, Runnable onChanged) {
		RequestPromiseQueue queue = new RequestPromiseQueue(onChanged);
		for (RequestPromise promise : input.read("List", LIST_CODEC).orElse(List.of()))
			queue.add(promise);
		return queue;
	}

	/** The same list the codec of {@link LogisticsNetwork} writes. */
	public static RequestPromiseQueue of(List<RequestPromise> promises, Runnable onChanged) {
		RequestPromiseQueue queue = new RequestPromiseQueue(onChanged);
		for (RequestPromise promise : promises)
			queue.add(promise);
		return queue;
	}

	public void tick() {
		promisesByItem.forEach((key, list) -> list.forEach(RequestPromise::tick)); // delete old entries?
	}

	public boolean isEmpty() {
		return promisesByItem.isEmpty();
	}
}
