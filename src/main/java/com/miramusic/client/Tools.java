package com.miramusic.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Herramientas externas que necesita el reproductor: yt-dlp (obtiene el audio de YouTube),
 * ffmpeg (lo convierte a PCM) y deno (lo exige yt-dlp para YouTube).
 * Se buscan primero en config/miramusic/ y luego en el PATH. En Windows se pueden instalar con un click.
 */
public final class Tools {
    private static final Logger LOG = LoggerFactory.getLogger("MiraMusic");

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("miramusic");
    public static final boolean WIN = System.getProperty("os.name", "").toLowerCase().contains("win");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private static volatile String ytdlp, ffmpeg, deno;
    private static volatile long lastRefresh = 0;
    private static volatile boolean installing = false;
    private static volatile String installStatus = "";

    private Tools() {}

    // ------------------------------------------------------------ búsqueda

    public static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 1500) return;
        lastRefresh = now;
        ytdlp = find("yt-dlp");
        ffmpeg = find("ffmpeg");
        deno = find("deno");
    }

    private static void forceRefresh() {
        lastRefresh = 0;
        refresh();
    }

    private static String find(String base) {
        String exe = WIN ? base + ".exe" : base;
        Path local = DIR.resolve(exe);
        if (Files.isRegularFile(local)) return local.toAbsolutePath().toString();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(File.pathSeparator)) {
                if (d.isBlank()) continue;
                try {
                    Path c = Path.of(d.replace("\"", "")).resolve(exe);
                    if (Files.isRegularFile(c)) return c.toString();
                } catch (Exception ignored) {
                    // ruta inválida en el PATH
                }
            }
        }
        return null;
    }

    public static String ytdlp() { return ytdlp; }
    public static String ffmpeg() { return ffmpeg; }

    /** yt-dlp y ffmpeg son imprescindibles para reproducir. */
    public static boolean ready() { return ytdlp != null && ffmpeg != null; }

    public static boolean denoMissing() { return deno == null; }

    public static String missingList() {
        List<String> m = new ArrayList<>();
        if (ytdlp == null) m.add("yt-dlp");
        if (ffmpeg == null) m.add("ffmpeg");
        if (deno == null) m.add("deno");
        return String.join(", ", m);
    }

    public static boolean canAutoInstall() { return WIN; }
    public static boolean isInstalling() { return installing; }
    public static String installStatus() { return installStatus; }

    /** Añade config/miramusic al PATH del proceso para que yt-dlp encuentre deno y ffmpeg. */
    public static void prepEnv(ProcessBuilder pb) {
        Map<String, String> env = pb.environment();
        String path = env.getOrDefault("PATH", "");
        env.put("PATH", DIR.toAbsolutePath() + File.pathSeparator + path);
    }

    // ------------------------------------------------------------ actualizar yt-dlp

    public static boolean canSelfUpdate() {
        String y = ytdlp;
        return y != null && y.startsWith(DIR.toAbsolutePath().toString());
    }

    public static void updateYtDlp() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ytdlp, "-U");
            prepEnv(pb);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (Exception e) {
            LOG.warn("No se pudo actualizar yt-dlp", e);
        }
    }

    // ------------------------------------------------------------ instalador (Windows)

    public static void installAsync() {
        if (installing) return;
        if (!WIN) {
            installStatus = "Instala yt-dlp, ffmpeg y deno a mano (mira el README)";
            return;
        }
        installing = true;
        installStatus = "Preparando...";
        Thread th = new Thread(() -> {
            try {
                Files.createDirectories(DIR);
                forceRefresh();
                if (ytdlp == null) {
                    download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
                            DIR.resolve("yt-dlp.exe"), "yt-dlp");
                }
                if (deno == null) {
                    Path z = DIR.resolve("deno.zip");
                    download("https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip",
                            z, "deno");
                    unzip(z, "deno.exe", DIR.resolve("deno.exe"));
                    Files.deleteIfExists(z);
                }
                if (ffmpeg == null) {
                    Path z = DIR.resolve("ffmpeg.zip");
                    download("https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip",
                            z, "ffmpeg");
                    installStatus = "Extrayendo ffmpeg...";
                    unzip(z, "ffmpeg.exe", DIR.resolve("ffmpeg.exe"));
                    Files.deleteIfExists(z);
                }
                installStatus = "¡Listo!";
            } catch (Exception e) {
                LOG.warn("Fallo la instalación de herramientas", e);
                installStatus = "Error: " + e.getMessage();
            } finally {
                installing = false;
                forceRefresh();
            }
        }, "MiraMusic-installer");
        th.setDaemon(true);
        th.start();
    }

    private static void download(String url, Path target, String label) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "MiraMusic").GET().build();
        HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) {
            throw new IOException("HTTP " + r.statusCode() + " al bajar " + label);
        }
        long len = r.headers().firstValueAsLong("content-length").orElse(-1L);
        Path part = Path.of(target.toString() + ".part");
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] b = new byte[65536];
            long tot = 0;
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                tot += n;
                installStatus = "Bajando " + label + " " + (len > 0 ? (tot * 100 / len) + "%" : (tot / 1048576) + " MB");
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void unzip(Path zip, String fileName, Path target) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName();
                if (!e.isDirectory() && (n.equals(fileName) || n.endsWith("/" + fileName))) {
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("No encontré " + fileName + " dentro del zip");
    }
}
