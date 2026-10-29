package dev.createpolymer.trains.station;

import dev.createpolymer.contraptions.AssemblyException;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.entity.TrainIconType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Chest GUI that replaces Create's station screen and train assembly screen (StationScreen, AssemblyScreen and the
 * packets they send). It shows the station's name and the train that is there, with the buttons to rename them,
 * to pick the train icon, to disassemble the train and to enter assembly mode. In assembly mode it lists the bogeys
 * found on the assembly track and the reason an assembly failed, and has the assemble and cancel buttons.
 */
public class StationGui extends SimpleGui {
	private static final int TITLE = 4;
	private static final int CLOSE = 22;

	private final StationBlockEntity be;
	private int lastHash;

	public static void open(ServerPlayer player, StationBlockEntity be) {
		new StationGui(player, be).open();
	}

	public StationGui(ServerPlayer player, StationBlockEntity be) {
		super(MenuType.GENERIC_9x3, player, false);
		this.be = be;
		setLockPlayerInventory(true);
		refresh();
	}

	@Override
	public boolean stillValid() {
		return !be.isRemoved() && be.canPlayerUse(player);
	}

	@Override
	public void onTick() {
		if (player.tickCount % 5 == 0)
			refresh();
	}

	private GlobalStation station() {
		return be.getStation();
	}

	private void refresh() {
		for (int i = 0; i < 27; i++)
			clearSlot(i);
		GlobalStation station = station();
		if (station == null) {
			setTitle(Component.translatable(be.getBlockState()
				.getBlock()
				.getDescriptionId()));
			setSlot(13, new GuiElementBuilder(Items.BARRIER).setName(TrackNbt.translateDirect("station.idle"))
				.hideDefaultTooltip());
			setSlot(CLOSE, closeButton());
			return;
		}

		if (be.isAssembling() && station.getPresentTrain() == null)
			showAssembly(station);
		else
			showStation(station);
		setSlot(CLOSE, closeButton());
	}

