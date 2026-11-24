package com.katalan.core.cleanup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempDirManagerTest {

    @TempDir
    Path root;

    private Path dirWithOwner(String name, long pid) throws Exception {
        Path d = Files.createDirectories(root.resolve(name));
        Files.write(d.resolve("Default-file"), new byte[1024]);
        Files.write(d.resolve(TempDirManager.OWNER_MARKER), String.valueOf(pid).getBytes(StandardCharsets.UTF_8));
        return d;
    }

    private Path legacyDir(String name, long ageMs) throws Exception {
        Path d = Files.createDirectories(root.resolve(name).resolve("Default"));
        Files.write(d.resolve("Preferences"), new byte[10]);
        Path top = root.resolve(name);
        Files.setLastModifiedTime(top, FileTime.fromMillis(System.currentTimeMillis() - ageMs));
        return top;
    }

    private static long deadPid() throws Exception {
        Process p = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        p.waitFor(10, TimeUnit.SECONDS);
        long pid = p.pid();
        assertFalse(TempDirManager.isAlive(pid));
        return pid;
    }

    @Test
    void sweepDeletesOnlyDirsWhoseOwnerIsDead() throws Exception {
        long own = ProcessHandle.current().pid();
        long liveOther = ProcessHandle.current().parent().map(ProcessHandle::pid).orElseThrow();

        Path ownDir = dirWithOwner("katalan-chrome-own", own);
        Path parallelRun = dirWithOwner("katalan-chrome-parallel", liveOther);
        Path deadRun = dirWithOwner("katalan-keywords-dead", deadPid());

        assertEquals(1, TempDirManager.sweepStale(root));

        assertTrue(Files.exists(ownDir), "own dir must survive");
        assertTrue(Files.exists(parallelRun), "dir of a live parallel run must survive");
        assertFalse(Files.exists(deadRun), "dir of a dead run must be deleted");
    }

    @Test
    void legacyDirsWithoutMarkerAreDeletedOnlyWhenOld() throws Exception {
        Path young = legacyDir("katalan-chrome-young", TimeUnit.HOURS.toMillis(1));
        Path old = legacyDir("katalan-chrome-old", TimeUnit.HOURS.toMillis(13));

        TempDirManager.sweepStale(root);

        assertTrue(Files.exists(young));
        assertFalse(Files.exists(old));
    }

    @Test
    void oldLegacyChromeProfileWithLiveSingletonLockIsKept() throws Exception {
        long liveOther = ProcessHandle.current().parent().map(ProcessHandle::pid).orElseThrow();
        Path inUse = legacyDir("katalan-chrome-longrun", TimeUnit.HOURS.toMillis(30));
        Files.createSymbolicLink(inUse.resolve("SingletonLock"), Path.of("my-host-" + liveOther));

        TempDirManager.sweepStale(root);

        assertTrue(Files.exists(inUse), "profile locked by a running Chrome must survive");
    }

    @Test
    void nonKatalanDirsAndSymlinkTargetsAreNeverTouched() throws Exception {
        Path other = Files.createDirectories(root.resolve("com.apple.something"));
        Files.setLastModifiedTime(other, FileTime.fromMillis(0));

        Path outside = Files.createTempDirectory("outside-target");
        Path precious = Files.write(outside.resolve("precious.txt"), new byte[]{1});
        Path link = Files.createSymbolicLink(root.resolve("katalan-chrome-link"), outside);

        TempDirManager.sweepStale(root);

        assertTrue(Files.exists(other));
        assertTrue(Files.exists(precious), "sweep must not follow symlinks");
        assertTrue(Files.isSymbolicLink(link));
        TempDirManager.deleteRecursively(outside);
    }

    @Test
    void ownedDirsCarryOwnerMarkerAndAreUnique() throws Exception {
        Path a = TempDirManager.createChromeProfile();
        Path b = TempDirManager.createChromeProfile();
        try {
            assertFalse(a.equals(b));
            assertEquals(ProcessHandle.current().pid(), TempDirManager.readOwnerPid(a).orElseThrow());
        } finally {
            TempDirManager.deleteRecursively(a);
            TempDirManager.deleteRecursively(b);
        }
    }
}
