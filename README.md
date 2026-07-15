# P2P Connect (Forge 1.20.1)

Bore ile iki oyuncuyu birbirine bağlayan, kullanıcı adı tabanlı davet sistemine
sahip bir Minecraft Forge modu. **Hiçbir sunucu senin (yayıncının) barındırmasına
gerek yok** — tünelleme için herkese açık `bore.pub`, eşleştirme/istek sistemi
için herkese açık bir MQTT broker'ı (`broker.hivemq.com`) kullanılıyor.

## ⚙️ CI ile Otomatik Build

Bu repoya `.github/workflows/build.yml` eklendi. `main` dalına her push'ta
(veya Actions sekmesinden manuel "Run workflow" ile) GitHub'ın kendi
runner'ları üzerinde gerçek bir `gradle build` çalıştırılır — bu runner'ların
Forge Maven'a serbest ağ erişimi olduğu için lokal bir sandbox'ta çalışamayan
bu build orada gerçekten tamamlanabilir.

**Derlenen jar'ı almak için:** GitHub'da bu reponun **Actions** sekmesine gir,
en son "Build P2P Connect Mod" çalışmasına tıkla, alt kısımdaki
**Artifacts** bölümünden `p2pconnect-jar` dosyasını indir. Build başarısız
olursa aynı yerden `build-logs` artifact'ini indirip hatayı görebilirsin
(bkz. aşağıdaki "Bilinen Risk Noktaları" — muhtemel hatalar zaten burada
belgelendi).

Not: Bu repoda `gradlew`/`gradlew.bat`/`gradle/wrapper` yok (ForgeGradle MDK'sinin
binary dosyaları, bkz. "Kurulum Adımları" md.2) bu yüzden workflow `gradle`
komutunu (Gradle Actions'ın kurduğu sürüm) doğrudan kullanıyor. Wrapper'ı
kendin eklersen `Build with Gradle` adımını `./gradlew build` olarak
değiştirebilirsin.

## ⚠️ Önemli — Yerel Ortamda Test Edilmedi

Bu kod, Forge Maven repository'lerine ağ erişimi olmayan bir sandbox ortamında
yazıldı, yani `gradlew build` hiç yerel olarak çalıştırılmadı. Mantık ve API
kullanımı doğru olacak şekilde yazıldı ama birkaç yerde Minecraft/Forge sürümüne
göre değişebilecek metod imzaları var (yukarıdaki CI, bunları derleme
hatası olarak gösterecek). İlk derlemede hata alırsan aşağıdaki
"Bilinen Risk Noktaları" bölümüne bak.

## Kurulum Adımları (yerelde geliştirmek istersen)

1. **JDK 17** kur (Forge 1.20.1 bunu gerektirir).
2. **Forge 1.20.1 MDK'yı indir**: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
   (47.3.0 sürümünü seç, "Mdk" linkine tıkla) ve bir klasöre çıkart. Bu sana
   `gradlew`, `gradlew.bat` ve `gradle/` klasörünü (wrapper) verecek — bunlar
   binary dosyalar olduğu için repo içinde yok, MDK'dan almalısın.
3. MDK'nın içindeki `src/`, `build.gradle`, `settings.gradle`, `gradle.properties`
   dosyalarını **sil** ve onların yerine bu projedeki aynı isimli dosya/klasörleri
   kopyala (gradlew, gradlew.bat, gradle/ klasörüne dokunma).
4. **bore binary'sini indir**: https://github.com/ekzhang/bore/releases
   (işletim sistemine göre `bore` ya da `bore.exe`), `config/p2pconnect/bore(.exe)`
   yoluna koy (mod ilk çalıştığında bu klasör oluşur, sen manuel oluşturabilirsin)
   ya da sistem PATH'ine ekle.
5. Terminalde proje klasöründe:
   ```
   ./gradlew build
   ```
   (Windows'ta `gradlew.bat build`)
6. Oluşan jar: `build/libs/p2pconnect-1.0.0-all.jar` (shadow jar, OkHttp/MQTT
   içine gömülü olan) — bunu `.minecraft/mods/` klasörüne koy.
7. Test için: `./gradlew runClient` iki farklı klasörde iki instance açarak
   (ya da iki farklı bilgisayarda) deneyebilirsin.

## Nasıl Çalışır?

- **Multiplayer ekranında** sağ üstte "P2P Connect" butonu çıkar.
- İlk kullanımda kullanıcı adı sorulur (yerel olarak `config/p2pconnect.properties`'de saklanır).
- **Sunucu Aç**: Zaten içinde bulunduğun bir dünyayı LAN'a açar, bore ile dışarı
  tünelleyip bir ID (`bore.pub:PORT`) verir. Bunu Discord/WhatsApp vs. ile
  manuel paylaşabilirsin.
- **Arkadaşa Bağlan**:
  - *Kullanıcı adıyla istek gönder*: Karşı taraf online ve mod menüsünü
    kullanıyorsa (MQTT bağlantısı kurulmuşsa) bir istek düşer, "Kabul Et"
    derse otomatik olarak dünya oluşturma ekranına yönlendirilir, dünyayı
    oluşturup oyuna girdiği an arka planda hosting başlar ve sana bağlantı
    bilgisi gönderilir, otomatik bağlanırsın.
  - *ID ile doğrudan bağlan*: Host'un sana manuel verdiği `bore.pub:PORT`
    adresini yazıp direkt bağlanırsın.
- **Mod karşılaştırması**: Bağlanmadan hemen önce iki tarafın mod listesi
  karşılaştırılır, uyuşmazlık varsa uyarı gösterilip onay istenir.

## Bilinen Risk Noktaları (derlerken kontrol et)

- `HostingUtil.startHostingCurrentWorld` içindeki `server.publishServer(...)`
  çağrısının parametreleri — vanilla `OpenToLanScreen` sınıfına bak (IDE'de
  decompile edilmiş kaynağı görebilirsin).
- `ConnectUtil.connect` içindeki `ConnectScreen.startConnecting(...)` çağrısının
  parametre sayısı/sırası.
- `CreateWorldScreen.openFresh(...)` metod adı/imzası.

Bunların hepsi IDE'de (IntelliJ + ForgeGradle) "Go to Definition" ile 2 dakikada
doğrulanabilir/düzeltilebilir. CI'da bir derleme hatası olursa, hata mesajındaki
satır/sınıf bilgisi büyük ihtimalle yukarıdaki üç noktadan birine işaret edecektir.

## Gizlilik / Güvenilirlik Notu

- `broker.hivemq.com` ve `bore.pub` üçüncü parti, herkese açık, ücretsiz
  servislerdir — kayıt/hesap gerektirmezler ama SLA garantisi de yoktur ve
  trafik teorik olarak halka açıktır (hassas veri geçmiyor, sadece
  kullanıcı adı + IP:port).
- Kullanıcı isterse `config/p2pconnect.properties` içindeki `mqttBrokerUrl`
  değerini kendi MQTT broker'ına (örn. ücretsiz HiveMQ Cloud hesabı) çevirebilir.
