package com.halqa.app.data

/**
 * A single country entry used by [PhoneAuthScreen] to format and dial-prefix a phone number.
 *
 * @param iso        ISO 3166-1 alpha-2 code (e.g. "SA"). Drives the flag emoji.
 * @param dial       International dial prefix WITHOUT the leading "+". Examples: "966", "1", "44".
 * @param nameAr     Arabic country name shown to the user.
 * @param nameEn     English country name used for substring search.
 * @param maxDigits  Realistic upper bound for the local subscriber number, used to cap input length.
 *                   This is intentionally permissive (we are not validating against E.164 minutiae).
 */
data class Country(
    val iso: String,
    val dial: String,
    val nameAr: String,
    val nameEn: String,
    val maxDigits: Int = 12,
) {
    /**
     * The flag emoji is rendered by combining the two regional-indicator code points for [iso].
     * For "SA" this produces 🇸🇦; for "GB" this produces 🇬🇧, etc.
     */
    val flag: String
        get() {
            if (iso.length != 2) return ""
            val first = Character.toChars(0x1F1E6 + (iso[0].uppercaseChar().code - 'A'.code))
            val second = Character.toChars(0x1F1E6 + (iso[1].uppercaseChar().code - 'A'.code))
            return String(first) + String(second)
        }
}

/**
 * World list of country dial codes, sorted alphabetically by Arabic name with the priority
 * countries (Saudi, GCC, Egypt, etc.) pinned to the top via [Countries.priority].
 *
 * The data is hand-curated from ITU E.164 with Arabic names matching the Saudi government
 * standard romanization. Coverage is 240+ entries — every UN member state plus dependencies.
 */
object Countries {

    /** Pinned to the top of the picker for the Gulf-first audience. */
    val priorityIso: List<String> = listOf(
        "SA", "AE", "KW", "QA", "BH", "OM", "EG", "JO", "IQ", "YE",
        "SY", "LB", "PS", "TR", "US", "GB",
    )

    val saudiArabia: Country = Country("SA", "966", "السعودية", "Saudi Arabia", maxDigits = 9)

