package com.p2pconnect.mod.network;

import com.p2pconnect.mod.P2PConnectMod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the "bore" (https://github.com/ekzhang/bore) binary as a subprocess
 * to expose the local Minecraft server port publicly through bore.pub.
 *
 * Lookup order for the bore executable:
 *  1. config/p2pconnect/bore(.exe) - a manual install always takes priority.
 *  2. Bundled inside the mod jar (bore-bin/{windows,linux,macos}/bore(.exe),
 *     added by the fetchBoreBinaries Gradle task) - extracted to the same
 *     config/p2pconnect/ location the first time it's needed.
 *  3. The system PATH.
 * If none of those work, hosting fails with a clear error pointing at a
 * manual install, so nothing is worse off than before bundling was added.
 *
 * Usage: bore local <localPort> --to bore.pub
 * It prints a line like: "listening at bore.pub:23456"
 */
public class BoreManager {

    private static final Pattern PORT_PATTERN = Pattern.compile("listening at [^:\\s]+:(\\d+)");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    private Process boreProcess;

    public boolean isRunning() {
        return boreProcess != null && boreProcess.isAlive();
    }

    public void start(int localPort, Consumer<Integer> onPortAssigned, Consumer<String> onError) {
        stop();
        try {
            String exe = findBoreExecutable();
            if (exe == null) {
                onError.accept("Could not find (or extract) the bore binary. Place it manually at "
                        + "config/p2pconnect/bore(.exe) or add it to your PATH. Download: https://github.com/ekzhang/bore/releases");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(exe, "local", String.valueOf(localPort), "--to", "bore.pub");
            pb.redirectErrorStream(true);
            boreProcess = pb.start();

            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(boreProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean assigned = false;
                    while ((line = reader.readLine()) != null) {
                        String cleanLine = ANSI_PATTERN.matcher(line).replaceAll("");
                        P2PConnectMod.LOGGER.info("[bore] " + cleanLine);
                        if (!assigned) {
                            Matcher m = PORT_PATTERN.matcher(cleanLine);
                            if (m.find()) {
                                assigned = true;
                                int port = Integer.parseInt(m.group(1));
                                onPortAssigned.accept(port);
                            }
                        }
                    }
                } catch (Exception e) {
                    P2PConnectMod.LOGGER.warn("Error reading bore output: " + e.getMessage());
                }
            }, "bore-output-reader");
            readerThread.setDaemon(true);
            readerThread.start();

        } catch (Exception e) {
            onError.accept("Could not start bore: " + e.getMessage());
        }
    }

    public void stop() {
        if (boreProcess != null && boreProcess.isAlive()) {
            boreProcess.destroy();
        }
        boreProcess = null;
    }

    private String findBoreExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        String fileName = os.contains("win") ? "bore.exe" : "bore";

        // 1) Manual install always wins if present.
        Path local = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("p2pconnect").resolve(fileName);
        if (Files.isExecutable(local) || Files.exists(local)) {
            return local.toAbsolutePath().toString();
        }

        // 2) Bundled inside the jar - extract once to the same config location.
        String extracted = extractBundledBore(local, os);
        if (extracted != null) {
            return extracted;
        }

        // 3) System PATH.
        try {
            ProcessBuilder test = new ProcessBuilder(fileName, "--version");
            test.redirectErrorStream(true);
            Process p = test.start();
            p.waitFor();
            return fileName;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @return the extracted executable's absolute path, or null if nothing bundled for this platform / extraction failed. */
    private String extractBundledBore(Path targetPath, String osName) {
        String platformDir;
        if (osName.contains("win")) {
            platformDir = "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            platformDir = "macos";
        } else {
            platformDir = "linux";
        }
        String resourcePath = "/bore-bin/" + platformDir + "/" + targetPath.getFileName();

        try (InputStream in = BoreManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null; // not bundled for this platform (or the fetch task didn't run) - fall through to PATH search
            }
            Files.createDirectories(targetPath.getParent());
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            //noinspection ResultOfMethodCallIgnored
            targetPath.toFile().setExecutable(true);
            P2PConnectMod.LOGGER.info("Extracted the bundled bore binary to " + targetPath);
            return targetPath.toAbsolutePath().toString();
        } catch (Exception e) {
            P2PConnectMod.LOGGER.warn("Could not extract the bundled bore binary: " + e.getMessage());
            return null;
        }
    }
}
