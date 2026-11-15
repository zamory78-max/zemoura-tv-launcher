package com.tvlauncher.data

data class Country(val code: String, val name: String)
data class Wilaya(val name: String, val lat: Double, val lon: Double)

object Countries {
    val list = listOf(
        Country("DZ", "الجزائر"), Country("MA", "المغرب"), Country("TN", "تونس"), Country("LY", "ليبيا"),
        Country("EG", "مصر"), Country("SA", "السعودية"), Country("AE", "الإمارات"), Country("QA", "قطر"),
        Country("KW", "الكويت"), Country("BH", "البحرين"), Country("OM", "عُمان"), Country("JO", "الأردن"),
        Country("PS", "فلسطين"), Country("LB", "لبنان"), Country("SY", "سوريا"), Country("IQ", "العراق"),
        Country("YE", "اليمن"), Country("SD", "السودان"), Country("MR", "موريتانيا"), Country("TR", "تركيا"),
        Country("FR", "فرنسا"), Country("ES", "إسبانيا"), Country("IT", "إيطاليا"), Country("DE", "ألمانيا"),
        Country("BE", "بلجيكا"), Country("GB", "بريطانيا"), Country("CA", "كندا"), Country("US", "أمريكا")
    )

    /** ولايات الجزائر الـ 58 مع إحداثيات مراكزها (تقريبية). */
    val wilayas = listOf(
        Wilaya("أدرار", 27.87, -0.29), Wilaya("الشلف", 36.16, 1.33), Wilaya("الأغواط", 33.80, 2.88),
        Wilaya("أم البواقي", 35.88, 7.11), Wilaya("باتنة", 35.56, 6.17), Wilaya("بجاية", 36.75, 5.06),
        Wilaya("بسكرة", 34.85, 5.73), Wilaya("بشار", 31.62, -2.22), Wilaya("البليدة", 36.47, 2.83),
        Wilaya("البويرة", 36.37, 3.90), Wilaya("تمنراست", 22.79, 5.53), Wilaya("تبسة", 35.40, 8.12),
        Wilaya("تلمسان", 34.88, -1.32), Wilaya("تيارت", 35.37, 1.32), Wilaya("تيزي وزو", 36.71, 4.05),
        Wilaya("الجزائر", 36.75, 3.06), Wilaya("الجلفة", 34.67, 3.25), Wilaya("جيجل", 36.82, 5.77),
        Wilaya("سطيف", 36.19, 5.41), Wilaya("سعيدة", 34.83, 0.15), Wilaya("سكيكدة", 36.88, 6.91),
        Wilaya("سيدي بلعباس", 35.19, -0.63), Wilaya("عنابة", 36.90, 7.77), Wilaya("قالمة", 36.46, 7.43),
        Wilaya("قسنطينة", 36.37, 6.61), Wilaya("المدية", 36.26, 2.75), Wilaya("مستغانم", 35.93, 0.09),
        Wilaya("المسيلة", 35.70, 4.54), Wilaya("معسكر", 35.40, 0.14), Wilaya("ورقلة", 31.95, 5.32),
        Wilaya("وهران", 35.70, -0.63), Wilaya("البيض", 33.68, 1.02), Wilaya("إليزي", 26.51, 8.48),
        Wilaya("برج بوعريريج", 36.07, 4.76), Wilaya("بومرداس", 36.76, 3.48), Wilaya("الطارف", 36.77, 8.31),
        Wilaya("تندوف", 27.67, -8.15), Wilaya("تيسمسيلت", 35.61, 1.81), Wilaya("الوادي", 33.37, 6.86),
        Wilaya("خنشلة", 35.43, 7.14), Wilaya("سوق أهراس", 36.29, 7.95), Wilaya("تيبازة", 36.59, 2.45),
        Wilaya("ميلة", 36.45, 6.26), Wilaya("عين الدفلى", 36.26, 2.22), Wilaya("النعامة", 33.27, -0.31),
        Wilaya("عين تموشنت", 35.30, -1.14), Wilaya("غرداية", 32.49, 3.67), Wilaya("غليزان", 35.74, 0.56),
        Wilaya("تيميمون", 29.26, 0.23), Wilaya("برج باجي مختار", 21.33, 0.95), Wilaya("أولاد جلال", 34.43, 5.07),
        Wilaya("بني عباس", 30.13, -2.17), Wilaya("عين صالح", 27.20, 2.47), Wilaya("عين قزام", 19.57, 5.77),
        Wilaya("تقرت", 33.10, 6.07), Wilaya("جانت", 24.55, 9.48), Wilaya("المغير", 33.95, 5.92),
        Wilaya("المنيعة", 30.58, 2.88)
    )
}
