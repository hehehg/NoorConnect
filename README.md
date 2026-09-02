# NoorConnect

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](#)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![Backend](https://img.shields.io/badge/backend-TDLib-26A5E4?logo=telegram&logoColor=white)](#)
[![Data](https://img.shields.io/badge/data-Firebase-FFCA28?logo=firebase&logoColor=black)](#)
[![License](https://img.shields.io/badge/license-see%20LICENSE-lightgrey)](#)
[![Security Policy](https://img.shields.io/badge/security-SECURITY.md-critical)](./SECURITY.md)

> عميل أندرويد مخصّص لتليجرام (مبني على TDLib) بقواعد فلترة محتوى قابلة للتخصيص على مستوى القنوات والجروبات والكلمات، مُدارة عن بُعد عبر Firebase.

---

## نظرة عامة على العمارة

المشروع مقسّم إلى وحدات (modules) بحيث تُضاف كل ميزة جديدة كطبقة مستقلة، دون الحاجة لتعديل ما سبق بناؤه:

| الوحدة | المسؤولية |
|---|---|
| `app` | نقطة التجميع فقط — لا تحتوي منطقًا، وتعتمد على جميع الوحدات الأخرى |
| `feature:auth` | شاشات تسجيل الدخول (Compose) — تعتمد على `domain` فقط |
| `feature:chats` | شاشة قائمة المحادثات — تعتمد على `domain` فقط |
| `feature:chat` | شاشة المحادثة الفردية (رسائل + إرسال) |
| `feature:moderation` | تنفيذ `ContentFilter` — الميزة المميِّزة للمشروع |
| `feature:settings` | تعديل إعدادات الفلترة، مخزَّنة عبر DataStore |
| `domain` | Kotlin نقي — نماذج البيانات وواجهات المستودعات وحالات الاستخدام، بلا أي اعتماد على Android أو TDLib |
| `data` | تنفيذ المستودعات، ويحوّل أنواع `TdApi.*` إلى نماذج `domain` |
| `core:tdlib` | الوحدة الوحيدة المصرَّح لها باستيراد `org.drinkless.tdlib.*` |
| `core:common` | أنواع مشتركة (`AppResult`, `DispatcherProvider`) تُستخدم في كل مكان |

**القاعدة المعمارية الملزمة:** لا يُكتب `org.drinkless.tdlib.*` إلا داخل `:core:tdlib` و `:data`. أي حاجة لاستخدام نوع من TDLib داخل شاشة أو ViewModel تعني أن الحل هو إضافة نموذج/دالة جديدة في `domain`، وليس كسر هذه القاعدة.

بهذا التصميم:
- يمكن استبدال TDLib بمكتبة أخرى دون المساس بأي شاشة.
- يمكن إضافة قواعد فلترة جديدة عبر `feature:moderation` دون لمس طبقتي العرض والبيانات.
- يمكن إضافة وحدة ميزة جديدة (مثل `feature:azkar`) بالاعتماد على `domain` فقط، أسوة بباقي الميزات.

---

## البدء السريع

### 1. بيانات اعتماد Telegram API
سجّل `api_id` و `api_hash` من [my.telegram.org](https://my.telegram.org) (مجاني)، وضعهما في `local.properties` (غير مُتتبَّع في git):

```properties
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

انسخ من `local.properties.example` كنقطة بداية؛ يُقرأ الملف تلقائيًا داخل `BuildConfig` عبر `core/tdlib/build.gradle.kts`.

### 2. بناء TDLib
> ⚠️ الحزم الجاهزة القديمة (مثل `jitpack.io/com.github.tdlib:td`) متوقفة عن التحديث منذ 2024 — صالحة للتجربة السريعة فقط، **غير مناسبة للنشر**.

من [`tdlib/td` — دليل أندرويد](https://github.com/tdlib/td/tree/master/example/android)، اختر إحدى الطريقتين:

**يدويًا** (داخل `example/android`):
```bash
./check-environment.sh   # يتحقق من الأدوات المطلوبة (JDK, PHP, perl, gperf...)
./fetch-sdk.sh           # ينزّل Android SDK محليًا عند الحاجة
./build-openssl.sh       # يبني OpenSSL لأندرويد
./build-tdlib.sh         # يبني TDLib (الخطوة الأطول زمنيًا)
```

**عبر Docker** (رسمي من نفس المستودع):
```bash
cd td/example/android
docker build --output tdlib .
```

انقل ناتج البناء إلى المشروع:
- `tdlib/libs/<abi>/libtdjni.so` → `core/tdlib/src/main/jniLibs/<abi>/`
- `tdlib/java/org/drinkless/tdlib/*.java` → `core/tdlib/src/main/java/org/drinkless/tdlib/`

### 3. فتح المشروع
افتح المشروع في Android Studio (Koala أو أحدث) واسمح بمزامنة Gradle.
للبناء من سطر الأوامر دون Studio، راجع `BUILD_CLI.md`.

### 4. التشغيل
شغّل التطبيق على محاكي أو جهاز حقيقي وسجّل الدخول (رقم الهاتف ← كود التفعيل ← كلمة مرور التحقق بخطوتين إن وُجدت). عند وصول `AuthState` إلى `Ready`، ينتقل `NoorConnectNavHost` تلقائيًا إلى قائمة المحادثات.

---

## إعداد Firebase

المشروع يتضمن `app/google-services.json` **وهميًا** يسمح بالبناء، لكن طلبات Firestore لن تعمل فعليًا حتى استبداله بملف حقيقي:

1. سجّل الدخول إلى [Firebase Console](https://console.firebase.google.com).
2. أنشئ مشروعًا جديدًا (Google Analytics غير مطلوب).
3. من أيقونة أندرويد ➕ Add app، أدخل `com.noorconnect.app` كـ package name (يجب مطابقته لـ `applicationId` في `app/build.gradle.kts`).
4. نزّل `google-services.json` واستبدل به النسخة الوهمية.
5. من القائمة الجانبية: **Firestore Database** ← Create database ← اختر **Production mode** (وليس Test mode) ← اختر أقرب منطقة.
6. اضبط **Security Rules** — التفاصيل والقاعدة النهائية في [`SECURITY.md`](./SECURITY.md#firestore-security-rules).

### هيكل بيانات Firestore

```
channels/{chatId}
  status:   "whitelist" | "blacklist" | "pending"   # لا وجود لحقل = لم تتم مراجعتها بعد
  reason:   string, اختياري — يُعرض للمستخدم عند الحظر أو المراجعة
  audience: "male" | "female" | "both"               # لا وجود لحقل = "both"

moderation_config/banned_words
  words: array<string>
```

لوحة الإدارة التي تدير هذه البيانات موجودة في `admin-panel/` — راجع الـ README الخاص بها لخطوات التشغيل، ويجب أن تشترك مع التطبيق في نفس قواعد الأمان تمامًا (تفاصيل ذلك في [`SECURITY.md`](./SECURITY.md)).

---

## آلية الفلترة

- **قبل فتح أي محادثة**: يقرأ `CheckChatAccessUseCase` حقلي `status` و`audience` من Firestore. إن كانت `whitelist` ومناسبة لفئة المستخدم (من إجابات onboarding) تُفتح المحادثة، وإلا (`blacklist` أو `pending` أو لا سجل) تُقفل مع رسالة توضيحية، وتُرسل تلقائيًا لقائمة المراجعة (`status = pending`) إن لم يكن لها سجل أصلًا.
- **فحص الكلمات المحظورة**: مستمر على كل الرسائل الواردة، وليس فقط عند فتح المحادثة. عند ظهور كلمة من `moderation_config/banned_words`، تُقفل المحادثة فورًا ويُحدَّث `status` إلى `pending` تلقائيًا (حتى لو كانت `whitelist` سابقًا).

---

## الحالة الحالية للمشروع

| المكوّن | الحالة |
|---|---|
| `TdLibManager` | ✅ عميل مشترك واحد، يتابع `UpdateAuthorizationState` الحقيقية ويحوّلها إلى `AuthState`. القراءة من `local.properties` فعلية |
| `feature:auth` | ✅ 3 خطوات (هاتف/كود/كلمة مرور) مبنية على `AuthState` |
| `feature:chats` | ✅ قائمة حقيقية (`LazyColumn`) متصلة بـ `GetChatsUseCase` عبر `ContentFilter` |
| `feature:chat` | ✅ شاشة محادثة كاملة، `chatId` عبر `SavedStateHandle` |
| `feature:moderation` | ✅ `IslamicContentFilter` — 3 قواعد (قنوات غير موثّقة، جروبات، كلمات محظورة)، دالة نقية بلا حالة داخلية |
| `feature:settings` | ✅ تعديل `ModerationSettings` عبر DataStore، ينعكس فوريًا على القائمة (Flow حي عبر `combine`) |
| مزامنة قائمة المحادثات | ✅ يستمع الآن لـ `UpdateChatLastMessage` و`UpdateChatReadInbox` إضافة إلى `UpdateNewChat` |
| ترتيب القائمة حسب النشاط (`TdApi.ChatPosition`) | ⏳ غير منفَّذ بعد — موثّق صراحة في الكود |
| مجلدات المحادثات | ✅ محلية على الجهاز فقط (إنشاء/حذف/تسمية/تعيين محادثة) — غير متزامنة بين الأجهزة؛ عند الحاجة، `FolderRepositoryImpl` هو الملف الوحيد المطلوب تعديله |

**الخطوة التالية**: المشروع قابل للتشغيل الكامل من ناحية الكود (تسجيل دخول → قائمة مفلترة → محادثة كاملة → إعدادات فلترة → فلترة عبر Firebase → مجلدات محلية). لوحة الإدارة منفصلة في `admin-panel/`.

---

## قبل النشر

راجع [`SECURITY.md`](./SECURITY.md) قبل أي نشر فعلي — يغطي الالتزام بشروط تليجرام، ترخيص GPL، قواعد أمان Firestore، وتوقيع الإصدار.

## الوثائق ذات الصلة

- [`SECURITY.md`](./SECURITY.md) — الأمان، الترخيص، والتزامات ما قبل النشر
- `BUILD_CLI.md` — البناء من سطر الأوامر
- `admin-panel/README.md` — لوحة إدارة الفلترة
