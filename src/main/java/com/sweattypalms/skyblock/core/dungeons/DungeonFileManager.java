package com.sweattypalms.skyblock.core.dungeons;

import com.sweattypalms.skyblock.SkyBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DungeonFileManager {

    private DungeonFileManager() {
    }

    public static Path getDungeonRoot() {
        return SkyBlock.getInstance().getDataFolder().toPath().resolve("dungeons");
    }

    public static Path getSamplesRoot() {
        return getDungeonRoot().resolve("samples");
    }

    public static void ensureStructure() throws IOException {
        Files.createDirectories(getSamplesRoot().resolve("specialTypes"));
    }

    public static void importSamples(Path sourceSamplesDir) throws IOException {
        if (!Files.exists(sourceSamplesDir) || !Files.isDirectory(sourceSamplesDir)) {
            throw new IOException("Source samples directory does not exist: " + sourceSamplesDir);
        }

        ensureStructure();
        Path target = getSamplesRoot();
        clearDirectory(target);

        try (Stream<Path> stream = Files.walk(sourceSamplesDir)) {
            stream.forEach(source -> {
                Path relative = sourceSamplesDir.relativize(source);
                Path destination = target.resolve(relative);
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private static void clearDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(path))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }
}

