package DahRealPanda.plugins.colonyweb.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Draws the world's surface into a {@link ColonyMap} and encodes it as a PNG.
 *
 * <p>Colours come from each block's own {@link MapColor} — the same palette the in-game map item
 * uses — so the result reads like a map of the world rather than an invented colour scheme.
 * Height is kept per pixel and turned into hill shading only at encode time, because a slope
 * that crosses a chunk border can only be shaded once both of its chunks have been drawn.</p>
 */
final class SurfaceRenderer {
    /** How far below the surface water is still darkened, in blocks. */
    private static final int MAX_WATER_DEPTH = 16;

    /** Darkening applied per block of water depth. */
    private static final double WATER_STEP = 0.035;

    /** Brightness change per block of height difference against the north/west neighbours. */
    private static final double RELIEF_STEP = 0.055;

    private static final double RELIEF_MIN = 0.68;
    private static final double RELIEF_MAX = 1.30;

    private SurfaceRenderer() {
    }

    /**
     * Draw one loaded chunk into the map. Columns outside the map's bounds are skipped, so an
     * edge chunk contributes only the part that fits.
     */
    static void drawChunk(ColonyMap map, ServerLevel level, LevelChunk chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int floorY = level.getMinBuildHeight();

        for (int dz = 0; dz < 16; dz++) {
            int worldZ = baseZ + dz;
            int pixelZ = worldZ - map.minZ;
            if (pixelZ < 0 || pixelZ >= map.height) {
                continue;
            }
            for (int dx = 0; dx < 16; dx++) {
                int worldX = baseX + dx;
                int pixelX = worldX - map.minX;
                if (pixelX < 0 || pixelX >= map.width) {
                    continue;
                }
                drawColumn(map, level, chunk, pos, worldX, worldZ, floorY,
                        pixelZ * map.width + pixelX);
            }
        }
    }

    /** Find the topmost block that has a colour on a map, and record it. */
    private static void drawColumn(ColonyMap map, ServerLevel level, LevelChunk chunk,
                                   BlockPos.MutableBlockPos pos, int worldX, int worldZ,
                                   int floorY, int index) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        BlockState state = null;
        MapColor color = MapColor.NONE;

        // Plants, torches and the like report no map colour; keep walking down to what they
        // stand on, exactly as the map item does.
        while (y >= floorY) {
            pos.set(worldX, y, worldZ);
            state = chunk.getBlockState(pos);
            color = state.getMapColor(level, pos);
            if (color != MapColor.NONE) {
                break;
            }
            y--;
        }
        if (state == null || color == MapColor.NONE) {
            return;
        }

        int rgb = color.col;
        int depth = waterDepth(chunk, pos, state, worldX, worldZ, y, floorY);
        if (depth > 0) {
            rgb = scale(rgb, 1.0 - Math.min(depth, MAX_WATER_DEPTH) * WATER_STEP);
        }
        map.rgb[index] = 0xFF000000 | rgb;
        map.top[index] = y;
    }

    /** How deep the water at this column runs, so shallows read lighter than open ocean. */
    private static int waterDepth(LevelChunk chunk, BlockPos.MutableBlockPos pos, BlockState surface,
                                  int worldX, int worldZ, int surfaceY, int floorY) {
        if (surface.getFluidState().isEmpty()) {
            return 0;
        }
        int depth = 1;
        for (int y = surfaceY - 1; y >= floorY && depth <= MAX_WATER_DEPTH; y--) {
            pos.set(worldX, y, worldZ);
            if (chunk.getBlockState(pos).getFluidState().isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }

    /**
     * Encode a snapshot of the raster, shading each pixel against its north and west neighbours.
     *
     * <p>Runs off the server thread, which is why it takes copies of the arrays rather than the
     * live map.</p>
     *
     * @return PNG bytes, or null when the image could not be written
     */
    static byte[] encode(int width, int height, int[] rgb, int[] top) {
        int[] pixels = new int[rgb.length];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int i = z * width + x;
                int base = rgb[i];
                if (base == 0) {
                    continue; // never drawn — stays transparent, so the page shows "unmapped"
                }
                int h = top[i];
                int north = (z > 0 && rgb[i - width] != 0) ? top[i - width] : h;
                int west = (x > 0 && rgb[i - 1] != 0) ? top[i - 1] : h;
                double relief = 1.0 + RELIEF_STEP * ((h - north) + (h - west));
                pixels[i] = 0xFF000000 | scale(base, clamp(relief));
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static double clamp(double factor) {
        return Math.max(RELIEF_MIN, Math.min(RELIEF_MAX, factor));
    }

    /** Multiply an RGB triple by a brightness factor, keeping each channel in range. */
    private static int scale(int rgb, double factor) {
        int r = channel(((rgb >> 16) & 0xFF) * factor);
        int g = channel(((rgb >> 8) & 0xFF) * factor);
        int b = channel((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }
}
