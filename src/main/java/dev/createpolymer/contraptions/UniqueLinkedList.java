package dev.createpolymer.contraptions;

import java.util.AbstractQueue;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * A FIFO queue that ignores elements it already holds (Catnip's UniqueLinkedList, as far as contraptions use it).
 */
public class UniqueLinkedList<T> extends AbstractQueue<T> implements Queue<T> {
	private final LinkedList<T> list = new LinkedList<>();
	private final Set<T> members = new HashSet<>();

	/** Unlike AbstractQueue, adding an element that is already queued is not an error. */
	@Override
	public boolean add(T t) {
		return offer(t);
	}

	@Override
	public boolean offer(T t) {
		if (!members.add(t))
			return false;
		list.addLast(t);
		return true;
	}

	@Override
	public T poll() {
		T t = list.pollFirst();
		if (t != null)
			members.remove(t);
		return t;
	}

	@Override
	public T peek() {
		return list.peekFirst();
	}

	@Override
	public Iterator<T> iterator() {
		Iterator<T> inner = list.iterator();
		return new Iterator<>() {
			private T last;

			@Override
			public boolean hasNext() {
				return inner.hasNext();
			}

			@Override
			public T next() {
				return last = inner.next();
			}

			@Override
			public void remove() {
				inner.remove();
				members.remove(last);
			}
		};
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public void clear() {
		list.clear();
		members.clear();
	}
}
