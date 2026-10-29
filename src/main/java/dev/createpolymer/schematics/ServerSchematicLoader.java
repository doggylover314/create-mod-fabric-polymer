package dev.createpolymer.schematics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.schematics.SchematicExport.SchematicExportResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

/**
 * Create's ServerSchematicLoader. Create's clients upload schematic files from their own folder over packets; a
 * vanilla client has no folder to upload from, so the files a player can use are the ones in the server's
 * {@code schematics/} folder (shared by everybody) and in {@code schematics/uploaded/<player name>/} (Create's
 * layout for what a player has uploaded, and where the schematic and quill saves to). The checks Create makes on an
 * upload (path traversal, file format, size, number of files per player) are made here whenever a file is put into
 * a player's folder or read from it.
 */
public class ServerSchematicLoader {

	/** Create's CreatePaths.SCHEMATICS_DIR: the server's game directory has no client folder to read from */
	public static Path schematicsDir() {
		return FabricLoader.getInstance()
			.getGameDir()
			.resolve("schematics");
	}

	/** Create's CreatePaths.UPLOADED_SCHEMATICS_DIR */
	public static Path uploadedDir() {
		return schematicsDir().resolve("uploaded");
	}

	/** A schematic file a player can choose, with where it comes from. {@code name} includes the .nbt ending. */
	public record AvailableSchematic(String name, boolean uploaded) {
		public Component displayName() {
			return Component.literal(name);
		}
	}

	/**
	 * The file of {@code schematic} in the folder of the player, or null when the name is not a plain .nbt file name
	 * inside that folder (path traversal, other formats). This is the check Create's handleNewUpload makes.
	 */
	@Nullable
	public static Path resolveUploadPath(String playerName, String schematic) {
		if (playerName == null || playerName.isEmpty() || schematic == null || schematic.isEmpty())
			return null;
		Path baseDir = uploadedDir().normalize();
		Path playerPath;
		Path uploadPath;
		try {
			playerPath = baseDir.resolve(playerName)
				.normalize();
			uploadPath = playerPath.resolve(schematic)
				.normalize();
		} catch (java.nio.file.InvalidPathException e) {
			return null;
		}
		String playerSchematicId = playerName + "/" + schematic;

		if (!(playerPath.startsWith(baseDir) && !playerPath.equals(baseDir) && uploadPath.startsWith(playerPath)
			&& !uploadPath.equals(playerPath) && uploadPath.getParent()
				.equals(playerPath))) {
			CreatePolymer.LOGGER.warn("Attempted Schematic Upload with path traversal: {}", playerSchematicId);
			return null;
		}

		// Unsupported Format
		if (!schematic.endsWith(".nbt")) {
			CreatePolymer.LOGGER.warn("Attempted Schematic Upload with non-supported Format: {}", playerSchematicId);
			return null;
		}
		return uploadPath;
	}

	/** The file of a schematic in the shared folder, or null when the name is not a plain .nbt file name in it */
	@Nullable
	public static Path resolveSharedPath(String schematic) {
		if (schematic == null || schematic.isEmpty() || !schematic.endsWith(".nbt"))
			return null;
		Path baseDir = schematicsDir().normalize();
		try {
			Path path = baseDir.resolve(schematic)
				.normalize();
			if (path.equals(baseDir) || !path.startsWith(baseDir) || !baseDir.equals(path.getParent()))
				return null;
			return path;
		} catch (java.nio.file.InvalidPathException e) {
			return null;
		}
	}

	public static String playerName(ServerPlayer player) {
		return player.getGameProfile()
			.name();
	}

	/** Create's ClientSchematicLoader.refresh, for a player: their uploaded files first, then the shared ones */
	public static List<AvailableSchematic> getAvailableSchematics(String playerName) {
		List<AvailableSchematic> available = new ArrayList<>();
		Path own = resolveUploadPath(playerName, "x.nbt");
		if (own != null)
			collect(own.getParent(), true, available);
		collect(schematicsDir(), false, available);
		return available;
	}

