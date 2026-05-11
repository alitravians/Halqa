import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "شروط الاستخدام — حلقة",
  description:
    "شروط استخدام تطبيق حلقة لمرحلة الاختبار المغلق — قواعد البث، الهدايا، السلوك، والحظر.",
};

export default function TermsOfServicePage() {
  return (
    <main
      dir="rtl"
      lang="ar"
      className="mx-auto max-w-3xl px-6 py-12 leading-loose text-zinc-100"
      style={{ fontFamily: "var(--font-cairo), system-ui, -apple-system, sans-serif" }}
    >
      <article className="prose prose-invert max-w-none">
        <h1 className="text-3xl font-bold mb-2">شروط الاستخدام — تطبيق حلقة</h1>
        <p className="text-sm text-zinc-400 mb-6">
          <strong>آخر تحديث:</strong> 2 مايو 2026 — <em>مرحلة الاختبار المغلق</em>
        </p>

        <hr className="my-6 border-zinc-700" />

        <h2 className="text-xl font-semibold mt-8 mb-3">1) الأهلية</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>الحد الأدنى للعمر: 18 سنة قمرية على الأقل.</li>
          <li>
            يجب أن تكون مقيماً نظامياً في المملكة العربية السعودية أو دول الخليج
            خلال فترة البيتا.
          </li>
          <li>تمنح موافقتك على إثبات هويتك (KYC) قبل عمليات السحب.</li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">2) السلوك المسموح</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>التواصل مع المجتمع باحترام، بدون تحريض أو إساءة.</li>
          <li>لا يُسمح بمحتوى جنسي، عنف صريح، أو ترويج للمخدرات.</li>
          <li>لا يُسمح بالإعلان لجهات خارجية أو بيع منتجات داخل البث.</li>
          <li>لا يُسمح بانتحال شخصية موظف حلقة أو أي جهة رسمية.</li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">3) النظام المالي</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>الكوينز (Coins) عملة شراء داخلية لا يمكن سحبها نقداً.</li>
          <li>
            الماس (Diamonds) يحصل عليه المضيف من الهدايا، ويمكن تحويله نقداً عبر
            طلب سحب بعد KYC مكتمل.
          </li>
          <li>
            خلال فترة الاختبار المغلق، عمليات السحب مغلقة (شارة "اختبار فقط").
          </li>
          <li>
            في حال شُكّ في تحايل، يُحتجَز الرصيد مؤقتاً حتى انتهاء التحقيق
            (حد أقصى 30 يوماً).
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">4) الحظر والإيقاف</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            مخالفة سياسة المحتوى قد تؤدي إلى تحذير، إيقاف مؤقت، أو حظر دائم.
          </li>
          <li>قرارات الحظر قابلة للطعن خلال 14 يوماً.</li>
          <li>
            الحظر الناتج عن جرائم سلامة الطفل (CSAE) نهائي وغير قابل للطعن
            ويُبلَّغ النيابة.
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">5) الملكية الفكرية</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            تحتفظ بحقوق ملكية المحتوى الذي تنشره وتمنح حلقة ترخيصاً غير حصري
            لاستضافته وعرضه.
          </li>
          <li>
            لا يُسمح بنشر محتوى تنتهك حقوقه طرف ثالث (موسيقى، فيديو، الخ).
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">6) إخلاء المسؤولية</h2>
        <p>
          خدمة حلقة في مرحلة بيتا. قد تنقطع، تتغيّر، أو تفقد بيانات. لا نضمن
          استمرارية الخدمة خلال هذه المرحلة. الأرصدة محفوظة لكن قد تتأثر بتغييرات
          النموذج الاقتصادي قبل الإطلاق الرسمي.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">7) القانون الحاكم</h2>
        <p>
          تخضع هذه الشروط لأنظمة المملكة العربية السعودية. أي نزاع يُحال إلى
          المحاكم المختصة في الرياض.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">8) الاتصال</h2>
        <p>
          للاستفسارات أو طلب الدعم:{" "}
          <a href="mailto:support@halqa.app" className="text-purple-400 underline">
            support@halqa.app
          </a>
        </p>
      </article>
    </main>
  );
}
