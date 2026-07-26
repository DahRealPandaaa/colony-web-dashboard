package DahRealPanda.plugins.colonyweb.texture;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Function;

/**
 * Renders a {@link BlockModel} as a Minecraft-style inventory icon: an orthographic
 * projection with the vanilla GUI block rotation ({@code [30, 225, 0]}), so a stair reads as
 * a stair and a shingle reads as a shingle instead of as a flat swatch of its material.
 *
 * <p>Every face of every cuboid is rasterized through a z-buffer with nearest-neighbour
 * texture sampling and vanilla's directional face shading. Fully transparent texels are
 * skipped so cut-out geometry (fences, panels, trapdoors) keeps its silhouette.</p>
 */
public final class IsometricRenderer {
    /** Supersampling factor; the render is box-filtered down to the requested size. */
    private static final int SUPERSAMPLE = 2;

    /** Vanilla block face shading (see {@code Direction} light multipliers). */
    private static final double SHADE_UP = 1.0;
    private static final double SHADE_DOWN = 0.5;
    private static final double SHADE_NS = 0.8;
    private static final double SHADE_EW = 0.6;

    private static final double GUI_PITCH_DEGREES = 30;

    /**
     * The vanilla GUI yaw, negated.
     *
     * <p>Minecraft's item transform is {@code [30, 225, 0]}, but the GUI pass also mirrors the
     * scene ({@code poseStack.scale(16, -16, 16)}), which flips the handedness the yaw is
     * applied in. Rotating by a plain +225° here produced a horizontally mirrored block — the
     * far corner instead of the near one. Checked against the game's own renders: a
     * {@code facing=north} furnace shows its front on the left, and a crafting table shows its
     * front texture on both visible sides, which only holds when the visible faces are
     * <em>up</em>, <em>north</em> (left) and <em>west</em> (right).</p>
     */
    private static final double GUI_YAW_DEGREES = -225;

    private static final double COS_X = Math.cos(Math.toRadians(GUI_PITCH_DEGREES));
    private static final double SIN_X = Math.sin(Math.toRadians(GUI_PITCH_DEGREES));
    private static final double COS_Y = Math.cos(Math.toRadians(GUI_YAW_DEGREES));
    private static final double SIN_Y = Math.sin(Math.toRadians(GUI_YAW_DEGREES));

    /** Largest half-extent of a rotated 16-unit cube; used to fit the model to the canvas. */
    private static final double MODEL_HALF_EXTENT = 12.6;

    private IsometricRenderer() {
    }

    /**
     * @param model      geometry to draw
     * @param textureFor maps a face's texture variable to its pixels (null to skip the face)
     * @param size       output edge length in pixels
     * @return the rendered icon, or null when nothing was drawn
     */
    public static BufferedImage render(BlockModel model, Function<String, BufferedImage> textureFor, int size) {
        if (model == null || model.elements.isEmpty()) {
            return null;
        }
        int canvas = size * SUPERSAMPLE;
        int[] argb = new int[canvas * canvas];
        double[] depth = new double[canvas * canvas];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);

        double scale = (canvas * 0.5 * 0.96) / MODEL_HALF_EXTENT;
        boolean drewAnything = false;

