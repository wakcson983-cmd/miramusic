package com.miramusic.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reproductor local (solo lo oye quien lo usa). Obtiene el audio con yt-dlp, lo convierte con ffmpeg
 * a PCM y lo reproduce con Java Sound. Maneja una lista: anterior / pausa / siguiente.
 */
public final class MusicPlayer {
    private static final Logger LOG = LoggerFactory.getLogger("MiraMusic");
    public static final MusicPlayer INSTANCE = new MusicPlayer();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Pattern ID_PATTERN =
            Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/|/v/)([A-Za-z0-9_-]{11})");
    private static final int BYTES_PER_SEC = 44100 * 4;
    private static int thumbCounter = 0;

    public static final class Track {
        public final String id;
        public final String url;
        public volatile String title = "";
        public volatile Identifier thumb;
        public volatile int tw, th;

        Track(String id) {
            this.id = id;
            this.url = "https://www.youtube.com/watch?v=" + id;
        }
    }

    private enum Result { PLAYED, FAILED, CANCELLED }

    private final List<Track> tracks = new ArrayList<>();
    private int index = -1;
    private final AtomicInteger gen = new AtomicInteger();
    private volatile boolean paused;
    private volatile boolean active;
    private volatile float volume = 0.8f;
    private volatile String status = "";
    private volatile long statusUntil = 0;
    private volatile long playedBytes = 0;
    private volatile List<Process> procs = List.of();

    private MusicPlayer() {}

    // ------------------------------------------------------------ estado para la interfaz

    public synchronized Track current() { return index >= 0 && index < tracks.size() ? tracks.get(index) : null; }
    public synchronized int index() { return index; }
    public synchronized int size() { return tracks.size(); }
    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return active && !paused; }
    public float volume() { return volume; }

    public void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }

    public String status() {
        if (statusUntil > 0 && System.currentTimeMillis() > statusUntil) {
            status = "";
            statusUntil = 0;
        }
        return status;
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = 0;
    }

    private void flash(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------ controles

    public static String parseId(String input) {
        String s = input.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        if (!s.contains("youtu")) return null;
        Matcher m = ID_PATTERN.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** Añade el link a la lista y lo reproduce enseguida. */
    public boolean addAndPlay(String input) {
        String id = parseId(input);
        if (id == null) {
            flash("Ese link no parece de YouTube");
            return false;
        }
        Track t = new Track(id);
        synchronized (this) {
            tracks.add(t);
            index = tracks.size() - 1;
        }
        loadInfoAsync(t);
        startCurrent();
        return true;
    }

    public void togglePause() {
        if (current() == null) {
            flash("Escribe un link primero");
            return;
        }
        if (!active) {
            startCurrent();
            return;
        }
        paused = !paused;
    }

    public void next() {
        synchronized (this) {
            if (index + 1 >= tracks.size()) {
                flash("No hay más canciones");
                return;
            }
            index++;
        }
        startCurrent();
    }

    public void prev() {
        synchronized (this) {
            if (index < 0) {
                flash("Escribe un link primero");
                return;
            }
            // Si ya sonaron más de 4 segundos, "anterior" reinicia la canción actual.
            if (playedBytes / BYTES_PER_SEC < 4 && index > 0) {
                index--;
            }
        }
        startCurrent();
    }

    public void stopAll() {
        gen.incrementAndGet();
        killProcs();
        active = false;
        paused = false;
        setStatus("");
        List<Track> old;
        synchronized (this) {
            old = new ArrayList<>(tracks);
            tracks.clear();
            index = -1;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Track t : old) {
            Identifier id = t.thumb;
            if (id != null) {
                mc.execute(() -> mc.getTextureManager().destroyTexture(id));
            }
        }
    }

    // ------------------------------------------------------------ reproducción

    private boolean cancelled(int g) { return gen.get() != g; }

    private void startCurrent() {
        Track t = current();
        if (t == null) return;
        int g = gen.incrementAndGet();
        killProcs();
        paused = false;
        playedBytes = 0;
        active = true;
        setStatus("Cargando...");
        Thread th = new Thread(() -> run(t, g), "MiraMusic-player");
        th.setDaemon(true);
        th.start();
    }

    private void killProcs() {
        kill(procs);
        procs = List.of();
    }

    private static void kill(List<Process> ps) {
        for (Process p : ps) {
            try {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            } catch (Exception ignored) {
                // ya terminó
            }
        }
    }

    private void run(Track t, int g) {
        boolean triedUpdate = false;
        try {
            while (true) {
                Tools.refresh();
                if (!Tools.ready()) {
                    setStatus("Faltan herramientas: " + Tools.missingList());
                    return;
                }
                Result r = stream(t, g);
                if (cancelled(g) || r == Result.CANCELLED) return;
                if (r == Result.PLAYED) {
                    advance(g);
                    return;
                }
                if (!triedUpdate && Tools.canSelfUpdate()) {
                    triedUpdate = true;
                    setStatus("Actualizando yt-dlp...");
                    Tools.updateYtDlp();
                    if (cancelled(g)) return;
                    setStatus("Cargando...");
                    continue;
                }
                setStatus("No se pudo reproducir" + (Tools.denoMissing() ? " (falta deno)" : "")
                        + ". Revisa config/miramusic/ytdlp.log");
                return;
            }
        } finally {
            if (!cancelled(g)) {
                active = false;
            }
        }
    }

    private void advance(int g) {
        boolean hasNext;
        synchronized (this) {
            hasNext = index + 1 < tracks.size();
            if (hasNext && !cancelled(g)) index++;
        }
        if (cancelled(g)) return;
        if (hasNext) {
            startCurrent();
        } else {
            active = false;
            flash("Terminó la lista");
        }
    }

    private Result stream(Track t, int g) {
        List<Process> ps = List.of();
        SourceDataLine line = null;
        long total = 0;
        try {
            Files.createDirectories(Tools.DIR);
            ProcessBuilder yt = new ProcessBuilder(Tools.ytdlp(), "-f", "bestaudio/best", "--no-playlist",
                    "--no-warnings", "-q", "--ffmpeg-location", Tools.ffmpeg(), "-o", "-", t.url);
            ProcessBuilder ff = new ProcessBuilder(Tools.ffmpeg(), "-hide_banner", "-loglevel", "error",
                    "-i", "pipe:0", "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2", "pipe:1");
            Tools.prepEnv(yt);
            Tools.prepEnv(ff);
            yt.redirectError(ProcessBuilder.Redirect.to(Tools.DIR.resolve("ytdlp.log").toFile()));
            ff.redirectError(ProcessBuilder.Redirect.to(Tools.DIR.resolve("ffmpeg.log").toFile()));
            ps = ProcessBuilder.startPipeline(List.of(yt, ff));
            procs = ps;
            if (cancelled(g)) {
                kill(ps);
                return Result.CANCELLED;
            }

            AudioFormat fmt = new AudioFormat(44100f, 16, 2, true, false);
            boolean stopped = false;
            byte[] buf = new byte[8192];
            try (InputStream in = ps.get(ps.size() - 1).getInputStream()) {
                while (!cancelled(g)) {
                    int n = in.readNBytes(buf, 0, buf.length);
                    if (n <= 0) break;
                    n -= n % 4;
                    if (n <= 0) break;
                    if (line == null) {
                        line = AudioSystem.getSourceDataLine(fmt);
                        line.open(fmt, 32768);
                        line.start();
                        setStatus("");
                    }
                    applyGain(buf, n, volume);
                    while (paused && !cancelled(g)) {
                        if (!stopped) {
                            line.stop();
                            stopped = true;
                        }
                        Thread.sleep(40);
                    }
                    if (cancelled(g)) break;
                    if (stopped) {
                        line.start();
                        stopped = false;
                    }
                    line.write(buf, 0, n);
                    total += n;
                    playedBytes = total;
                }
                if (line != null && !cancelled(g)) {
                    line.drain();
                }
            }
        } catch (Exception e) {
            if (!cancelled(g)) LOG.warn("Error reproduciendo {}", t.url, e);
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                    line.close();
                } catch (Exception ignored) {
                    // cerrando
                }
            }
            kill(ps);
        }
        if (cancelled(g)) return Result.CANCELLED;
        return total > 0 ? Result.PLAYED : Result.FAILED;
    }

    private static void applyGain(byte[] b, int n, float v) {
        float g = v * v;
        if (g >= 0.999f) return;
        for (int i = 0; i + 1 < n; i += 2) {
            int s = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            s = Math.round(s * g);
            b[i] = (byte) s;
            b[i + 1] = (byte) (s >> 8);
        }
    }

    // ------------------------------------------------------------ título y miniatura

    private void loadInfoAsync(Track t) {
        Thread th = new Thread(() -> {
            try {
                String api = "https://www.youtube.com/oembed?format=json&url="
                        + URLEncoder.encode(t.url, StandardCharsets.UTF_8);
                byte[] body = get(api);
                JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("title")) t.title = o.get("title").getAsString();
            } catch (Exception e) {
                LOG.debug("Sin título para {}", t.id);
            }
            try {
                byte[] img = get("https://i.ytimg.com/vi/" + t.id + "/mqdefault.jpg");
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(img));
                if (bi == null) return;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", bo);
                byte[] png = bo.toByteArray();
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    try {
                        NativeImage ni = NativeImage.read(new ByteArrayInputStream(png));
                        Identifier id = Identifier.of("miramusic", "thumb_" + (thumbCounter++));
                        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(ni));
                        t.tw = ni.getWidth();
                        t.th = ni.getHeight();
                        t.thumb = id;
                    } catch (IOException e) {
                        LOG.debug("Miniatura inválida", e);
                    }
                });
            } catch (Exception e) {
                LOG.debug("Sin miniatura para {}", t.id);
            }
        }, "MiraMusic-info");
        th.setDaemon(true);
        th.start();
    }

    private static byte[] get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }
}
