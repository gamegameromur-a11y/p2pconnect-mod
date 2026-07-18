package com.p2pconnect.mod.network;

import com.p2pconnect.mod.P2PConnectMod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the "bore" (https://github.com/ekzhang/bore) binary as a subprocess
 * to expose the local Minecraft server port publicly through bore.pub. The
 * bore binary needs to be in the mod's config folder (config/p2pconnect/bore
 * or bore.exe) or on the system PATH.
 *
 * This used to also auto-extract a bore binary bundled inside the mod jar,
 * so players wouldn't have to install it manually. That was removed: a jar
 * that silently writes and executes an embedded, unsigned .exe at runtime is
 * exactly the behavior pattern antivirus heuristics flag as a "trojan
 * dropper," and it was getting the mod jar itself flagged/blocked on
 * download. A manual install is one extra step, but it means the jar is
 * just Java bytecode - nothing hidden inside it gets written to disk and run.
 *
 * Usage: bore local <localPort> --to bore.pub
 * It prints a line like: "listening at bore.pub:23456"
 */
public class BoreManager {

    private static final Pattern PORT_PATTERN = Pattern.compile("listening at [^:\\s]+:(\\d+)");
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    private Process boreProcess;
    private volatile boolean stoppedIntentionally = false;

    public boolean isRunning() {
        return boreProcess != null && boreProcess.isAlive();
    }

    /**
     * @param onPortAssigned  runs on bore's own reader thread once bore.pub assigns the external port
     * @param onError         called on startup failure (binary not found, process couldn't start)
     * @param onUnexpectedExit called (on bore's reader thread) if the process exits on its own AFTER
     *                          having started successfully - e.g. it crashed, lost its own network
     *                          connection, or was killed by something external (antivirus, OOM, etc).
     *                          Not called after a normal stop().
     */
    public void start(int localPort, Consumer<Integer> onPortAssigned, Consumer<String> onError, Runnable onUnexpectedExit) {
        stop();
        stoppedIntentionally = false;
        try {
            String exe = findBoreExecutable();
            if (exe == null) {
                onError.accept("Could not find the bore binary. Place it at config/p2pconnect/bore(.exe) "
                        + "or add it to your PATH. Download: https://github.com/ekzhang/bore/releases");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(exe, "local", String.valueOf(localPort), "--to", "bore.pub");
            pb.redirectErrorStream(true);
            boreProcess = pb.start();

            Thread readerThread = new Thread(() -> {
                boolean assigned = false;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(boreProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
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

                // The read loop above only ends when bore's stdout closes, which happens when the
                // process exits (normally or otherwise). If we didn't ask it to stop, that's a crash.
                if (assigned && !stoppedIntentionally && onUnexpectedExit != null) {
                    P2PConnectMod.LOGGER.warn("bore exited unexpectedly while hosting was active");
                    onUnexpectedExit.run();
                }
            }, "bore-output-reader");
            readerThread.setDaemon(true);
            readerThread.start();

        } catch (Exception e) {
            onError.accept("Could not start bore: " + e.getMessage());
        }
    }

    public void stop() {
        stoppedIntentionally = true;
        if (boreProcess != null && boreProcess.isAlive()) {
            boreProcess.destroy();
        }
        boreProcess = null;
    }

    private String findBoreExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        String fileName = os.contains("win") ? "bore.exe" : "bore";

        // 1) config/p2pconnect/bore(.exe)
        Path local = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("p2pconnect").resolve(fileName);
        if (Files.isExecutable(local) || Files.exists(local)) {
            return local.toAbsolutePath().toString();
        }

        // 2) system PATH - try running it directly instead of shelling out to which/where
        try {
            ProcessBuilder test = new ProcessBuilder(fileName, "--version");
            test.redirectErrorStream(true);
            Process p = test.start();
            p.waitFor();
            return fileName; // runnable via PATH
        } catch (Exception ignored) {
            return null;
        }
    }
}
