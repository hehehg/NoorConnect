# NoorConnect — Scaffold

هيكل مشروع أندرويد لعميل تليجرام مخصّص (زي CloudVeil لكن بقواعد عربية/إسلامية)، مبني بحيث كل ميزة جديدة
تضاف كـ module منفصل، ومفيش خطوة معمول عليها بنرجع فيها لاحقًا.

## فكرة العمارة (ليه اتقسّمت كده)

```
app            → نقطة التجميع بس. مفيهاش أي منطق. بتعتمد على كل الـ features.
feature:auth   → شاشات تسجيل الدخول (كومبوز) — بتعتمد على domain بس.
feature:chats  → شاشة المحادثات — بتعتمد على domain بس.
feature:moderation → *هنا الفرق الحقيقي عن تليجرام/CloudVeil*. تنفيذ ContentFilter.
domain         → Kotlin نضيف، مفيهوش Android ولا TDLib. Models + Repository interfaces + UseCases.
data           → تنفيذ الـ repositories، بيحوّل TdApi.* لموديلات domain (الوحيد اللي يعمل mapping).
core:tdlib     → الوحيد في المشروع كله اللي يعمل import لـ org.drinkless.tdlib.*
core:common    → أنواع مشتركة (AppResult, DispatcherProvider) بيستخدمها الكل.
```

**القاعدة الوحيدة اللي لازم تتاحترم**: `org.drinkless.tdlib.*` ميتكتبش غير جوه `:core:tdlib` و
`:data`. لو حسّيت إنك محتاج تستخدم TdApi type في شاشة أو ViewModel — ده معناه إنك محتاج تضيف
موديل/method جديد في `domain`، مش تكسر القاعدة.

بالطريقة دي:
- تقدر تغيّر TDLib بحاجة تانية من غير ما تلمس أي شاشة.
- تقدر تضيف قواعد فلترة جديدة (`feature:moderation`) من غير ما تلمس الشاشات ولا الـ data layer.
- تقدر تضيف feature module جديد (مثلاً `feature:azkar` أو `feature:settings`) وهو بس بياخد
  dependency على `domain`، زي أي feature تاني.

## خطوات التشغيل

1. **سجّل `api_id` و `api_hash`** من https://my.telegram.org (مجاني، بياخد دقيقتين).
   حطهم في `local.properties` (متتحطش في git):
   ```
   TELEGRAM_API_ID=123456
   TELEGRAM_API_HASH=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
   (انسخ من `local.properties.example` كنقطة بداية — ده بيتقرا أوتوماتيك في `BuildConfig` عن
   طريق `core/tdlib/build.gradle.kts`، مفيش حاجة تانية تعملها هنا.)

2. **هات TDLib نفسها.** ملحوظة مهمة اتأكدت منها: النسخ الجاهزة (prebuilt AAR) القديمة زي
   `jitpack.io/com.github.tdlib:td` واقفة عن التحديث من 2024 — يعني تصلح للتجربة السريعة بس،
   مش للنشر. الأفضل تبنيها بنفسك — وأندرويد ليه سكريبتات build مخصّصة (مش أوامر CMake العامة
   زي باقي المنصات):

   افتح `https://github.com/tdlib/td/tree/master/example/android` وعندك طريقتين:
   - **يدوي**: شغّل السكريبتات دي بالترتيب جوه فولدر `example/android`:
     ```bash
     ./check-environment.sh   # يتأكد إن كل الأدوات المطلوبة (JDK, PHP, perl, gperf...) موجودة
     ./fetch-sdk.sh           # ينزّل Android SDK محلي (لو مش عندك واحد جاهز أصلاً)
     ./build-openssl.sh       # يبني OpenSSL لأندرويد
     ./build-tdlib.sh         # يبني TDLib نفسها — ده اللي بياخد وقت
     ```
   - **Docker (رسمي من نفس المستودع، مش أداة طرف ثالث)**:
     ```bash
     cd td/example/android
     docker build --output tdlib .
     ```

   الناتج (فولدر `tdlib/` سواء بالطريقة اليدوية أو Docker):
   - `tdlib/libs/<abi>/libtdjni.so` → انقلها لـ `core/tdlib/src/main/jniLibs/<abi>/` في مشروعنا
     (الفولدرات جاهزة أصلاً وفيها README يوضح مكان كل حاجة).
   - `tdlib/java/org/drinkless/tdlib/*.java` (يعني `TdApi.java` و `Client.java`) → انقلهم لـ
     `core/tdlib/src/main/java/org/drinkless/tdlib/` (نفس الفكرة).

