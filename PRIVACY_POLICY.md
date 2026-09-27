# Seslendirmen Gizlilik Politikası / Privacy Policy

**Yürürlük ve son güncelleme tarihi / Effective and last updated:** 27 Eylül 2026 / 27 September 2026

Bu politika, M. Utku Ensert ("geliştirici") tarafından sunulan Seslendirmen Android uygulamasını
("uygulama") kapsar. Sorularınız için `ensertyazilim@gmail.com` adresinden iletişime geçebilirsiniz.

This policy applies to the Seslendirmen Android application (the "app") provided by M. Utku Ensert
(the "developer"). For questions, contact `ensertyazilim@gmail.com`.

## Türkçe

### Kısa özet

Seslendirmen, kullanıcının seçtiği PDF ve EPUB belgelerini cihaz üzerinde işleyen çevrimdışı bir
belge okuyucudur. Uygulama internet izni istemez; hesap, geliştirici sunucusu, reklam, kullanıcı
izleme, Firebase Analytics veya Crashlytics içermez. Belgelerin içeriği, OCR ile tanınan metin ve
üretilen konuşma geliştiriciye ya da Google'a gönderilmez. Bununla birlikte, aşağıda açıklandığı
üzere uygulamada kullanılan Google ML Kit SDK'sı sınırlı teknik ölçümleri Google'a iletebilir.

### Uygulamanın eriştiği ve cihazda işlediği veriler

- **Kullanıcının seçtiği belgeler:** Uygulama yalnızca Android'in sistem belge seçicisi üzerinden
  açıkça seçtiğiniz PDF veya EPUB dosyasını okur. Geniş kapsamlı depolama izni istemez. Belge
  içeriği; metin çıkarma, taranmış PDF sayfalarında OCR ve ses üretimi amacıyla cihazda işlenir.
- **Belge bilgileri ve okuma konumu:** Son seçilen belgenin dosya adı ile son okunan bölümün kimliği
  ve toplam bölüm sayısı, kaldığınız yere dönebilmeniz için uygulamanın özel yerel tercihlerinde
  saklanır. Uygulama ayrıca seçilen belgeyi daha sonra tekrar okuyabilmek için Android belge
  sağlayıcısından kalıcı salt-okuma erişimi isteyebilir.
- **TTS tercihi:** Seçtiğiniz konuşma üretim kalite/adım değeri cihazda saklanır.
- **Geçici veriler:** Çıkarılmış metin, OCR için oluşturulan sayfa görüntüleri ve üretilen ses,
  özelliği çalıştırmak için cihazda geçici olarak işlenir; uygulama bunları geliştirici sunucusuna
  yüklemez veya ayrı kullanıcı dosyaları olarak kalıcı biçimde kaydetmez.
- **Oynatma bildirimi:** Belgenin dosya adı, oynatma denetimlerini sunan özel görünürlüklü medya
  bildiriminde gösterilebilir. Bildirimin kilit ekranında nasıl gösterileceğini Android bildirim
  gizliliği ayarlarınız belirler.
- **Teknik günlükler:** Uygulama; süre, sayfa sayısı ve hata bilgisi gibi sınırlı teknik kayıtları
  Android sistem günlüğüne yazabilir. Belge URI'si, dosya adı ve belge metni bilerek günlüğe
  yazılmaz. Uygulama bu günlükleri geliştiriciye otomatik olarak göndermez.

Uygulama tarafından yönetilen yerel veriler için Android yedekleme özelliği devre dışı
bırakılmıştır.

### Üçüncü taraf işlemesi: Google ML Kit

Taranmış PDF sayfalarındaki metni tanımak için paket içinde gelen Google ML Kit Text Recognition
SDK'sı kullanılır. Google'ın açıklamasına göre giriş görüntüleri, metin ve sonuçlar tamamen cihazda
işlenir ve Google sunucularına gönderilmez. Google ayrıca ML Kit'in teşhis ve kullanım analizi için
cihaz ve uygulama bilgisi, kurulum başına tanımlayıcı, performans ölçümleri, API yapılandırması,
girdi/çıktı boyutu, özellik sürümü, olay türü ve hata kodları gibi teknik verileri toplayabileceğini;
bu verilerin aktarım sırasında HTTPS ile şifrelendiğini ve üçüncü taraflarla paylaşılmadığını
belirtir. Geliştirici bu teknik ölçümleri almaz ve bunları reklam amacıyla kullanmaz.

Google'ın güncel açıklamaları:

- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/android-data-disclosure
- https://policies.google.com/privacy

sherpa-onnx/Supertonic 3 konuşma üretimi, PDFBox metin çıkarımı ve uygulamanın diğer belge işleme
bileşenleri cihaz üzerinde çalışır. Uygulamada reklam SDK'sı bulunmaz.

### Paylaşım, satış ve amaçlar

Geliştirici belgelerinizi, çıkarılan metni, üretilen sesi veya yerel tercihlerinizi toplamaz,
satmaz ya da üçüncü taraflarla paylaşmaz. Uygulama bu verileri yalnızca istediğiniz belge okuma,
OCR, konuşma üretimi, oynatma ve okuma konumunu geri yükleme işlevlerini sunmak için cihazda
kullanır. ML Kit'in bağımsız teknik veri işlemesi bir önceki bölümde açıklanmıştır.

