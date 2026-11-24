package com.katalan.core.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns every temp directory katalan creates under {@code java.io.tmpdir}
 * (Chrome profiles, preprocessed keywords/include/listener sources, bin/groovy staging)
 * and makes sure they are removed. Before this existed nothing was ever deleted:
 * {@code File.deleteOnExit()} silently skips non-empty directories, and Chrome profiles
 * had no cleanup at all, so long-running agents filled the disk.
 *
 * <p>Three layers, each a safety net for the one before:
 * <ol>
 *   <li><b>After a browser closes</b> — {@link #onBrowserClosed()} deletes, on a background
 *       thread, the Chrome profiles of this JVM whose Chrome process is gone. Test execution
 *       never waits for it.</li>
 *   <li><b>JVM shutdown</b> — every directory this JVM created is deleted.</li>
 *   <li><b>Startup sweep</b> — leftovers of runs that were killed hard ({@code kill -9},
 *       where shutdown hooks never run) are deleted, but only when their owning process
 *       is dead.</li>
 * </ol>
 *
 * <p>Parallel-safety: each directory gets a {@code .katalan-owner} marker holding the PID of
 * the JVM that created it. Another JVM never deletes a directory whose owner is still alive.
 * Directories without a marker (created by katalan versions before this class) are only
 * swept when older than {@link #LEGACY_MAX_AGE_MS}, and Chrome profiles whose
 * {@code SingletonLock} points at a live process are always left alone.
 *
 * <p>Kill switch: {@code -Dkatalan.tempCleanup=false} or env {@code KATALAN_TEMP_CLEANUP=false}
 * restores the old behaviour (nothing deleted) without a redeploy.
 */
public final class TempDirManager {

    private static final Logger logger = LoggerFactory.getLogger(TempDirManager.class);

    public static final String PREFIX = "katalan-";
    public static final String CHROME_PREFIX = PREFIX + "chrome-";
    static final String OWNER_MARKER = ".katalan-owner";

    /** Marker-less (pre-upgrade) leftovers are swept only after this age. */
    static final long LEGACY_MAX_AGE_MS = TimeUnit.HOURS.toMillis(12);

    /** Chrome's child processes can hold files briefly after the main process exits. */
    private static final long RELEASE_DELAY_MS = 1500;
    private static final int RELEASE_ATTEMPTS = 3;

    private static final long OWN_PID = ProcessHandle.current().pid();

    /** Every directory this JVM created and has not deleted yet. */
    private static final Set<Path> owned = ConcurrentHashMap.newKeySet();
    /** Chrome profile → Chrome browser PID (-1 when unknown: deleted at shutdown only). */
    private static final Map<Path, Long> chromeProfiles = new ConcurrentHashMap<>();

    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "katalan-temp-cleanup");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean initialized = false;

    private TempDirManager() {}

    public static boolean isEnabled() {
        String prop = System.getProperty("katalan.tempCleanup");
        if (prop == null) prop = System.getenv("KATALAN_TEMP_CLEANUP");
        return prop == null || !prop.trim().equalsIgnoreCase("false");
    }

    /** Registers the shutdown hook and starts the background startup sweep. Idempotent. */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        if (!isEnabled()) {
            logger.info("🧹 Temp cleanup disabled (katalan.tempCleanup=false)");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(TempDirManager::deleteAllOwned, "katalan-temp-shutdown"));
        worker.submit(() -> sweepStale(tmpRoot()));
    }

    /**
     * Creates a unique temp directory (like {@link Files#createTempDirectory}) tagged with this
     * JVM's PID and tracked for deletion. Unlike the old {@code currentTimeMillis()} naming,
     * two parallel processes can never receive the same directory.
     */
    public static Path createOwnedTempDir(String prefix) throws IOException {
        if (!initialized) initialize(); // entry points other than KatalanEngine still get cleanup
        Path dir = Files.createTempDirectory(prefix);
        try {
            Files.write(dir.resolve(OWNER_MARKER), String.valueOf(OWN_PID).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.debug("Could not write owner marker in {}: {}", dir, e.getMessage());
        }
        owned.add(dir);
        return dir;
    }

    /** Creates a Chrome profile directory; call {@link #bindChromePid} once Chrome has started. */
    public static Path createChromeProfile() throws IOException {
        Path dir = createOwnedTempDir(CHROME_PREFIX);
        chromeProfiles.put(dir, -1L);
        return dir;
    }

    public static void bindChromePid(Path profile, long chromePid) {
        if (profile != null && chromeProfiles.containsKey(profile)) {
            chromeProfiles.put(profile, chromePid);
        }
    }

    /**
     * Call after any {@code driver.quit()}. Returns immediately; profiles whose Chrome is gone
     * are deleted on a background thread. Profiles of browsers still running (another test in
     * this JVM, or a pooled driver) are untouched because their Chrome PID is alive.
     */
    public static void onBrowserClosed() {
        if (!isEnabled() || chromeProfiles.isEmpty()) return;
        try {
            worker.submit(TempDirManager::releaseDeadChromeProfiles);
        } catch (Exception e) {
            logger.debug("Temp cleanup not scheduled: {}", e.getMessage());
        }
    }

    private static void releaseDeadChromeProfiles() {
        for (int attempt = 0; attempt < RELEASE_ATTEMPTS; attempt++) {
            sleepQuietly(RELEASE_DELAY_MS);
            boolean pending = false;
            for (Map.Entry<Path, Long> e : chromeProfiles.entrySet()) {
                long pid = e.getValue();
                if (pid <= 0 || isAlive(pid)) continue;
                Path profile = e.getKey();
                if (deleteRecursively(profile)) {
                    chromeProfiles.remove(profile);
                    owned.remove(profile);
                    logger.debug("🧹 Deleted Chrome profile {}", profile.getFileName());
                } else {
                    pending = true; // a child process still holds a file — retry
                }
            }
            if (!pending) return;
        }
    }

    static void deleteAllOwned() {
        int deleted = 0;
        for (Path dir : owned) {
            if (deleteRecursively(dir)) deleted++;
        }
        owned.clear();
        chromeProfiles.clear();
        if (deleted > 0) logger.info("🧹 Deleted {} katalan temp dir(s)", deleted);
    }

    /** Deletes leftovers of dead katalan processes under {@code root}. Package-private for tests. */
    static int sweepStale(Path root) {
        int deleted = 0;
        long now = System.currentTimeMillis();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, PREFIX + "*")) {
            for (Path dir : ds) {
                try {
                    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || owned.contains(dir)) continue;
                    if (isStale(dir, now) && deleteRecursively(dir)) deleted++;
                } catch (Exception e) {
                    logger.debug("Skip {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.debug("Startup temp sweep skipped: {}", e.getMessage());
        }
        if (deleted > 0) logger.info("🧹 Startup sweep removed {} stale katalan temp dir(s)", deleted);
        return deleted;
    }

    static boolean isStale(Path dir, long now) throws IOException {
        Optional<Long> owner = readOwnerPid(dir);
        if (owner.isPresent()) {
            long pid = owner.get();
            return pid != OWN_PID && !isAlive(pid);
        }
        // No marker: created by an older katalan, or by a process that is writing it right now.
        if (now - Files.getLastModifiedTime(dir, LinkOption.NOFOLLOW_LINKS).toMillis() < LEGACY_MAX_AGE_MS) {
            return false;
        }
        return !chromeLockHeldByLiveProcess(dir);
    }

    static Optional<Long> readOwnerPid(Path dir) {
        Path marker = dir.resolve(OWNER_MARKER);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try {
            String s = new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim();
            return Optional.of(Long.parseLong(s));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * macOS/Linux Chrome keeps a {@code SingletonLock} symlink ("hostname-PID") while running.
     * On Windows the profile's lockfile is held open instead, so the delete itself fails safely.
     */
    static boolean chromeLockHeldByLiveProcess(Path dir) {
        Path lock = dir.resolve("SingletonLock");
        try {
            if (!Files.isSymbolicLink(lock)) return false;
            String target = Files.readSymbolicLink(lock).toString();
            int dash = target.lastIndexOf('-');
            if (dash < 0) return true; // unknown format → be conservative
            return isAlive(Long.parseLong(target.substring(dash + 1)));
        } catch (Exception e) {
            return true; // cannot tell → keep it
        }
    }

    static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** Best-effort recursive delete that never follows symlinks. Returns true when gone. */
    static boolean deleteRecursively(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return true;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    try { Files.deleteIfExists(d); } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.debug("Delete {} failed: {}", dir, e.getMessage());
        }
        return !Files.exists(dir, LinkOption.NOFOLLOW_LINKS);
    }

    static Path tmpRoot() {
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