3. افتح المشروع في Android Studio (Koala أو أحدث)، خليه يعمل Gradle sync.
   (عندك Gradle جاهز وعايز تبني من الـ command line من غير Studio؟ اتبع `BUILD_CLI.md`.)

4. شغّله على إيموليتور أو جهاز حقيقي، وجرّب أول تسجيل دخول (رقم تليفون → كود التفعيل → باسورد
   لو عندك تحقق بخطوتين). أول ما `AuthState` يوصل `Ready`، `NoorConnectNavHost` هينقلك تلقائيًا
   لقائمة المحادثات.

## الالتزام بشروط تليجرام (مهم عشان تفضل مسموح على المتجر)

- **متستخدمش اسم "Telegram" ولا اللوجو بتاعهم.** الاسم `NoorConnect` هنا placeholder — غيّره لأي
  اسم مميز قبل النشر.
- لو استخدمت كود TDLib نفسه (مش TDLib API بس)، افتكر إن أجزاء منه ممكن تكون تحت GPL — اتأكد من
  الرخصة قبل النشر واعمل comply معاها (نشر الكود لو الرخصة بتطلب كده).
- خليك ملتزم بـ [إرشادات تليجرام للأمان](https://core.telegram.org/api) الخاصة بحماية بيانات المستخدمين.

## اللي اتعمل لحد دلوقتي

- `TdLibManager`: عميل واحد مشترك (`start()` بيتنادى مرة واحدة من `App.onCreate`)، بيتابع
  `UpdateAuthorizationState` الحقيقي ويحوّله لـ `AuthState` (رقم تليفون → كود → باسورد لو فيه
  تحقق بخطوتين → جاهز). قراءة `api_id/api_hash` بقت من `local.properties` فعليًا (مش placeholder).
- `feature:auth`: 3 خطوات (تليفون/كود/باسورد) مبنية على `AuthState` مباشرة.
- `feature:chats`: قائمة محادثات حقيقية (`LazyColumn`) متصلة بـ `GetChatsUseCase` اللي بيعدي
  على `ContentFilter`.
- `feature:chat`: شاشة محادثة كاملة (رسائل + صندوق كتابة وإرسال)، بتاخد `chatId` من الـ nav
  args مباشرة عن طريق `SavedStateHandle`.
- **`feature:moderation` بقى فعّال فعليًا**: `IslamicContentFilter` بيطبّق 3 قواعد (قنوات غير
  موثّقة، جروبات، كلمات محظورة) — دالة نقية `(Chat, ModerationSettings) -> Boolean`، مفيهاش
  state داخلي، فسهل تختبرها وتضيف عليها قواعد جديدة.
- **`feature:settings` (جديد)**: شاشة بتعدّل `ModerationSettings` (سويتشات + إضافة/حذف كلمات
  محظورة)، متخزنة في DataStore عن طريق `ModerationSettingsRepositoryImpl` في `:data`. أي تغيير
  في الإعدادات بينعكس فورًا على قائمة المحادثات لإن `GetChatsUseCase` بيعمل `combine` بين
  المحادثات والإعدادات الاثنين كـ Flow حي.
- `NoorConnectNavHost`: `auth → chats → chat/{chatId}` + `chats → settings` (زرار الترس في
  الـ top bar بتاع شاشة المحادثات).
- **إصلاح مزامنة القائمة**: `observeChats()` كان بيسمع `UpdateNewChat` بس، يعني آخر رسالة وعدد
  غير المقروء كانوا هيتجمدوا بعد أول تحميل. دلوقتي بيسمع كمان `UpdateChatLastMessage` و
  `UpdateChatReadInbox`. ترتيب القائمة حسب النشاط (`TdApi.ChatPosition`) لسه مش متعمول —
  متسجل كملحوظة صريحة في الكود، مش متجاهل بصمت.

## تصغير حجم التطبيق

224 ميجا سببها الأغلب حاجة واحدة: `libtdjni.so` بتاعتك **غير مُجرَّدة من رموز التصحيح (unstripped)**،
ومكرّرة 3 مرات (لكل معمارية: arm64-v8a + armeabi-v7a + x86_64) في نسخة واحدة من الـ APK.
النسخة غير المُجرَّدة من مكتبة C++ زي TDLib (بتضم OpenSSL وباقي المكتبات مبنيين جواها) ممكن
تكون 150-300 ميجا للمعمارية الواحدة، بينما نفس المكتبة بعد التجريد (`strip`) بتنزل غالبًا لـ
15-40 ميجا. الـ build log اللي بعتهولي فعلاً فيه إشارة للمشكلة دي:
```
Unable to strip the following libraries, packaging them as they are: ...libtdjni.so
```
يعني Gradle حاول يجرّدها تلقائيًا وفشل — غالبًا لإنها اتبنت من غير معلومات debug منظمة بالشكل
اللي أدوات AGP بتعرف تتعامل معاه.

**3 إصلاحات اتعملت في المشروع:**

1. **الآن بيتم تجميع معمارية `arm64-v8a` بس** (اللي شغال على أي جهاز حقيقي حديث تقريبًا، بما
   فيهم اللي جربت عليه) بدل التلاتة. ده لوحده هيقلل حجم المكتبات الأصلية لتقريبًا التلت.
2. **`isMinifyEnabled` و`isShrinkResources` مفعّلين** في نسخة `release` — بيقلصوا كود
   Kotlin/Java والموارد غير المستخدمة (مش هيأثر على `libtdjni.so` نفسها، بس بيقلل باقي حجم
   التطبيق). ضفت `proguard-rules.pro` بقاعدة أساسية بتحافظ على كلاسات `org.drinkless.tdlib.*`
   لإن الكود الأصلي (native) بينادي عليها بالاسم مباشرة عن طريق JNI — لو R8 غيّر الأسماء دي
   هيكسر التطبيق وقت التشغيل، مش وقت البناء.
3. **`signingConfig` بتاع الـ release مربوط مؤقتًا بمفتاح الـ debug** عشان تقدر تجرّب حجم نسخة
   الـ release دلوقتي من غير ما تحتاج تعمل keystore حقيقي — استبدلها قبل أي نشر فعلي للمستخدمين.

**الخطوة الأهم (يدوية، برا Gradle) — تجريد `libtdjni.so` بنفسك:**

لو بنيت TDLib عن طريق `fetch-sdk.sh`، عندك NDK محلي جوّا `example/android` بتاعة TDLib. جرّد
كل `.so` بأداة `llvm-strip` بتاعته:
```bash
<مسار-NDK>/toolchains/llvm/prebuilt/<host-tag>/bin/llvm-strip --strip-unneeded \
    core/tdlib/src/main/jniLibs/arm64-v8a/libtdjni.so
```
`<host-tag>` بيبقى حاجة زي `windows-x86_64` أو `linux-x86_64` حسب نظامك. جرّب الأمر وشوف حجم
الملف قبل وبعد — الفرق هيبقى كبير جدًا غالبًا.

**قياس الفرق:**
```bash
gradlew clean assembleRelease
```
الناتج في `app/build/outputs/apk/release/app-release.apk` — قارن حجمه بالـ 224 ميجا الأصليين.

## إعداد Firebase (لازم قبل ما فلترة المحادثات تشتغل فعليًا)

المشروع دلوقتي فيه ملف `app/google-services.json` **وهمي** — بيخلّي المشروع يتبني عادي، لكن أي
طلب Firestore هيفشل فعليًا لحد ما تستبدله بملفك الحقيقي. الخطوات:

1. روح على https://console.firebase.google.com وسجّل دخول بحساب Google عادي.
2. "Add project" → اديله أي اسم (مثلاً NoorConnect) → كمّل الخطوات (مش لازم Google Analytics).
3. جوه المشروع: أيقونة أندرويد ➕ "Add app" → حط `com.noorconnect.app` بالظبط كـ package name
   (لازم يتطابق مع `applicationId` في `app/build.gradle.kts`).
4. نزّل `google-services.json` اللي هيديهولك، واستبدل بيه `app/google-services.json`
   الوهمي الموجود.
5. من قائمة Firebase الجانبية: **Firestore Database** → "Create database" → اختار **Production
   mode** (مش Test mode) → اختار أقرب منطقة ليك.
6. لسه محتاج تضبط **Security Rules** (تبويب "Rules" في Firestore) — من غيرها أي حد يقدر
   يكتب/يقرأ بياناتك. القاعدة النهائية موجودة في `admin-panel/README.md` (لوحة الأدمن) —
   لازم الاثنين (التطبيق ولوحة الأدمن) يستخدموا نفس القواعد بالظبط، لإن التطبيق نفسه محتاج
   يقدر يكتب `status = pending` بس (يعني "يبلّغ")، ولوحة الأدمن هي الوحيدة اللي تقدر تحوّلها
   لـ whitelist أو blacklist. لو حطيت `allow write: if false` عادي كده، هتكسر خاصية "الإبلاغ
   التلقائي" اللي التطبيق بيعملها لما يلاقي كلمة محظورة أو محادثة جديدة.

## هيكل بيانات Firebase (لوحة الأدمن، لما تتعمل، هتقرأ/تكتب على نفس الشكل ده)

```
channels/{chatId}          — مستند واحد لكل محادثة، chatId هو الـ document id
  status:   "whitelist" | "blacklist" | "pending"   (مفيش status خالص = لسه ما اتراجعتش)
  reason:   نص، اختياري — بيتعرض للمستخدم لو محظورة أو تحت المراجعة
  audience: "male" | "female" | "both"               (مفيش audience = "both")

moderation_config/banned_words
  words: array<string>
```

## آلية الفلترة اللي اشتغلت

- **قبل ما أي محادثة تتفتح**: `CheckChatAccessUseCase` بيقرا الـ `status` و`audience` بتاعتها
  من Firestore. لو `whitelist` ومناسبة لتصنيف المستخدم (من إجابة onboarding) → تتفتح عادي. لو
  `blacklist` أو `pending` أو مفيش سجل خالص → المحادثة بتتقفل برسالة توضيحية، وأي محادثة
  "مفيش سجل ليها" بتتبعت أوتوماتيك لقائمة المراجعة (`status = pending`).
- **فحص الكلمات المحظورة**: شغّال مستمر على أي رسائل بتوصل (مش بس أول ما تفتح المحادثة) —
  لو ظهرت كلمة من `moderation_config/banned_words` في أي رسالة، المحادثة بتتقفل فورًا
  وبيتحط `status = pending` تلقائيًا (حتى لو كانت `whitelist` قبل كده — بيتشالها منها لحد
  ما الأدمن يراجعها من جديد).

## الخطوة الجاية

المشروع بقى قابل للتشغيل من ناحية الكود بالكامل: تسجيل دخول → قائمة محادثات مفلترة → فتح
محادثة وإرسال (بأسماء المرسلين) → تعديل قواعد الفلترة → فلترة عن طريق Firebase
(whitelist/blacklist/كلمات محظورة) → تقسيم المحادثات لمجلدات (زي تليجرام: إنشاء/حذف/إعادة
تسمية مجلد، وإضافة/إزالة أي محادثة من أي مجلد بزرار المجلد جنب كل محادثة). لوحة الأدمن اللي
بتدير الفلترة موجودة في مجلد `admin-panel/` المنفصل — شوف الـ README بتاعها لخطوات التشغيل.

المجلدات محلية على الجهاز بس دلوقتي (مش متزامنة مع أي سيرفر) — لو حبيت تتزامن بين أجهزة
المستخدم، `FolderRepositoryImpl` هو الملف الوحيد اللي يحتاج يتغيّر (الواجهة `FolderRepository`
هتفضل زي ما هي).