### Saklama ve silme

Belgenin kendisi seçtiğiniz depolama konumunda kalır ve uygulama tarafından silinmez. Dosya adı,
okuma konumu, TTS tercihi ve kalıcı okuma izni; siz Android ayarlarından uygulama verilerini
temizleyene veya uygulamayı kaldırana kadar cihazda kalabilir. Uygulama verilerini temizlemek ya da
uygulamayı kaldırmak uygulamanın yönettiği yerel verileri siler ve belge erişimini kaldırır. Hesap
ve geliştirici sunucusunda saklanan kullanıcı verisi bulunmadığından ayrıca sunucu taraflı silme
talebi gerekmez. Google tarafından işlenen ML Kit teknik verileri için Google'ın gizlilik politikası
ve saklama uygulamaları geçerlidir.

### Çocukların gizliliği

Uygulama hiçbir kullanıcıdan bilerek kişisel veri toplamaz ve çocuklara yönelik davranışsal reklam
sunmaz. Bir çocuk uygulamayı kullanırsa belge içeriği yine cihaz üzerinde işlenir; ML Kit'in teknik
veri işlemesi yukarıdaki koşullara tabidir.

### Değişiklikler ve iletişim

Uygulamanın veri uygulamaları değişirse bu politika ve Google Play Veri Güvenliği beyanı sürüm
yayımlanmadan önce güncellenecektir. Önemli değişiklikler güncellenen tarih ile belirtilecektir.
Gizlilik soruları ve hak talepleri için: `ensertyazilim@gmail.com`.

## English

### Summary

Seslendirmen is an offline document reader that processes user-selected PDF and EPUB documents on
the device. The app does not request Internet permission and has no account system, developer
server, advertising, user tracking, Firebase Analytics, or Crashlytics. Document contents,
OCR-recognized text, and generated speech are not sent to the developer or Google. However, the
Google ML Kit SDK used by the app may transmit limited technical metrics to Google as described
below.

### Data accessed and processed on the device

- **Documents you select:** The app reads only a PDF or EPUB file that you explicitly select with
  Android's system document picker. It does not request broad storage access. Document content is
  processed on the device for text extraction, OCR of scanned PDF pages, and speech generation.
- **Document information and reading position:** The last document's file name, last-read chunk ID,
  and total chunk count are stored in private local preferences so the app can restore your place.
  The app may also ask the Android document provider to retain read-only access to the selected
  document so it can be opened again.
- **TTS preference:** Your selected speech-generation quality/step value is stored on the device.
- **Temporary data:** Extracted text, rendered page images used for OCR, and generated audio are
  processed temporarily on the device. They are not uploaded to a developer server or saved by the
  app as separate permanent user files.
- **Playback notification:** The document file name may appear in a private-visibility media
  notification that provides playback controls. Android notification privacy settings determine
  how it appears on the lock screen.
- **Technical logs:** The app may write limited technical information such as timings, page counts,
  and errors to the Android system log. Document URIs, file names, and document text are not
  intentionally logged. The app does not automatically transmit these logs to the developer.

Android backup is disabled for app-managed local data.

### Third-party processing: Google ML Kit

The app uses the bundled Google ML Kit Text Recognition SDK to recognize text on scanned PDF pages.
Google states that input images, text, and results are processed entirely on-device and are not sent
to Google servers. Google also states that ML Kit may collect technical data for diagnostics and
usage analytics, including device and app information, a per-installation identifier, performance
metrics, API configuration, input/output sizes, feature version, event types, and error codes. Google
states that this data is encrypted in transit with HTTPS and is not shared with third parties. The
developer does not receive these metrics or use them for advertising.

Google's current disclosures:

- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/android-data-disclosure
- https://policies.google.com/privacy

sherpa-onnx/Supertonic 3 speech generation, PDFBox extraction, and the app's other document-processing
components run on the device. The app contains no advertising SDK.

### Sharing, sale, and purposes

The developer does not collect, sell, or share your documents, extracted text, generated speech, or
local preferences. The app uses this data on the device only to provide the document-reading, OCR,
speech-generation, playback, and reading-position restoration features you request. ML Kit's
separate technical data processing is disclosed above.

### Retention and deletion

The document itself remains in the storage location you selected and is not deleted by the app.
The file name, reading position, TTS preference, and retained read permission may remain on the
device until you clear the app's data in Android settings or uninstall the app. Either action removes
app-managed local data and document access. Because there is no account or user data stored on a
developer server, no separate server-side deletion request is required. Google's privacy policy and
retention practices apply to technical ML Kit data processed by Google.

### Children's privacy

The app does not knowingly collect personal data from any user and does not serve behaviorally
targeted advertising to children. If a child uses the app, document content is still processed on
the device; ML Kit technical data remains subject to the terms described above.

### Changes and contact

If the app's data practices change, this policy and the Google Play Data safety declaration will be
updated before the changed version is released. Material changes will be identified by an updated
date. For privacy questions and rights requests, contact `ensertyazilim@gmail.com`.