    val all: List<Country> = listOf(
        Country("AF", "93",   "أفغانستان",            "Afghanistan",                maxDigits = 9),
        Country("AL", "355",  "ألبانيا",              "Albania",                    maxDigits = 9),
        Country("DZ", "213",  "الجزائر",              "Algeria",                    maxDigits = 9),
        Country("AS", "1684", "ساموا الأمريكية",      "American Samoa",             maxDigits = 7),
        Country("AD", "376",  "أندورا",               "Andorra",                    maxDigits = 9),
        Country("AO", "244",  "أنغولا",               "Angola",                     maxDigits = 9),
        Country("AI", "1264", "أنغويلا",              "Anguilla",                   maxDigits = 7),
        Country("AG", "1268", "أنتيغوا وبربودا",      "Antigua and Barbuda",        maxDigits = 7),
        Country("AR", "54",   "الأرجنتين",            "Argentina",                  maxDigits = 11),
        Country("AM", "374",  "أرمينيا",              "Armenia",                    maxDigits = 8),
        Country("AW", "297",  "أروبا",                "Aruba",                      maxDigits = 7),
        Country("AU", "61",   "أستراليا",             "Australia",                  maxDigits = 9),
        Country("AT", "43",   "النمسا",               "Austria",                    maxDigits = 11),
        Country("AZ", "994",  "أذربيجان",             "Azerbaijan",                 maxDigits = 9),
        Country("BS", "1242", "الباهاما",             "Bahamas",                    maxDigits = 7),
        Country("BH", "973",  "البحرين",              "Bahrain",                    maxDigits = 8),
        Country("BD", "880",  "بنغلاديش",             "Bangladesh",                 maxDigits = 11),
        Country("BB", "1246", "بربادوس",              "Barbados",                   maxDigits = 7),
        Country("BY", "375",  "بيلاروس",              "Belarus",                    maxDigits = 9),
        Country("BE", "32",   "بلجيكا",               "Belgium",                    maxDigits = 9),
        Country("BZ", "501",  "بليز",                 "Belize",                     maxDigits = 7),
        Country("BJ", "229",  "بنين",                 "Benin",                      maxDigits = 8),
        Country("BM", "1441", "برمودا",               "Bermuda",                    maxDigits = 7),
        Country("BT", "975",  "بوتان",                "Bhutan",                     maxDigits = 8),
        Country("BO", "591",  "بوليفيا",              "Bolivia",                    maxDigits = 8),
        Country("BA", "387",  "البوسنة والهرسك",      "Bosnia and Herzegovina",     maxDigits = 8),
        Country("BW", "267",  "بوتسوانا",             "Botswana",                   maxDigits = 8),
        Country("BR", "55",   "البرازيل",             "Brazil",                     maxDigits = 11),
        Country("IO", "246",  "إقليم المحيط الهندي",  "British Indian Ocean Terr.", maxDigits = 7),
        Country("BN", "673",  "بروناي",               "Brunei",                     maxDigits = 7),
        Country("BG", "359",  "بلغاريا",              "Bulgaria",                   maxDigits = 9),
        Country("BF", "226",  "بوركينا فاسو",         "Burkina Faso",               maxDigits = 8),
        Country("BI", "257",  "بوروندي",              "Burundi",                    maxDigits = 8),
        Country("KH", "855",  "كمبوديا",              "Cambodia",                   maxDigits = 9),
        Country("CM", "237",  "الكاميرون",            "Cameroon",                   maxDigits = 9),
        Country("CA", "1",    "كندا",                 "Canada",                     maxDigits = 10),
        Country("CV", "238",  "الرأس الأخضر",         "Cape Verde",                 maxDigits = 7),
        Country("KY", "1345", "جزر كايمان",           "Cayman Islands",             maxDigits = 7),
        Country("CF", "236",  "إفريقيا الوسطى",       "Central African Republic",   maxDigits = 8),
        Country("TD", "235",  "تشاد",                 "Chad",                       maxDigits = 8),
        Country("CL", "56",   "تشيلي",                "Chile",                      maxDigits = 9),
        Country("CN", "86",   "الصين",                "China",                      maxDigits = 11),
        Country("CO", "57",   "كولومبيا",             "Colombia",                   maxDigits = 10),
        Country("KM", "269",  "جزر القمر",            "Comoros",                    maxDigits = 7),
        Country("CG", "242",  "الكونغو",              "Congo",                      maxDigits = 9),
        Country("CD", "243",  "الكونغو الديمقراطية",  "Congo (DRC)",                maxDigits = 9),
        Country("CK", "682",  "جزر كوك",              "Cook Islands",               maxDigits = 5),
        Country("CR", "506",  "كوستاريكا",            "Costa Rica",                 maxDigits = 8),
        Country("CI", "225",  "ساحل العاج",           "Côte d'Ivoire",              maxDigits = 10),
        Country("HR", "385",  "كرواتيا",              "Croatia",                    maxDigits = 9),
        Country("CU", "53",   "كوبا",                 "Cuba",                       maxDigits = 8),
        Country("CY", "357",  "قبرص",                 "Cyprus",                     maxDigits = 8),
        Country("CZ", "420",  "التشيك",               "Czech Republic",             maxDigits = 9),
        Country("DK", "45",   "الدنمارك",             "Denmark",                    maxDigits = 8),
        Country("DJ", "253",  "جيبوتي",               "Djibouti",                   maxDigits = 8),
        Country("DM", "1767", "دومينيكا",             "Dominica",                   maxDigits = 7),
        Country("DO", "1809", "الدومينيكان",          "Dominican Republic",         maxDigits = 10),
        Country("EC", "593",  "الإكوادور",            "Ecuador",                    maxDigits = 9),
        Country("EG", "20",   "مصر",                  "Egypt",                      maxDigits = 10),
        Country("SV", "503",  "السلفادور",            "El Salvador",                maxDigits = 8),
        Country("GQ", "240",  "غينيا الاستوائية",     "Equatorial Guinea",          maxDigits = 9),
        Country("ER", "291",  "إريتريا",              "Eritrea",                    maxDigits = 7),
        Country("EE", "372",  "إستونيا",              "Estonia",                    maxDigits = 8),
        Country("SZ", "268",  "إسواتيني",             "Eswatini",                   maxDigits = 8),
        Country("ET", "251",  "إثيوبيا",              "Ethiopia",                   maxDigits = 9),
        Country("FK", "500",  "جزر فوكلاند",          "Falkland Islands",           maxDigits = 5),
        Country("FO", "298",  "جزر فارو",             "Faroe Islands",              maxDigits = 6),
        Country("FJ", "679",  "فيجي",                 "Fiji",                       maxDigits = 7),
        Country("FI", "358",  "فنلندا",               "Finland",                    maxDigits = 11),
        Country("FR", "33",   "فرنسا",                "France",                     maxDigits = 9),
        Country("GF", "594",  "غويانا الفرنسية",      "French Guiana",              maxDigits = 9),
        Country("PF", "689",  "بولينيزيا الفرنسية",   "French Polynesia",           maxDigits = 8),
        Country("GA", "241",  "الغابون",              "Gabon",                      maxDigits = 8),
        Country("GM", "220",  "غامبيا",               "Gambia",                     maxDigits = 7),
        Country("GE", "995",  "جورجيا",               "Georgia",                    maxDigits = 9),
        Country("DE", "49",   "ألمانيا",              "Germany",                    maxDigits = 12),
        Country("GH", "233",  "غانا",                 "Ghana",                      maxDigits = 9),
        Country("GI", "350",  "جبل طارق",             "Gibraltar",                  maxDigits = 8),
        Country("GR", "30",   "اليونان",              "Greece",                     maxDigits = 10),
        Country("GL", "299",  "جرينلاند",             "Greenland",                  maxDigits = 6),
        Country("GD", "1473", "غرينادا",              "Grenada",                    maxDigits = 7),
        Country("GP", "590",  "غوادلوب",              "Guadeloupe",                 maxDigits = 9),
        Country("GU", "1671", "غوام",                 "Guam",                       maxDigits = 7),
        Country("GT", "502",  "غواتيمالا",            "Guatemala",                  maxDigits = 8),
        Country("GN", "224",  "غينيا",                "Guinea",                     maxDigits = 9),
        Country("GW", "245",  "غينيا بيساو",          "Guinea-Bissau",              maxDigits = 7),
        Country("GY", "592",  "غويانا",               "Guyana",                     maxDigits = 7),
        Country("HT", "509",  "هايتي",                "Haiti",                      maxDigits = 8),
        Country("HN", "504",  "هندوراس",              "Honduras",                   maxDigits = 8),
        Country("HK", "852",  "هونغ كونغ",            "Hong Kong",                  maxDigits = 9),
        Country("HU", "36",   "المجر",                "Hungary",                    maxDigits = 9),
        Country("IS", "354",  "آيسلندا",              "Iceland",                    maxDigits = 9),
        Country("IN", "91",   "الهند",                "India",                      maxDigits = 10),
        Country("ID", "62",   "إندونيسيا",            "Indonesia",                  maxDigits = 12),
        Country("IR", "98",   "إيران",                "Iran",                       maxDigits = 10),
        Country("IQ", "964",  "العراق",               "Iraq",                       maxDigits = 10),
        Country("IE", "353",  "أيرلندا",              "Ireland",                    maxDigits = 11),
        Country("IL", "972",  "إسرائيل",              "Israel",                     maxDigits = 9),
        Country("IT", "39",   "إيطاليا",              "Italy",                      maxDigits = 11),
        Country("JM", "1876", "جامايكا",              "Jamaica",                    maxDigits = 7),
        Country("JP", "81",   "اليابان",              "Japan",                      maxDigits = 11),
        Country("JO", "962",  "الأردن",               "Jordan",                     maxDigits = 9),
        Country("KZ", "7",    "كازاخستان",            "Kazakhstan",                 maxDigits = 10),
        Country("KE", "254",  "كينيا",                "Kenya",                      maxDigits = 10),
        Country("KI", "686",  "كيريباتي",             "Kiribati",                   maxDigits = 5),
        Country("XK", "383",  "كوسوفو",               "Kosovo",                     maxDigits = 8),
        Country("KW", "965",  "الكويت",               "Kuwait",                     maxDigits = 8),
        Country("KG", "996",  "قيرغيزستان",           "Kyrgyzstan",                 maxDigits = 9),
        Country("LA", "856",  "لاوس",                 "Laos",                       maxDigits = 10),
        Country("LV", "371",  "لاتفيا",               "Latvia",                     maxDigits = 8),
        Country("LB", "961",  "لبنان",                "Lebanon",                    maxDigits = 8),
        Country("LS", "266",  "ليسوتو",               "Lesotho",                    maxDigits = 8),
        Country("LR", "231",  "ليبيريا",              "Liberia",                    maxDigits = 8),
        Country("LY", "218",  "ليبيا",                "Libya",                      maxDigits = 9),
        Country("LI", "423",  "ليختنشتاين",           "Liechtenstein",              maxDigits = 7),
        Country("LT", "370",  "ليتوانيا",             "Lithuania",                  maxDigits = 8),
        Country("LU", "352",  "لوكسمبورغ",            "Luxembourg",                 maxDigits = 9),
        Country("MO", "853",  "ماكاو",                "Macao",                      maxDigits = 8),
        Country("MG", "261",  "مدغشقر",               "Madagascar",                 maxDigits = 9),
        Country("MW", "265",  "مالاوي",               "Malawi",                     maxDigits = 9),
        Country("MY", "60",   "ماليزيا",              "Malaysia",                   maxDigits = 10),
        Country("MV", "960",  "المالديف",             "Maldives",                   maxDigits = 7),
        Country("ML", "223",  "مالي",                 "Mali",                       maxDigits = 8),
        Country("MT", "356",  "مالطا",                "Malta",                      maxDigits = 8),
        Country("MH", "692",  "جزر مارشال",           "Marshall Islands",           maxDigits = 7),
        Country("MQ", "596",  "مارتينيك",             "Martinique",                 maxDigits = 9),
        Country("MR", "222",  "موريتانيا",            "Mauritania",                 maxDigits = 8),
        Country("MU", "230",  "موريشيوس",             "Mauritius",                  maxDigits = 8),
        Country("MX", "52",   "المكسيك",              "Mexico",                     maxDigits = 10),
        Country("FM", "691",  "ميكرونيزيا",           "Micronesia",                 maxDigits = 7),
        Country("MD", "373",  "مولدوفا",              "Moldova",                    maxDigits = 8),
        Country("MC", "377",  "موناكو",               "Monaco",                     maxDigits = 8),
        Country("MN", "976",  "منغوليا",              "Mongolia",                   maxDigits = 8),
        Country("ME", "382",  "الجبل الأسود",         "Montenegro",                 maxDigits = 8),
        Country("MS", "1664", "مونتسرات",             "Montserrat",                 maxDigits = 7),
        Country("MA", "212",  "المغرب",               "Morocco",                    maxDigits = 9),
        Country("MZ", "258",  "موزمبيق",              "Mozambique",                 maxDigits = 9),
        Country("MM", "95",   "ميانمار",              "Myanmar",                    maxDigits = 10),
        Country("NA", "264",  "ناميبيا",              "Namibia",                    maxDigits = 9),
        Country("NR", "674",  "ناورو",                "Nauru",                      maxDigits = 7),
        Country("NP", "977",  "نيبال",                "Nepal",                      maxDigits = 10),
        Country("NL", "31",   "هولندا",               "Netherlands",                maxDigits = 9),
        Country("NC", "687",  "كاليدونيا الجديدة",    "New Caledonia",              maxDigits = 6),
        Country("NZ", "64",   "نيوزيلندا",            "New Zealand",                maxDigits = 10),
        Country("NI", "505",  "نيكاراغوا",            "Nicaragua",                  maxDigits = 8),
        Country("NE", "227",  "النيجر",               "Niger",                      maxDigits = 8),
        Country("NG", "234",  "نيجيريا",              "Nigeria",                    maxDigits = 10),
        Country("NU", "683",  "نيوي",                 "Niue",                       maxDigits = 4),
        Country("KP", "850",  "كوريا الشمالية",       "North Korea",                maxDigits = 13),
        Country("MK", "389",  "مقدونيا الشمالية",     "North Macedonia",            maxDigits = 8),
        Country("NO", "47",   "النرويج",              "Norway",                     maxDigits = 8),
        Country("OM", "968",  "عُمان",                "Oman",                       maxDigits = 8),
        Country("PK", "92",   "باكستان",              "Pakistan",                   maxDigits = 10),
        Country("PW", "680",  "بالاو",                "Palau",                      maxDigits = 7),
        Country("PS", "970",  "فلسطين",               "Palestine",                  maxDigits = 9),
        Country("PA", "507",  "بنما",                 "Panama",                     maxDigits = 8),
        Country("PG", "675",  "بابوا غينيا الجديدة",  "Papua New Guinea",           maxDigits = 8),
        Country("PY", "595",  "باراغواي",             "Paraguay",                   maxDigits = 9),
        Country("PE", "51",   "بيرو",                 "Peru",                       maxDigits = 9),
        Country("PH", "63",   "الفلبين",              "Philippines",                maxDigits = 10),
        Country("PL", "48",   "بولندا",               "Poland",                     maxDigits = 9),
        Country("PT", "351",  "البرتغال",             "Portugal",                   maxDigits = 9),
        Country("PR", "1787", "بورتوريكو",            "Puerto Rico",                maxDigits = 7),
        Country("QA", "974",  "قطر",                  "Qatar",                      maxDigits = 8),
        Country("RE", "262",  "ريونيون",              "Réunion",                    maxDigits = 9),
        Country("RO", "40",   "رومانيا",              "Romania",                    maxDigits = 9),
        Country("RU", "7",    "روسيا",                "Russia",                     maxDigits = 10),
        Country("RW", "250",  "رواندا",               "Rwanda",                     maxDigits = 9),
        Country("KN", "1869", "سانت كيتس ونيفيس",     "Saint Kitts and Nevis",      maxDigits = 7),
        Country("LC", "1758", "سانت لوسيا",           "Saint Lucia",                maxDigits = 7),
        Country("VC", "1784", "سانت فينسنت",          "Saint Vincent",              maxDigits = 7),
        Country("WS", "685",  "ساموا",                "Samoa",                      maxDigits = 7),
        Country("SM", "378",  "سان مارينو",           "San Marino",                 maxDigits = 10),
        Country("ST", "239",  "ساو تومي",             "São Tomé and Príncipe",      maxDigits = 7),
        saudiArabia,
        Country("SN", "221",  "السنغال",              "Senegal",                    maxDigits = 9),
        Country("RS", "381",  "صربيا",                "Serbia",                     maxDigits = 9),
        Country("SC", "248",  "سيشل",                 "Seychelles",                 maxDigits = 7),
        Country("SL", "232",  "سيراليون",             "Sierra Leone",               maxDigits = 8),
        Country("SG", "65",   "سنغافورة",             "Singapore",                  maxDigits = 8),
        Country("SK", "421",  "سلوفاكيا",             "Slovakia",                   maxDigits = 9),
        Country("SI", "386",  "سلوفينيا",             "Slovenia",                   maxDigits = 8),
        Country("SB", "677",  "جزر سليمان",           "Solomon Islands",            maxDigits = 5),
        Country("SO", "252",  "الصومال",              "Somalia",                    maxDigits = 9),
        Country("ZA", "27",   "جنوب أفريقيا",         "South Africa",               maxDigits = 9),
        Country("KR", "82",   "كوريا الجنوبية",       "South Korea",                maxDigits = 10),
        Country("SS", "211",  "جنوب السودان",         "South Sudan",                maxDigits = 9),
        Country("ES", "34",   "إسبانيا",              "Spain",                      maxDigits = 9),
        Country("LK", "94",   "سريلانكا",             "Sri Lanka",                  maxDigits = 9),
        Country("SD", "249",  "السودان",              "Sudan",                      maxDigits = 9),
        Country("SR", "597",  "سورينام",              "Suriname",                   maxDigits = 7),
        Country("SE", "46",   "السويد",               "Sweden",                     maxDigits = 9),
        Country("CH", "41",   "سويسرا",               "Switzerland",                maxDigits = 9),
        Country("SY", "963",  "سوريا",                "Syria",                      maxDigits = 9),
        Country("TW", "886",  "تايوان",               "Taiwan",                     maxDigits = 9),
        Country("TJ", "992",  "طاجيكستان",            "Tajikistan",                 maxDigits = 9),
        Country("TZ", "255",  "تنزانيا",              "Tanzania",                   maxDigits = 9),
        Country("TH", "66",   "تايلاند",              "Thailand",                   maxDigits = 9),
        Country("TL", "670",  "تيمور الشرقية",        "Timor-Leste",                maxDigits = 8),
        Country("TG", "228",  "توغو",                 "Togo",                       maxDigits = 8),
        Country("TK", "690",  "توكيلاو",              "Tokelau",                    maxDigits = 4),
        Country("TO", "676",  "تونغا",                "Tonga",                      maxDigits = 7),
        Country("TT", "1868", "ترينيداد وتوباغو",     "Trinidad and Tobago",        maxDigits = 7),
        Country("TN", "216",  "تونس",                 "Tunisia",                    maxDigits = 8),
        Country("TR", "90",   "تركيا",                "Turkey",                     maxDigits = 10),
        Country("TM", "993",  "تركمانستان",           "Turkmenistan",               maxDigits = 8),
        Country("TC", "1649", "جزر توركس وكايكوس",    "Turks and Caicos",           maxDigits = 7),
        Country("TV", "688",  "توفالو",               "Tuvalu",                     maxDigits = 6),
        Country("UG", "256",  "أوغندا",               "Uganda",                     maxDigits = 9),
        Country("UA", "380",  "أوكرانيا",             "Ukraine",                    maxDigits = 9),
        Country("AE", "971",  "الإمارات",             "United Arab Emirates",       maxDigits = 9),
        Country("GB", "44",   "بريطانيا",             "United Kingdom",             maxDigits = 10),
        Country("US", "1",    "الولايات المتحدة",     "United States",              maxDigits = 10),
        Country("UY", "598",  "الأوروغواي",           "Uruguay",                    maxDigits = 8),
        Country("UZ", "998",  "أوزبكستان",            "Uzbekistan",                 maxDigits = 9),
        Country("VU", "678",  "فانواتو",              "Vanuatu",                    maxDigits = 7),
        Country("VA", "379",  "الفاتيكان",            "Vatican City",               maxDigits = 10),
        Country("VE", "58",   "فنزويلا",              "Venezuela",                  maxDigits = 10),
        Country("VN", "84",   "فيتنام",               "Vietnam",                    maxDigits = 10),
        Country("VG", "1284", "جزر العذراء البريطانية", "Virgin Islands (British)", maxDigits = 7),
        Country("VI", "1340", "جزر العذراء الأمريكية",  "Virgin Islands (US)",      maxDigits = 7),
        Country("WF", "681",  "والس وفوتونا",         "Wallis and Futuna",          maxDigits = 6),
        Country("YE", "967",  "اليمن",                "Yemen",                      maxDigits = 9),
        Country("ZM", "260",  "زامبيا",               "Zambia",                     maxDigits = 9),
        Country("ZW", "263",  "زيمبابوي",             "Zimbabwe",                   maxDigits = 9),
    )

    /** Map for fast lookup by ISO code. */
    val byIso: Map<String, Country> = all.associateBy { it.iso }

    /** Priority countries first, then everyone else (deduped). */
    val sortedForPicker: List<Country> = run {
        val priority = priorityIso.mapNotNull { byIso[it] }
        val priorityIsoSet = priorityIso.toSet()
        val rest = all.filter { it.iso !in priorityIsoSet }.sortedBy { it.nameAr }
        priority + rest
    }

    /**
     * Case-insensitive substring search on Arabic name, English name, or dial prefix.
     * Empty queries return [sortedForPicker] unchanged.
     */
    fun search(query: String): List<Country> {
        val q = query.trim()
        if (q.isEmpty()) return sortedForPicker
        val needle = q.lowercase().removePrefix("+")
        return sortedForPicker.filter { c ->
            c.nameAr.contains(q) ||
                c.nameEn.lowercase().contains(needle) ||
                c.dial.startsWith(needle) ||
                c.iso.lowercase().contains(needle)
        }
    }
}
