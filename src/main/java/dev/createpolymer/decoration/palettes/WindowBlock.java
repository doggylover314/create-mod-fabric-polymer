package dev.createpolymer.decoration.palettes;

public class WindowBlock extends ConnectedGlassBlock {

	protected final boolean translucent;

	public WindowBlock(Properties p_i48392_1_, boolean translucent, String... models) {
		super(p_i48392_1_, models);
		this.translucent = translucent;
	}

	public boolean isTranslucent() {
		return translucent;
	}

}
