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
                onError.accept("Could not find the bore binary. Place it at config/p2pconnect/bore(.exe) "
                        + "or add it to your PATH. Download: https://github.com/ekzhang/bore/releases");
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

        Path local = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("p2pconnect").resolve(fileName);
        if (Files.isExecutable(local) || Files.exists(local)) {
            return local.toAbsolutePath().toString();
        }

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
}
