package com.halqa.app.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun LegalScreen(kind: String, navController: NavController) {
    val (title, body) = when (kind) {
        "terms" -> "شروط الاستخدام" to termsBody
        "privacy" -> "سياسة الخصوصية" to privacyBody
        else -> "إرشادات المجتمع" to communityBody
    }

    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = HalqaColors.Text)
            }
            Text(title, color = HalqaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                body,
                color = HalqaColors.Text,
                fontSize = 14.sp,
                lineHeight = 26.sp,
            )
        }
    }
}

private const val termsBody = """
مرحباً بك في تطبيق حلقة (Halqa). باستخدامك للتطبيق فإنك توافق على الشروط التالية:

1. الأهلية:
   - استخدام التطبيق كمشاهد متاح لمن أتم 13 سنة فأكثر.
   - البث المباشر متاح حصرياً لمن أتم الثامنة عشرة فأكثر. يُمنع منعاً باتاً فتح بث من قبل أي شخص دون 18 سنة، ويُحال الحساب المخالف للجهات المختصة عند توفر شروط ذلك.
   - يُحظر تماماً ظهور أي قاصر (دون 18) في إطار البث، ولو كان من أهل المذيع، لحماية الأطفال.

2. السلوك: يحظر نشر أي محتوى مخل بالآداب، يحرّض على الكراهية، أو ينتهك حقوق الآخرين. أي مخالفة قد تؤدي للتعليق أو الحظر الفوري.

3. نظام المراقبة الذاتي:
   - يعمل في حلقة نظام مراقبة تلقائي يرصد المخالفات أثناء البث.
   - عند الاشتباه بمخالفة، يتم إيقاف البث فوراً، وتُفتح مراجعة لمدة 10 دقائق يصدر بعدها النظام قراراً بنوع المخالفة والعقوبة المناسبة.
   - تصلك رسالة في صندوق الوارد عند فتح المراجعة، ثم رسالة ثانية بالقرار النهائي. لك حق الاعتراض خلال 48 ساعة.
   - سلّم العقوبات: تحذير → حظر 24 ساعة → 7 أيام → 30 يوم → حظر دائم وإحالة قانونية. حالات بث القاصرين أو ظهورهم تُعامل بأعلى درجة مباشرةً.

4. الكوينز: الكوينز عملة افتراضية داخل التطبيق فقط، غير قابلة للاسترجاع، ولا تُعتبر عملة قانونية. تُستخدم لشراء الهدايا الافتراضية.

5. الأرباح: يحتفظ المذيع بنسبة 45% من قيمة الهدايا، وتأخذ المنصة 55% (تشمل تكاليف البنية التحتية ورسوم المعالجة). الحد الأدنى للسحب 375 ريال (ما يعادل ~100 دولار).

6. KYC: يلزم التحقق من الهوية للمذيعين عند بلوغ أرباحهم 500 ريال أو أكثر، وفقاً للأنظمة المالية في المملكة العربية السعودية، ويُستخدم أيضاً للتحقق من العمر وفحص أهلية البث.

7. الإنهاء: نحتفظ بالحق في إنهاء أو تعليق حسابك في حال مخالفتك للشروط.

8. التعديلات: قد نقوم بتعديل هذه الشروط من وقت لآخر، وسنخطرك بالتعديلات الجوهرية.

للتواصل: support@halqa.app
"""

private const val privacyBody = """
نحن في حلقة نأخذ خصوصيتك بجدية. هذه السياسة توضح كيف نجمع ونستخدم ونحمي بياناتك:

1. البيانات التي نجمعها:
   - معلومات الحساب (الاسم، رقم الجوال، البريد).
   - بيانات الاستخدام (البث، الرسائل، الهدايا).
   - بيانات الجهاز (نوعه، نظام التشغيل، معرف الجهاز).
   - بيانات الكاميرا والميكروفون أثناء البث فقط (لا نخزن البث الخام إلا للمراجعة).

2. كيف نستخدمها:
   - تشغيل خدمات البث والشات.
   - تحسين التطبيق وكشف الاحتيال.
   - الامتثال للقوانين المحلية (سعودي).

3. المشاركة:
   - لا نبيع بياناتك لأي طرف ثالث.
   - نستخدم مزودي خدمة موثوقين (LiveKit للبث، Vercel للاستضافة).

4. الأمان: نستخدم تشفير TLS للنقل، وتشفير عند التخزين للبيانات الحساسة.

5. حقوقك: لك الحق في مراجعة بياناتك، تصحيحها، أو طلب حذفها.

6. حماية القاصرين: لا نسمح للأطفال دون 13 سنة باستخدام التطبيق. أي حساب يثبت أن صاحبه قاصر سيُحذف.

التواصل: privacy@halqa.app
"""

private const val communityBody = """
الجو الإيجابي والمحترم هو روح حلقة. هذي الإرشادات مهمة:

✅ مسموح:
- المحتوى الترفيهي، التعليمي، الموسيقي، الرياضي، الديني (ضمن الإطار العام).
- النقاشات الهادفة، تبادل الآراء بأدب.
- إرسال الهدايا والتعبير عن الإعجاب.

❌ ممنوع:
- فتح بث من قبل أي شخص دون 18 سنة (ممنوع منعاً باتاً).
- ظهور أي طفل/قاصر في البث لأي سبب.
- المحتوى الإباحي أو الموحي.
- التنمر، الإهانة، التحرش.
- خطاب الكراهية على أساس عرق، دين، جنس، أو خلفية.
- العنف الواقعي أو الترويج له.
- الاحتيال، الكوينز المسروقة، الحسابات المزدوجة.
- الإعلانات المخالفة (قمار، مخدرات، تمويل غير قانوني).

🛡️ حماية الأطفال (أولوية قصوى):
- نظام المراقبة الذاتي يوقف البث فور الاشتباه بظهور قاصر.
- تُفتح مراجعة 10 دقائق ثم تصدر العقوبة، مع إحالة قانونية عند ثبوت الحالة.

📨 الإبلاغ والإعلام:
- اضغط زر "إبلاغ" في أي بث أو رسالة.
- عند الاشتباه بمخالفة في بثك تصلك رسالة بفتح المراجعة، ثم رسالة بالقرار النهائي ونوع المخالفة والعقوبة. لك حق الاعتراض خلال 48 ساعة.

⚠️ سلّم العقوبات:
تحذير → حظر 24 ساعة → حظر 7 أيام → حظر 30 يوم → حظر دائم + إحالة قانونية.
مخالفات حماية الأطفال تُعامل بأعلى درجة مباشرةً.

شكراً لجعل حلقة مكاناً آمناً للجميع.
"""