	private static void collect(Path dir, boolean uploaded, List<AvailableSchematic> into) {
		List<AvailableSchematic> found = new ArrayList<>();
		try (Stream<Path> paths = Files.list(dir)) {
			paths.filter(f -> !Files.isDirectory(f) && f.getFileName()
				.toString()
				.endsWith(".nbt"))
				.forEach(path -> found.add(new AvailableSchematic(path.getFileName()
					.toString(), uploaded)));
		} catch (java.nio.file.NoSuchFileException ignored) {
			// No Schematics created yet
		} catch (IOException e) {
			CreatePolymer.LOGGER.error("Failed to refresh schematics", e);
		}
		found.sort(Comparator.comparing(AvailableSchematic::name, ServerSchematicLoader::compareNatural));
		into.addAll(found);
	}

	/** Create's order for the list of schematics: numbers inside names are compared as numbers */
	public static int compareNatural(String a, String b) {
		if (a.endsWith(".nbt"))
			a = a.substring(0, a.length() - 4);
		if (b.endsWith(".nbt"))
			b = b.substring(0, b.length() - 4);
		int aLength = a.length();
		int bLength = b.length();
		int minSize = Math.min(aLength, bLength);
		char aChar, bChar;
		boolean aNumber, bNumber;
		boolean asNumeric = false;
		int lastNumericCompare = 0;
		for (int i = 0; i < minSize; i++) {
			aChar = a.charAt(i);
			bChar = b.charAt(i);
			aNumber = aChar >= '0' && aChar <= '9';
			bNumber = bChar >= '0' && bChar <= '9';
			if (asNumeric)
				if (aNumber && bNumber) {
					if (lastNumericCompare == 0)
						lastNumericCompare = aChar - bChar;
				} else if (aNumber)
					return 1;
				else if (bNumber)
					return -1;
				else if (lastNumericCompare == 0) {
					if (aChar != bChar)
						return aChar - bChar;
					asNumeric = false;
				} else
					return lastNumericCompare;
			else if (aNumber && bNumber) {
				asNumeric = true;
				if (lastNumericCompare == 0)
					lastNumericCompare = aChar - bChar;
			} else if (aChar != bChar)
				return aChar - bChar;
		}
		if (asNumeric)
			if (aLength > bLength && a.charAt(bLength) >= '0' && a.charAt(bLength) <= '9') // as number
				return 1; // a has bigger size, thus b is smaller
			else if (bLength > aLength && b.charAt(aLength) >= '0' && b.charAt(aLength) <= '9') // as number
				return -1; // b has bigger size, thus a is smaller
			else if (lastNumericCompare == 0)
				return aLength - bLength;
			else
				return lastNumericCompare;
		else
			return aLength - bLength;
	}

