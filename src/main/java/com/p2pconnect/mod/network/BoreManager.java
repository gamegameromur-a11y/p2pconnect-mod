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
 * "bore" (https://github.com/ekzhang/bore) binary'sini alt process olarak
 * çalıştırıp local Minecraft server portunu bore.pub üzerinden herkese açık
 * hale getirir. Bore binary'sinin mod klasöründe (config/p2pconnect/bore
 * veya bore.exe) ya da sistem PATH'inde bulunması gerekir.
 *
 * Kullanım: bore local <localPort> --to bore.pub
 * Çıktısında şuna benzer bir satır basar: "listening at bore.pub:23456"
 */
public class BoreManager {

    // "listening at bore.pub:23456" ya da kendi barındırdığın bir bore server ile
    // "listening at 1.2.3.4:23456" satırından portu çeker. Host kısmını "bore.pub"
    // ile sınırlamıyoruz ki kullanıcı kendi bore server'ını da kullanabilsin.
    private static final Pattern PORT_PATTERN = Pattern.compile("listening at [^:\\s]+:(\\d+)");
    // bore'un ANSI renk kodlarını (ör. "\u001B[32m") satırlardan temizlemek için.
    // GERÇEK TEST SIRASINDA BULUNDU: bore, çıktısı bir dosyaya/pipe'a yönlendirilmiş
    // olsa bile renk kodlarını basmaya devam ediyor; bunları temizlemeden regex hiç eşleşmiyordu.
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    private Process boreProcess;

    public boolean isRunning() {
        return boreProcess != null && boreProcess.isAlive();
    }

    /**
     * @param localPort      dışarı açılacak local Minecraft server portu (varsayılan 25565)
     * @param onPortAssigned bore.pub tarafından atanan dış port geldiğinde çağrılır (Minecraft ana thread'inde DEĞİL - dikkat)
     * @param onError        bir hata olursa (binary bulunamadı, process çöktü vs.) çağrılır
     */
    public void start(int localPort, Consumer<Integer> onPortAssigned, Consumer<String> onError) {
        stop();
        try {
            String exe = findBoreExecutable();
            if (exe == null) {
                onError.accept("bore binary bulunamadı. config/p2pconnect/bore(.exe) dosyasına koy ya da PATH'e ekle. "
                        + "İndirme adresi: https://github.com/ekzhang/bore/releases");
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
                    P2PConnectMod.LOGGER.warn("bore çıktısı okunurken hata: " + e.getMessage());
                }
            }, "bore-output-reader");
            readerThread.setDaemon(true);
            readerThread.start();

        } catch (Exception e) {
            onError.accept("bore başlatılamadı: " + e.getMessage());
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

        // 1) config/p2pconnect/bore(.exe)
        Path local = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("p2pconnect").resolve(fileName);
        if (Files.isExecutable(local) || Files.exists(local)) {
            return local.toAbsolutePath().toString();
        }

        // 2) sistem PATH'i - "which/where" yerine doğrudan process ile dene
        try {
            ProcessBuilder test = new ProcessBuilder(fileName, "--version");
            test.redirectErrorStream(true);
            Process p = test.start();
            p.waitFor();
            return fileName; // PATH üzerinden çalıştırılabilir
        } catch (Exception ignored) {
            return null;
        }
    }
}