	private GuiElementBuilder closeButton() {
		return new GuiElementBuilder(Items.EMERALD).setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close());
	}

	// ---- the station ----

	private void showStation(GlobalStation station) {
		setTitle(Component.literal(station.name));
		setSlot(TITLE, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.literal(station.name)
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatableWithFallback("create.station.rename", "Click to rename the station")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
			.hideDefaultTooltip()
			.setCallback(this::renameStation));

		Train train = station.getPresentTrain();
		if (train == null)
			train = station.getImminentTrain();

		if (train == null) {
			setSlot(11, new GuiElementBuilder(Items.MINECART).setName(TrackNbt.translateDirect("station.idle")
				.withStyle(ChatFormatting.GRAY))
				.hideDefaultTooltip());
			boolean ok = be.edgePoint.isOrthogonal() && !be.edgePoint.isOnCurve();
			GuiElementBuilder create = new GuiElementBuilder(ok ? Items.EMERALD : Items.BARRIER)
				.setName(TrackNbt.translateDirect("station.create_train")
					.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED))
				.hideDefaultTooltip();
			if (!ok)
				addAssemblyHint(create, be.edgePoint.isOnCurve() ? "no_assembly_curve" : "no_assembly_diagonal");
			else
				create.setCallback(() -> {
					be.enterAssemblyMode(player);
					refresh();
				});
			setSlot(15, create);
			return;
		}

		showTrain(station, train);
	}

	private void showTrain(GlobalStation station, Train train) {
		boolean present = station.getPresentTrain() == train;

		GuiElementBuilder icon = new GuiElementBuilder(iconItem(train.icon))
			.setName(train.name.copy()
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip();
		int bogeys = 0;
		for (Carriage carriage : train.carriages)
			bogeys += carriage.isOnTwoBogeys() ? 2 : 1;
		icon.addLoreLine(TrackNbt.translateDirect("station.more_bogeys", bogeys)
			.withStyle(ChatFormatting.GRAY));
		if (!present)
			icon.addLoreLine(Component.literal(String.format("%.0f m", Math.abs(train.navigation.distanceToDestination)))
				.withStyle(ChatFormatting.GRAY));
		icon.addLoreLine(Component.translatableWithFallback("create.station.rename_train", "Click to rename the train")
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		icon.setCallback(() -> renameTrain(train));
		setSlot(10, icon);

		setSlot(12, new GuiElementBuilder(iconItem(train.icon.next()))
			.setName(TrackNbt.translateDirect("station.icon_type")
				.withStyle(ChatFormatting.BLUE))
			.addLoreLine(Component.literal(train.icon.getId()
				.getPath())
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip()
			.setCallback(() -> {
				train.icon = train.icon.next();
				TrackRegistry.RAILWAYS.markTracksDirty();
				refresh();
			}));

		boolean canDisassemble = present && be.trainCanDisassemble && be.edgePoint.isOrthogonal();
		GuiElementBuilder disassemble = new GuiElementBuilder(canDisassemble ? Items.TNT_MINECART : Items.BARRIER)
			.setName(TrackNbt.translateDirect("station.disassemble_train")
				.withStyle(canDisassemble ? ChatFormatting.RED : ChatFormatting.GRAY))
			.hideDefaultTooltip();
		if (canDisassemble)
			disassemble.setCallback(() -> {
				be.enterAssemblyMode(player);
				refresh();
			});
		else if (present && !be.trainCanDisassemble)
			addAssemblyHint(disassemble, "train_not_aligned");
		else if (present)
			addAssemblyHint(disassemble, be.edgePoint.isOnCurve() ? "no_assembly_curve" : "no_assembly_diagonal");
		setSlot(14, disassemble);

		if (be.trainHasSchedule)
			setSlot(16, new GuiElementBuilder(Items.WRITABLE_BOOK)
				.setName(TrackNbt.translateDirect(
					be.trainHasAutoSchedule ? "station.remove_auto_schedule" : "station.remove_schedule"))
				.hideDefaultTooltip()
				.setCallback(() -> {
					be.dropSchedule(player, station.getPresentTrain());
					refresh();
				}));
	}

	private static void addAssemblyHint(GuiElementBuilder builder, String key) {
		builder.addLoreLine(TrackNbt.translateDirect("station." + key)
			.withStyle(ChatFormatting.GRAY));
		builder.addLoreLine(TrackNbt.translateDirect("station." + key + "_1")
			.withStyle(ChatFormatting.GRAY));
	}

	private static Item iconItem(TrainIconType icon) {
		return switch (icon.getId()
			.getPath()) {
		case "electric" -> Items.FURNACE_MINECART;
		case "modern" -> Items.HOPPER_MINECART;
		default -> Items.MINECART;
		};
	}

	// ---- assembly ----

	private void showAssembly(GlobalStation station) {
		// no client screen polls the track, so the screen looks for the bogeys itself
		be.refreshAssemblyInfo();
		setTitle(TrackNbt.translateDirect("station.assembly_title"));
		setSlot(TITLE, new GuiElementBuilder(Items.WRITABLE_BOOK)
			.setName(TrackNbt.translateDirect("station.assembly_title")
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip());

		AssemblyException exception = be.getLastException();
		if (exception != null) {
			GuiElementBuilder failed = new GuiElementBuilder(Items.BARRIER)
				.setName(TrackNbt.translateDirect("station.failed")
					.withStyle(ChatFormatting.RED))
				.hideDefaultTooltip();
			if (be.getFailedCarriageIndex() != -1)
				failed.addLoreLine(TrackNbt.translateDirect("station.carriage_number", be.getFailedCarriageIndex())
					.withStyle(ChatFormatting.GRAY));
			for (String line : exception.component.getString()
				.split("\n"))
				failed.addLoreLine(Component.literal(line)
					.withStyle(ChatFormatting.RED));
			failed.addLoreLine(TrackNbt.translateDirect("station.retry")
				.withStyle(ChatFormatting.GRAY));
			setSlot(11, failed);
		}

		int bogeyCount = be.getBogeyCount();
		setSlot(13, new GuiElementBuilder(Items.MINECART)
			.setName(TrackNbt.translateDirect(
				bogeyCount == 0 ? "station.no_bogeys" : bogeyCount == 1 ? "station.one_bogey" : "station.more_bogeys",
				bogeyCount)
				.withStyle(ChatFormatting.AQUA))
			.addLoreLine(TrackNbt.translateDirect("station.how_to")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(TrackNbt.translateDirect("station.how_to_1")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(TrackNbt.translateDirect("station.how_to_2")
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip());

		boolean canAssemble = bogeyCount > 0;
		GuiElementBuilder assemble = new GuiElementBuilder(canAssemble ? Items.EMERALD_BLOCK : Items.BARRIER)
			.setName(TrackNbt.translateDirect("station.assemble_train")
				.withStyle(canAssemble ? ChatFormatting.GREEN : ChatFormatting.GRAY))
			.hideDefaultTooltip();
		if (canAssemble)
			assemble.setCallback(() -> {
				be.assemble(player.getUUID());
				if (station.getPresentTrain() != null)
					be.exitAssemblyMode();
				refresh();
			});
		setSlot(15, assemble);

		setSlot(19, new GuiElementBuilder(Items.REDSTONE)
			.setName(TrackNbt.translateDirect("station.cancel")
				.withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> {
				be.exitAssemblyMode();
				refresh();
			}));
	}

	// ---- names ----

	private void renameStation() {
		GlobalStation station = station();
		if (station == null)
			return;
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatableWithFallback("create.station.rename", "Click to rename the station"));
		anvil.setDefaultInputValue(station.name);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable("create.gui.value_settings.confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 25)
					input = input.substring(0, 25);
				if (!input.isBlank())
					be.updateName(input);
				new StationGui(player, be).open();
			}));
		anvil.open();
	}

	private void renameTrain(Train train) {
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatableWithFallback("create.station.rename_train", "Click to rename the train"));
		anvil.setDefaultInputValue(train.name.getString());
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable("create.gui.value_settings.confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 35)
					input = input.substring(0, 35);
				if (!input.isBlank())
					train.name = Component.literal(input);
				TrackRegistry.RAILWAYS.markTracksDirty();
				new StationGui(player, be).open();
			}));
		anvil.open();
	}
}
