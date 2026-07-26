import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;

/**
 * Downloads the MineColonies wiki images named in a manifest and writes downscaled copies into
 * the mod's webroot.
 *
 * <pre>
 *   node tools/wiki-images.js &gt; tools/wiki-images.tsv
 *   java tools/WikiImages.java tools/wiki-images.tsv src/main/resources/webroot/img
 * </pre>
 *
 * <p>Manifest is TSV: {@code url \t outPath \t maxW \t maxH \t format}. Full-size wiki
 * screenshots are 100–800 KB each; the dashboard shows them at thumbnail size, so shrinking
 * them here is the difference between a ~1 MB and a ~30 MB jar.</p>
 *
 * <p>Deliberately dependency-free (plain ImageIO) so it runs with nothing but a JDK.</p>
 */
public final class WikiImages {

    /** JPEG quality for building shots — they are photographic, so this compresses well. */
    private static final float JPEG_QUALITY = 0.82f;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: WikiImages <manifest.tsv> <outputDir>");
            System.exit(2);
        }
        Path manifest = Path.of(args[0]);
        Path outRoot = Path.of(args[1]);

        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        List<String> lines = Files.readAllLines(manifest);
        int done = 0;
        int failed = 0;
        long bytes = 0;

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length < 5) {
                System.err.println("skipping malformed line: " + line);
                failed++;
                continue;
            }
            String url = parts[0];
            Path out = outRoot.resolve(parts[1]);
            int maxW = Integer.parseInt(parts[2]);
            int maxH = Integer.parseInt(parts[3]);
            String format = parts[4];

            try {
                byte[] source = download(http, url);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
                if (image == null) {
                    throw new IllegalStateException("not a decodable image");
                }
                BufferedImage scaled = scaleToFit(image, maxW, maxH, "jpg".equals(format));
                Files.createDirectories(out.getParent());
                byte[] encoded = "jpg".equals(format) ? encodeJpeg(scaled) : encodePng(scaled);
                Files.write(out, encoded);
                bytes += encoded.length;
                done++;
                System.out.printf("%3d/%d  %-34s %6.1f KB  (from %.0f KB)%n",
                        done, lines.size(), parts[1], encoded.length / 1024.0, source.length / 1024.0);
            } catch (Exception e) {
                System.err.println("FAILED " + parts[1] + ": " + e);
                failed++;
            }
        }
        System.out.printf("%nwrote %d images, %.1f MB total, %d failed%n",
                done, bytes / 1048576.0, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static byte[] download(HttpClient http, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "colonyweb-asset-tool")
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Scale down to fit the box, preserving aspect ratio. Never scales up — a portrait smaller
     * than the target is already the right size and enlarging it only adds bytes and blur.
     */
    private static BufferedImage scaleToFit(BufferedImage src, int maxW, int maxH, boolean opaque) {
        double scale = Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight());
        int w = scale < 1 ? (int) Math.round(src.getWidth() * scale) : src.getWidth();
        int h = scale < 1 ? (int) Math.round(src.getHeight() * scale) : src.getHeight();

        int type = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage dst = new BufferedImage(w, h, type);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static byte[] encodePng(BufferedImage image) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("no JPEG writer available");
        }
        ImageWriter writer = writers.next();
        var out = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
