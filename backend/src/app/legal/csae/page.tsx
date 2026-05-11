import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "سياسة سلامة الأطفال (CSAE) — حلقة",
  description:
    "سياسة عدم التساهل لمكافحة استغلال الأطفال جنسياً (CSAE) في تطبيق حلقة — متوافقة مع متطلبات Google Play.",
};

export default function CSAEPolicyPage() {
  return (
    <main
      dir="rtl"
      lang="ar"
      className="mx-auto max-w-3xl px-6 py-12 leading-loose text-zinc-100"
      style={{ fontFamily: "var(--font-cairo), system-ui, -apple-system, sans-serif" }}
    >
      <article className="prose prose-invert max-w-none">
        <h1 className="text-3xl font-bold mb-2">
          سياسة سلامة الأطفال — Child Safety Standards (CSAE)
        </h1>
        <p className="text-sm text-zinc-400 mb-6">
          <strong>آخر تحديث:</strong> 2 مايو 2026 — متوافق مع{" "}
          <em>Google Play Child Safety Standards</em>
        </p>

        <hr className="my-6 border-zinc-700" />

        <h2 className="text-xl font-semibold mt-8 mb-3">1) الالتزام</h2>
        <p>
          تطبيق حلقة يطبّق سياسة عدم التساهل المطلقة (Zero-tolerance) ضد أي محتوى
          استغلال أو إيذاء جنسي للأطفال (Child Sexual Abuse and Exploitation —
          CSAE)، بما يتوافق مع:
        </p>
        <ul className="list-disc pr-6 space-y-1">
          <li>Google Play Child Safety Standards (يونيو 2024).</li>
          <li>
            النظام السعودي لمكافحة جرائم الاتجار بالأشخاص (المرسوم الملكي
            رقم م/40).
          </li>
          <li>
            معايير المركز الوطني الأمريكي للأطفال المفقودين والمستغلين (NCMEC).
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">
          2) الحد الأدنى للعمر
        </h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            <strong>18 سنة قمرية</strong> هو الحد الأدنى المطلق لاستخدام
            التطبيق.
          </li>
          <li>
            عند التسجيل، يتم تأكيد العمر عبر شاشة DOB attestation (إقرار صريح)،
            وبعد ذلك عبر فحص KYC هوية رسمية مرفقة بصورة شخصية.
          </li>
          <li>
            البث المباشر يتطلب &gt;=18 سنة على الـserver-side (LiveKit token
            issuance يرفض أي طلب أصغر).
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">3) الإبلاغ</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            أي مستخدم يمكنه الإبلاغ عن محتوى مشبوه عبر زر "🚩" في كل بث ودردشة.
          </li>
          <li>
            تقارير CSAE تتلقى أعلى أولوية: مراجعة خلال 60 دقيقة من الإبلاغ.
          </li>
          <li>
            الفريق الداخلي يضم safety officer مدرب على التعرف على محتوى CSAE.
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">4) الإجراءات</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            عند تأكيد محتوى CSAE: حظر فوري دائم للحساب، حفظ الأدلة (preserve
            evidence) للسلطات.
          </li>
          <li>
            إبلاغ NCMEC CyberTipline ضمن 24 ساعة من التأكد عبر:{" "}
            <a
              href="https://report.cybertip.org/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-purple-400 underline"
            >
              report.cybertip.org
            </a>
            .
          </li>
          <li>
            إبلاغ النيابة العامة السعودية / المباحث الجنائية ضمن 48 ساعة.
          </li>
          <li>
            الـIP + UID + الـmetadata تُحفَظ بشكل دائم في{" "}
            <code className="text-purple-300">csae_preserved</code> collection
            للمساعدة في التحقيقات.
          </li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">5) الوقاية التقنية</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            فحص content على رفع KYC images (face match + age estimation) قبل
            القبول.
          </li>
          <li>
            موظفو الإشراف يراقبون البثوث عشوائياً ويستخدمون أدوات detection (في
            v0.2: PhotoDNA + SafeSearch).
          </li>
          <li>
            كلمات مفتاحية حساسة في الـchat تُفعّل alerting فوري للـmoderator
            on-call.
          </li>
          <li>الـwithdraw مغلق على الحسابات الجديدة لحين اكتمال KYC.</li>
        </ul>

        <h2 className="text-xl font-semibold mt-8 mb-3">6) التدريب</h2>
        <p>
          جميع موظفي الإشراف يتلقّون تدريباً ربع سنوي على معايير CSAE، أحدث
          أساليب الـgrooming، وطرق التعرف على محتوى تخوّف الأطفال. التدريب يُوثّق
          داخلياً.
        </p>

        <h2 className="text-xl font-semibold mt-8 mb-3">7) جهة الاتصال</h2>
        <ul className="list-disc pr-6 space-y-1">
          <li>
            للإبلاغ عن قضية CSAE داخل التطبيق:{" "}
            <a href="mailto:trust@halqa.app" className="text-purple-400 underline">
              trust@halqa.app
            </a>
          </li>
          <li>
            القضايا الطارئة (محتوى نشط):{" "}
            <a
              href="https://report.cybertip.org/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-purple-400 underline"
            >
              NCMEC CyberTipline
            </a>{" "}
            مباشرة.
          </li>
          <li>السلطات السعودية: 911 / 999</li>
        </ul>

        <hr className="my-8 border-zinc-700" />
        <p className="text-xs text-zinc-500">
          نُحدّث هذه السياسة عند أي تعديل في متطلبات Google Play أو NCMEC.
        </p>
      </article>
    </main>
  );
}