        for (BlockModel.Element element : model.elements) {
            for (Map.Entry<String, BlockModel.Face> entry : element.faces.entrySet()) {
                BufferedImage texture = textureFor.apply(entry.getValue().texture);
                if (texture == null) {
                    continue;
                }
                drewAnything |= drawFace(argb, depth, canvas, scale, element,
                        entry.getKey(), entry.getValue(), texture);
            }
        }
        if (!drewAnything) {
            return null;
        }
        return downsample(argb, canvas, size);
    }

    // ------------------------------------------------------------------
    // Face geometry
    // ------------------------------------------------------------------

    private static boolean drawFace(int[] argb, double[] depth, int canvas, double scale,
                                    BlockModel.Element element, String direction,
                                    BlockModel.Face face, BufferedImage texture) {
        double[] uv = face.uv != null ? face.uv : defaultUv(element, direction);
        if (uv == null) {
            return false;
        }
        double shade = shadeFor(direction);

        // Quad corners at parametric (s,t) = (0,0) (1,0) (1,1) (0,1) across the face.
        double[][] pos = new double[4][];
        double[][] tex = new double[4][];
        double[][] st = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            double s = st[i][0];
            double t = st[i][1];
            double[] point = facePoint(element, direction, s, t);
            if (point == null) {
                return false;
            }
            pos[i] = project(transform(point, element.rotation), canvas, scale);
            double[] rotated = rotateUv(s, t, face.rotation);
            tex[i] = new double[]{lerp(uv[0], uv[2], rotated[0]), lerp(uv[1], uv[3], rotated[1])};
        }

        boolean drew = triangle(argb, depth, canvas, pos[0], pos[1], pos[2], tex[0], tex[1], tex[2], texture, shade);
        drew |= triangle(argb, depth, canvas, pos[0], pos[2], pos[3], tex[0], tex[2], tex[3], texture, shade);
        return drew;
    }

    /** A point on the given face of the element at parametric coordinates (s, t). */
    private static double[] facePoint(BlockModel.Element e, String direction, double s, double t) {
        double x0 = e.from[0];
        double y0 = e.from[1];
        double z0 = e.from[2];
        double x1 = e.to[0];
        double y1 = e.to[1];
        double z1 = e.to[2];
        return switch (direction) {
            case "up" -> new double[]{lerp(x0, x1, s), y1, lerp(z0, z1, t)};
            case "down" -> new double[]{lerp(x0, x1, s), y0, lerp(z1, z0, t)};
            case "north" -> new double[]{lerp(x1, x0, s), lerp(y1, y0, t), z0};
            case "south" -> new double[]{lerp(x0, x1, s), lerp(y1, y0, t), z1};
            case "west" -> new double[]{x0, lerp(y1, y0, t), lerp(z0, z1, s)};
            case "east" -> new double[]{x1, lerp(y1, y0, t), lerp(z1, z0, s)};
            default -> null;
        };
    }

    /** Vanilla's default UV window for a face: the element box projected onto that plane. */
    private static double[] defaultUv(BlockModel.Element e, String direction) {
        double x0 = e.from[0];
        double y0 = e.from[1];
        double z0 = e.from[2];
        double x1 = e.to[0];
        double y1 = e.to[1];
        double z1 = e.to[2];
        return switch (direction) {
            case "up" -> new double[]{x0, z0, x1, z1};
            case "down" -> new double[]{x0, 16 - z1, x1, 16 - z0};
            case "north" -> new double[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
            case "south" -> new double[]{x0, 16 - y1, x1, 16 - y0};
            case "west" -> new double[]{z0, 16 - y1, z1, 16 - y0};
            case "east" -> new double[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
            default -> null;
        };
    }

    private static double shadeFor(String direction) {
        return switch (direction) {
            case "up" -> SHADE_UP;
            case "down" -> SHADE_DOWN;
            case "north", "south" -> SHADE_NS;
            default -> SHADE_EW;
        };
    }

    /** Rotate parametric face coordinates to honour a face's {@code rotation} property. */
    private static double[] rotateUv(double s, double t, int rotation) {
        int steps = ((rotation / 90) % 4 + 4) % 4;
        return switch (steps) {
            case 1 -> new double[]{t, 1 - s};
            case 2 -> new double[]{1 - s, 1 - t};
            case 3 -> new double[]{1 - t, s};
            default -> new double[]{s, t};
        };
    }

    // ------------------------------------------------------------------
    // Transform + projection
    // ------------------------------------------------------------------

    /** Apply the element's own rotation, then the vanilla GUI view rotation. */
    private static double[] transform(double[] p, BlockModel.Rotation rotation) {
        double x = p[0];
        double y = p[1];
        double z = p[2];

        if (rotation != null && rotation.angle != 0) {
            double angle = Math.toRadians(rotation.angle);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double ox = rotation.origin[0];
            double oy = rotation.origin[1];
            double oz = rotation.origin[2];
            double dx = x - ox;
            double dy = y - oy;
            double dz = z - oz;
            double factor = rotation.rescale ? 1.0 / Math.cos(angle) : 1.0;
            switch (rotation.axis) {
                case "x" -> {
                    double ny = dy * cos - dz * sin;
                    double nz = dy * sin + dz * cos;
                    dy = ny * factor;
                    dz = nz * factor;
                }
                case "z" -> {
                    double nx = dx * cos - dy * sin;
                    double ny = dx * sin + dy * cos;
                    dx = nx * factor;
                    dy = ny * factor;
                }
                default -> {
                    double nx = dx * cos + dz * sin;
                    double nz = -dx * sin + dz * cos;
                    dx = nx * factor;
                    dz = nz * factor;
                }
            }
            x = ox + dx;
            y = oy + dy;
            z = oz + dz;
        }

        // Centre on the block, then rotate Y by 225 degrees and X by 30 (vanilla GUI transform).
        x -= 8;
        y -= 8;
        z -= 8;
        double rx = x * COS_Y + z * SIN_Y;
        double rz = -x * SIN_Y + z * COS_Y;
        double ry = y * COS_X - rz * SIN_X;
        double rzz = y * SIN_X + rz * COS_X;
        return new double[]{rx, ry, rzz};
    }

    /** Orthographic projection to screen pixels; the third component stays as depth. */
    private static double[] project(double[] v, int canvas, double scale) {
        double half = canvas * 0.5;
        return new double[]{half + v[0] * scale, half - v[1] * scale, v[2]};
    }

    // ------------------------------------------------------------------
    // Rasterization
    // ------------------------------------------------------------------

    private static boolean triangle(int[] argb, double[] depth, int canvas,
                                    double[] a, double[] b, double[] c,
                                    double[] uvA, double[] uvB, double[] uvC,
                                    BufferedImage texture, double shade) {
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1e-9) {
            return false;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = Math.min(canvas - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = Math.min(canvas - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));

        int texW = texture.getWidth();
        int texH = texture.getHeight();
        boolean drew = false;

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double sx = px + 0.5;
                double sy = py + 0.5;
                double w0 = ((b[0] - a[0]) * (sy - a[1]) - (b[1] - a[1]) * (sx - a[0])) / area;
                double w1 = ((sx - a[0]) * (c[1] - a[1]) - (sy - a[1]) * (c[0] - a[0])) / area;
                double w2 = 1.0 - w0 - w1;
                // w1 weights b, w0 weights c, w2 weights a.
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) {
                    continue;
                }
                double z = w2 * a[2] + w1 * b[2] + w0 * c[2];
                int idx = py * canvas + px;
                if (z <= depth[idx]) {
                    continue;
                }
                double u = w2 * uvA[0] + w1 * uvB[0] + w0 * uvC[0];
                double v = w2 * uvA[1] + w1 * uvB[1] + w0 * uvC[1];
                int tx = Math.floorMod((int) Math.floor(u / 16.0 * texW), texW);
                int ty = Math.floorMod((int) Math.floor(v / 16.0 * texH), texH);
                int texel = texture.getRGB(tx, ty);
                int alpha = (texel >>> 24) & 0xFF;
                if (alpha < 8) {
                    continue;
                }
                depth[idx] = z;
                argb[idx] = shadeColor(texel, shade);
                drew = true;
            }
        }
        return drew;
    }

    private static int shadeColor(int argb, double shade) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * shade);
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * shade);
        int b = (int) Math.min(255, (argb & 0xFF) * shade);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Box-filter the supersampled buffer down to the requested icon size. */
    private static BufferedImage downsample(int[] argb, int canvas, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int factor = canvas / size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                long a = 0;
                long r = 0;
                long g = 0;
                long b = 0;
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        int p = argb[(y * factor + dy) * canvas + (x * factor + dx)];
                        int pa = (p >>> 24) & 0xFF;
                        a += pa;
                        // Weight colour by alpha so transparent pixels do not darken edges.
                        r += ((p >> 16) & 0xFF) * pa;
                        g += ((p >> 8) & 0xFF) * pa;
                        b += (p & 0xFF) * pa;
                    }
                }
                int samples = factor * factor;
                int outA = (int) (a / samples);
                if (a == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int outR = (int) (r / a);
                int outG = (int) (g / a);
                int outB = (int) (b / a);
                out.setRGB(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
        return out;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
