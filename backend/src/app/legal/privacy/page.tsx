import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "سياسة الخصوصية — حلقة",
  description:
    "سياسة خصوصية تطبيق حلقة لمرحلة الاختبار المغلق وفق نظام حماية البيانات الشخصية (PDPL) السعودي.",
};

export default function PrivacyPolicyPage() {
  return (
    <main
      dir="rtl"
      lang="ar"
      className="mx-auto max-w-3xl px-6 py-12 leading-loose text-zinc-100"
      style={{ fontFamily: "var(--font-cairo), system-ui, -apple-system, sans-serif" }}
    >
      <article className="prose prose-invert max-w-none">
        <h1 className="text-3xl font-bold mb-2">سياسة الخصوصية — تطبيق حلقة</h1>
        <p className="text-sm text-zinc-400 mb-1">
          <strong>آخر تحديث:</strong> 2 مايو 2026
        </p>
        <p className="text-sm text-zinc-400 mb-1">
          <strong>المنطقة:</strong> المملكة العربية السعودية
        </p>
        <p className="text-sm text-zinc-400 mb-6">
          <strong>النطاق:</strong> هذه الوثيقة تخص نسخة الاختبار المغلق (Closed
          Beta). ستُستبدل قبل الإطلاق العام بسياسة كاملة تحت إشراف مستشار قانوني
          سعودي مرخّص ومتوافقة كليّاً مع نظام حماية البيانات الشخصية (PDPL).
        </p>

        <hr className="my-6 border-zinc-700" />

        <h2 className="text-xl font-semibold mt-8 mb-3">1) هويّة المعالِج</h2>
        <p>
          تطبيق <em>حلقة</em> منتج قيد التطوير من قِبل ali travians (المالك
          ومسؤول البيانات لمرحلة البيتا).
        </p>
        <ul className="list-disc pr-6 space-y-1">
          <li>البريد لطلبات حقوق المستخدم: privacy@halqa.app</li>
          <li>ممثل الحماية: يُحدَّد عند تجاوز حدود PDPL للمسؤولين الفعليين.</li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">2) ما البيانات التي نجمعها</h2>

        <h3 className="text-lg font-semibold mt-4 mb-2">
          تُجمَع لأنها مطلوبة لتشغيل الخدمة
        </h3>
        <ul className="list-disc pr-6 space-y-1">
          <li>بريد إلكتروني (للمصادقة).</li>
          <li>مُعرّف Firebase الخاص بك (UID).</li>
          <li>اسم العرض، إن قمت بكتابته.</li>
          <li>وقت الاتصال، عنوان IP المُختصر (آخر أوكتيت مخفي)، نوع الجهاز.</li>
        </ul>

        <h3 className="text-lg font-semibold mt-4 mb-2">
          تُجمَع فقط حين تستخدم ميزات معيّنة
        </h3>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            صور KYC (الهوية، صورة شخصية): تخزّن مشفّرة، تُراجَع يدوياً، تُحذف بعد
            قرار المراجعة.
          </li>
          <li>
            صوت وفيديو البث المباشر: لا يُسجَّل ولا يُخزَّن. يُمرَّر عبر LiveKit
            في الوقت الحقيقي ويُفقَد بانتهاء الجلسة.
          </li>
          <li>رسائل الدردشة الحيّة: تُحفَظ 30 يوماً للسلامة والمراجعة.</li>
          <li>الإبلاغات وقرارات الحظر: تُحفَظ لأغراض السلامة.</li>
          <li>
            معاملات الكوينز/الماس: تُحفَظ لأغراض الفوترة، التدقيق، ومنع التحايل.
          </li>
        </ul>

        <h3 className="text-lg font-semibold mt-4 mb-2">لا نجمع</h3>
        <ul className="list-disc pr-6 space-y-1">
          <li>موقع GPS الدقيق.</li>
          <li>جهات الاتصال أو سجل المكالمات.</li>
          <li>محتوى رسائل خارج التطبيق.</li>
          <li>صور/فيديو من خارج تدفق KYC.</li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">
          3) الأساس القانوني للمعالجة (PDPL)
        </h2>
        <div className="overflow-x-auto my-4">
          <table className="w-full text-right border border-zinc-700">
            <thead className="bg-zinc-800">
              <tr>
                <th className="border border-zinc-700 px-3 py-2">البيانات</th>
                <th className="border border-zinc-700 px-3 py-2">الأساس القانوني</th>
                <th className="border border-zinc-700 px-3 py-2">الغرض</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">بريد + UID</td>
                <td className="border border-zinc-700 px-3 py-2">
                  ضرورة تنفيذ العقد
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  تسجيل الدخول، أمان الحساب
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">KYC</td>
                <td className="border border-zinc-700 px-3 py-2">
                  موافقة صريحة + التزام نظامي
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  التحقق من العمر والهوية
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">
                  صوت/فيديو البث
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  موافقة صريحة قبل كل بث
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  تشغيل خدمة البث
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">
                  رسائل الدردشة
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  مصلحة مشروعة (سلامة)
                </td>
                <td className="border border-zinc-700 px-3 py-2">
                  منع المحتوى المسيء، الإبلاغ، الحظر
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">Logs IP</td>
                <td className="border border-zinc-700 px-3 py-2">مصلحة مشروعة</td>
                <td className="border border-zinc-700 px-3 py-2">
                  مكافحة الاحتيال، الحماية الأمنية
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <p>
          <strong>إقرار صريح:</strong> الأساس القانوني لمعالجة بياناتك هو موافقتك
          الصريحة وفق المادة (5) من نظام حماية البيانات الشخصية السعودي. تُحفظ
          بياناتك لمدة أقصاها 12 شهراً من آخر نشاط ثم تُحذف نهائياً. يحق لك في
          أي وقت طلب الوصول إلى بياناتك أو تصحيحها أو حذفها أو سحب موافقتك عبر{" "}
          <a href="mailto:privacy@halqa.app" className="text-purple-400 underline">
            privacy@halqa.app
          </a>
          . تُخزَّن بعض البيانات على Google Cloud (Firestore) خارج المملكة،
          وباستمرارك في الاستخدام فإنك توافق صراحةً على هذا النقل وفق المادة (29)
          من النظام.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">4) المشاركة مع أطراف ثالثة</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            <strong>Firebase / Google</strong> (مصادقة، Firestore، Cloud
            Functions): البيانات تخزَّن في region قريبة (Multi-region
            asia-south1).
          </li>
          <li>
            <strong>LiveKit Cloud</strong> (بث صوت/فيديو): يمرر التدفقات في
            الوقت الحقيقي، لا يخزن المحتوى.
          </li>
          <li>
            <strong>Vercel</strong> (استضافة الباك إند): logs الطلبات تُحفَظ 30
            يوماً ثم تُنسخ.
          </li>
          <li>
            <strong>لا نبيع البيانات لأي طرف ثالث.</strong>
          </li>
          <li>
            <strong>لا توجد إعلانات مدفوعة في البيتا</strong>، فلا تتبع إعلاني.
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">5) حقوقك تحت PDPL</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            <strong>الوصول</strong>: طلب نسخة من بياناتك →{" "}
            <a href="mailto:privacy@halqa.app" className="text-purple-400 underline">
              privacy@halqa.app
            </a>
          </li>
          <li>
            <strong>التصحيح</strong>: تعديل بريد، اسم عرض، إلخ.
          </li>
          <li>
            <strong>الحذف</strong>: حذف حسابك. الـlogs المتعلقة بالسلامة
            (إبلاغات، حظر) تُحفَظ مع تجهيل (UID hash بدل UID خام).
          </li>
          <li>
            <strong>الاعتراض</strong>: على أي معالجة تُرى مفرطة.
          </li>
          <li>
            <strong>سحب الموافقة</strong>: في أي وقت من Profile → الخصوصية → سحب
            الموافقة.
          </li>
        </ul>
        <p>استجابة الطلبات: ٣٠ يوماً كحد أقصى. لا توجد رسوم على الطلب الأول.</p>

        <h2 className="text-xl font-semibold mt-8 mb-3">6) الأطفال</h2>
        <p>
          <strong>الحد الأدنى للعمر: 18 سنة.</strong> هذا تطبيق صوت/فيديو حي مع
          تفاعل مالي (هدايا)، فلا يصلح للقاصرين. إذا اكتُشف حساب لقاصر، يُحظَر
          الحساب فوراً وتُحذف بياناته خلال 7 أيام. نطبّق NCMEC report flow إذا
          اكتُشف محتوى استغلال أطفال؛ يُبلَّغ مكتب التحقيق المختص في المملكة +
          NCMEC الدولي.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">7) الأمن</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>التشفير في النقل: TLS 1.2+ لجميع الاتصالات.</li>
          <li>
            التشفير في التخزين: Firestore + Cloud Storage يستخدمان AES-256
            افتراضياً.
          </li>
          <li>
            صلاحيات الموظفين: بنية RBAC (Owner &gt; Staff &gt; Mod &gt; Scout
            &gt; User) مع audit log لكل قرار.
          </li>
          <li>
            في حال خرق بيانات، نُبلِّغ سدايا خلال 72 ساعة وفق PDPL Article 26.
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">8) الاحتفاظ بالبيانات</h2>
        <div className="overflow-x-auto my-4">
          <table className="w-full text-right border border-zinc-700">
            <thead className="bg-zinc-800">
              <tr>
                <th className="border border-zinc-700 px-3 py-2">البيانات</th>
                <th className="border border-zinc-700 px-3 py-2">فترة الاحتفاظ</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">حساب نشط</td>
                <td className="border border-zinc-700 px-3 py-2">طوال نشاط الحساب</td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">حساب محذوف</td>
                <td className="border border-zinc-700 px-3 py-2">
                  30 يوماً soft-delete، ثم محو فعلي
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">KYC images</td>
                <td className="border border-zinc-700 px-3 py-2">
                  حتى قرار المراجعة + 7 أيام
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">Chat</td>
                <td className="border border-zinc-700 px-3 py-2">30 يوماً</td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">إبلاغات/حظر</td>
                <td className="border border-zinc-700 px-3 py-2">
                  دائم (مجهَّل بعد سنة)
                </td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">Vercel logs</td>
                <td className="border border-zinc-700 px-3 py-2">30 يوماً</td>
              </tr>
              <tr>
                <td className="border border-zinc-700 px-3 py-2">Firestore audit log</td>
                <td className="border border-zinc-700 px-3 py-2">سنة كاملة</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h2 className="text-xl font-semibold mt-8 mb-3">9) التغييرات على السياسة</h2>
        <p>
          نُبلّغك عبر إشعار داخل التطبيق + بريد إلكتروني عند أي تعديل جوهري قبل
          نفاذ السياسة الجديدة بـ7 أيام على الأقل.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">10) جهة الاتصال</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            البريد:{" "}
            <a href="mailto:privacy@halqa.app" className="text-purple-400 underline">
              privacy@halqa.app
            </a>
          </li>
          <li>المسؤول: ali travians</li>
          <li>
            الإبلاغ عن انتهاك:{" "}
            <a href="mailto:trust@halqa.app" className="text-purple-400 underline">
              trust@halqa.app
            </a>
          </li>
        </ul>

        <hr className="my-8 border-zinc-700" />
        <p className="text-xs text-zinc-500">
          هذه النسخة (closed beta v0.1.x) تخضع للتحديث قبل الإطلاق العام.
        </p>
      </article>
    </main>
  );
}
