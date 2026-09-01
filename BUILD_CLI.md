# بناء المشروع من الـ command line (من غير Android Studio)

Android Studio في الآخر مجرد واجهة فوق Gradle — لو عندك Gradle مركّب أصلاً، تقدر تبني وتشغّل
المشروع بالكامل من الـ terminal. اللي محتاجه فعليًا مش "Android Studio" بل 3 حاجات: JDK، Android
SDK command-line tools، وGradle نفسه (اللي عندك جاهز).

## 1. تجهيزات لازمة قبل أول build

- **JDK 17** — تأكد `java -version` بيرجّع 17.
- **Android SDK command-line tools** (من غير Studio):
  نزّلها من https://developer.android.com/studio#command-tools ، فُكّها في أي مكان، مثلاً
  `~/android-sdk/cmdline-tools/latest/`.
  (لو هتشغّل `example/android/fetch-sdk.sh` بتاع TDLib أصلاً، هو بينزّل SDK محلي منفصل لبناء
  TDLib نفسها بس — ده غير الـ SDK اللي محتاجه تطبيقك في الخطوة دي. ينفع تستخدم نفس الـ SDK
  للاثنين لو عايز، بس مش شرط.)
- **متغيرات البيئة**:
  ```bash
  export ANDROID_HOME=~/android-sdk
  export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
  ```
- **نزّل الحزم المطلوبة** عن طريق `sdkmanager` (بديل Android Studio's SDK Manager بالظبط):
  ```bash
  sdkmanager --licenses          # وافق على كل التراخيص
  sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
  ```

## 2. خلي `local.properties` يعرف مكان الـ SDK

الملف نفسه اللي فيه `TELEGRAM_API_ID/HASH` (خطوة سابقة) — لازم يبقى فيه كمان:
```
sdk.dir=/absolute/path/to/android-sdk
```
Android Studio بيحطها أوتوماتيك، لكن من الـ CLI لازم تحطها انت بنفسك، وإلا الـ build هيفشل
على طول برسالة `SDK location not found`.

## 3. جهّز الـ Gradle wrapper (مرة واحدة بس)

المشروع من غير `gradlew` بشكل افتراضي — وده مقصود، عشان الملف الثنائي `gradle-wrapper.jar`
ميتحطش بشكل أعمى من مصدر مش متأكد منه. بما إن عندك Gradle جاهز، ولّده بنفسك:
```bash
cd noorconnect
gradle wrapper --gradle-version 8.9
```
ده هيعمل `gradlew` و `gradlew.bat` و `gradle/wrapper/gradle-wrapper.jar` مبنيين على نسخة
Gradle اللي طلبتها، ومتأكدين إنهم جايين من مصدرك انت.

(لو عايز تستخدم الـ `gradle` اللي عندك مباشرة من غير wrapper خالص، من غير مشكلة — كل الأوامر
تحت تشتغل بـ `gradle` عادي بدل `./gradlew`.)

## 4. الأوامر الأساسية

```bash
# ابني الـ APK (debug — بدون توقيع نهائي، للتجربة على جهازك)
./gradlew assembleDebug

# أو لو عايز تشغّل على جهاز/إيموليتور متصل مباشرة (build + install + launch)
./gradlew installDebug

# شغّل الأوامر دي بعد ما تحط ملفات TDLib (libtdjni.so + TdApi.java/Client.java)
# في مكانهم زي ما موضح في README.md — وإلا الـ build هيفشل على compile error
# في core:tdlib (import مفقود لـ org.drinkless.tdlib.*).
```

الناتج هيبقى في: `app/build/outputs/apk/debug/app-debug.apk`

## 5. تثبيت وتشغيل على جهاز/إيموليتور من CLI

```bash
adb devices                              # تأكد إن الجهاز/الإيموليتور شايفه
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.noorconnect.app/.MainActivity
```

## 6. لو مش عندك إيموليتور Studio ولا هتحمّله

تقدر تشغّل إيموليتور من CLI برضو من غير فتح Studio:
```bash
sdkmanager "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n noorconnect_test -k "system-images;android-34;google_apis;x86_64"
emulator -avd noorconnect_test
```

## مشاكل شائعة

- **`SDK location not found`** → `sdk.dir` في `local.properties` غلط أو ناقص.
- **compile error على `org.drinkless.tdlib`** → لسه محطتش `TdApi.java`/`Client.java` في
  `core/tdlib/src/main/java/org/drinkless/tdlib/`.
- **`UnsatisfiedLinkError` وقت التشغيل (مش وقت الـ build)** → `libtdjni.so` مش موجود للمعمارية
  بتاعة الجهاز/الإيموليتور اللي بتشغّل عليه (تأكد إن `arm64-v8a` أو `x86_64` موجود حسب الجهاز).