	/** Create's ClientSchematicLoader.isGZIPEncoded: the first two bytes are the magic number (0x1F, 0x8B) */
	public static boolean isGZIPEncoded(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] bytes = new byte[2];
			if (in.read(bytes) != 2)
				return false;
			return (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
		} catch (IOException exception) {
			return false;
		}
	}

	/** Whether a schematic of this size (in bytes) is within the limit; the player is told when it is not */
	public static boolean validateSchematicSizeOnServer(@Nullable ServerPlayer player, long size) {
		return validateSchematicSizeOnServer(player, size, SchematicsRegistry.maxTotalSchematicSize);
	}

	/** The same with the limit given, in KiloBytes */
	public static boolean validateSchematicSizeOnServer(@Nullable ServerPlayer player, long size, long maxFileSize) {
		if (size > maxFileSize * 1000) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("create.schematics.uploadTooLarge")
					.append(Component.literal(" (" + size / 1000 + " KB).")));
				player.sendSystemMessage(Component.translatable("create.schematics.maxAllowedSize")
					.append(Component.literal(" " + maxFileSize + " KB")));
			}
			return false;
		}
		return true;
	}

	/**
	 * Puts a file of the shared schematics folder into the folder of the player, the way Create's handleNewUpload
	 * prepares an upload: the file is checked, an older file of the same name is replaced, and when the player has
	 * too many schematics the oldest one goes.
	 *
	 * @return the name of the schematic in the player's folder, or null when the file was refused
	 */
	@Nullable
	public static String importShared(ServerPlayer player, String schematic) {
		String playerName = playerName(player);
		Path source = resolveSharedPath(schematic);
		Path target = resolveUploadPath(playerName, schematic);
		if (source == null || target == null || !Files.isRegularFile(source))
			return null;
		try {
			if (!isGZIPEncoded(source)) {
				player.sendSystemMessage(Component.translatable("create.schematics.wrongFormat"));
				return null;
			}
			if (!validateSchematicSizeOnServer(player, Files.size(source)))
				return null;

			Files.createDirectories(target.getParent());
			// Delete schematic with same name
			Files.deleteIfExists(target);
			if (!tryDeleteOldestSchematic(target.getParent()))
				return null;
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			CreatePolymer.LOGGER.info("New Schematic Uploaded: " + playerName + "/" + schematic);
			return schematic;
		} catch (IOException e) {
			CreatePolymer.LOGGER.error("Exception Thrown when starting Upload: {}/{}", playerName, schematic, e);
			return null;
		}
	}

	/** Whether the schematic (a name in the folder of the player) can be used: it exists and is within the limits */
	public static boolean isUsable(ServerPlayer player, String schematic) {
		Path path = resolveUploadPath(playerName(player), schematic);
		try {
			return path != null && Files.isRegularFile(path) && validateSchematicSizeOnServer(player, Files.size(path));
		} catch (IOException e) {
			return false;
		}
	}

	/** Saves a region to the folder of the player. Returns null when it failed; the player is told why. */
	@Nullable
	public static SchematicExportResult saveFromQuill(ServerPlayer player, String name, Level level, BlockPos first,
		BlockPos second) {
		String playerName = playerName(player);
		Path dir = resolveUploadPath(playerName, "x.nbt");
		if (dir == null)
			return fail(player);
		dir = dir.getParent();
		// if there's too many schematics, delete oldest
		if (!tryDeleteOldestSchematic(dir))
			return fail(player);

		SchematicExportResult result = SchematicExport.saveSchematic(dir, name, false, level, first, second);
		if (result == null)
			return fail(player);
		try {
			if (!validateSchematicSizeOnServer(player, Files.size(result.file()))) {
				Files.deleteIfExists(result.file());
				return null;
			}
		} catch (IOException e) {
			CreatePolymer.LOGGER.error("Could not check the size of {}", result.file(), e);
		}
		return result;
	}

	@Nullable
	private static SchematicExportResult fail(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.failed")
			.withStyle(ChatFormatting.RED));
		return null;
	}

	/**
	 * Create's handleInstantSchematic: the schematic and quill in the hand is replaced by a schematic item of the
	 * region.
	 */
	public static void handleInstantSchematic(ServerPlayer player, String schematic, Level world, BlockPos pos,
		BlockPos bounds) {
		String playerName = playerName(player);
		Path uploadPath = resolveUploadPath(playerName, schematic);
		if (uploadPath == null)
			return;
		Path playerPath = uploadPath.getParent();

		// Not holding S&Q
		if (!player.getMainHandItem()
			.is(SchematicsRegistry.SCHEMATIC_AND_QUILL))
			return;

		// if there's too many schematics, delete oldest
		if (!tryDeleteOldestSchematic(playerPath))
			return;

		SchematicExportResult result =
			SchematicExport.saveSchematic(playerPath, schematic, true, world, pos, pos.offset(bounds)
				.offset(-1, -1, -1));
		if (result != null)
			player.setItemInHand(InteractionHand.MAIN_HAND, SchematicItem.create(world, schematic, playerName));
		else
			player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.instant_failed")
				.withStyle(ChatFormatting.RED));
	}

	/** Makes room for one more file in the folder: the oldest goes when the player is at the limit */
	public static boolean tryDeleteOldestSchematic(Path dir) {
		return tryDeleteOldestSchematic(dir, SchematicsRegistry.maxSchematics);
	}

	/** The same with the number of files that fit given */
	public static boolean tryDeleteOldestSchematic(Path dir, int maxSchematics) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			CreatePolymer.LOGGER.error("Error creating {}", dir, e);
			return false;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			List<Path> files = stream.filter(f -> !Files.isDirectory(f))
				.toList();
			if (files.size() < maxSchematics)
				return true;
			Optional<Path> oldest = files.stream()
				.min(Comparator.comparingLong(ServerSchematicLoader::getLastModifiedTime));
			Files.delete(oldest.orElseThrow());
			return true;
		} catch (IOException | IllegalStateException e) {
			CreatePolymer.LOGGER.error("Error deleting oldest schematic", e);
			return false;
		}
	}

	private static long getLastModifiedTime(Path file) {
		try {
			return Files.getLastModifiedTime(file)
				.toMillis();
		} catch (IOException e) {
			CreatePolymer.LOGGER.error("Error getting modification time of file {}", file.getFileName(), e);
			throw new IllegalStateException(e);
		}
	}

}
