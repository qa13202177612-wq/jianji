package com.jianji.app.data.model

/**
 * 分类模型。
 * emoji 作为「喵喵同款」分类图标的轻量替代：无需额外图片资源，安装包小，
 * 且在任何 Android 10+ 设备上都能稳定渲染。
 */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val type: BillType,
    val color: Int
)

object CategoryIds {
    const val FOOD = "food"
    const val TRANSPORT = "transport"
    const val SHOPPING = "shopping"
    const val FUN = "fun"
    const val HOME = "home"
    const val MEDICAL = "medical"
    const val STUDY = "study"
    const val COMMUNICATION = "communication"
    const val PET = "pet"
    const val OTHER_EXPENSE = "other_expense"

    const val SALARY = "salary"
    const val BONUS = "bonus"
    const val REFUND = "refund"
    const val INVESTMENT = "investment"
    const val PART_TIME = "parttime"
    const val OTHER_INCOME = "other_income"
}

object Categories {

    val expenses: List<Category> = listOf(
        Category(CategoryIds.FOOD, "餐饮", "🍜", BillType.EXPENSE, 0xFFFFB74D.toInt()),
        Category(CategoryIds.TRANSPORT, "交通", "🚌", BillType.EXPENSE, 0xFF4FC3F7.toInt()),
        Category(CategoryIds.SHOPPING, "购物", "🛍️", BillType.EXPENSE, 0xFFF06292.toInt()),
        Category(CategoryIds.FUN, "娱乐", "🎮", BillType.EXPENSE, 0xFFBA68C8.toInt()),
        Category(CategoryIds.HOME, "居家", "🏠", BillType.EXPENSE, 0xFF81C784.toInt()),
        Category(CategoryIds.MEDICAL, "医疗", "💊", BillType.EXPENSE, 0xFF4DB6AC.toInt()),
        Category(CategoryIds.STUDY, "学习", "📚", BillType.EXPENSE, 0xFF7986CB.toInt()),
        Category(CategoryIds.COMMUNICATION, "通讯", "📱", BillType.EXPENSE, 0xFF90A4AE.toInt()),
        Category(CategoryIds.PET, "宠物", "🐱", BillType.EXPENSE, 0xFFFFD54F.toInt()),
        Category(CategoryIds.OTHER_EXPENSE, "其他", "📦", BillType.EXPENSE, 0xFFA1887F.toInt())
    )

    val incomes: List<Category> = listOf(
        Category(CategoryIds.SALARY, "工资", "💰", BillType.INCOME, 0xFF4CAF93.toInt()),
        Category(CategoryIds.BONUS, "奖金", "🎁", BillType.INCOME, 0xFFFFB300.toInt()),
        Category(CategoryIds.REFUND, "退款", "↩️", BillType.INCOME, 0xFF64B5F6.toInt()),
        Category(CategoryIds.INVESTMENT, "理财", "📈", BillType.INCOME, 0xFF9575CD.toInt()),
        Category(CategoryIds.PART_TIME, "兼职", "💼", BillType.INCOME, 0xFF4DD0E1.toInt()),
        Category(CategoryIds.OTHER_INCOME, "其他", "🧧", BillType.INCOME, 0xFFAED581.toInt())
    )

    val all: List<Category> = expenses + incomes

    fun forType(type: BillType): List<Category> =
        if (type == BillType.INCOME) incomes else expenses

    fun byId(id: String?): Category? = all.firstOrNull { it.id == id }

    fun fallback(type: BillType): Category =
        if (type == BillType.INCOME) incomes.last() else expenses.last()

    fun safeById(id: String?, type: BillType): Category =
        byId(id)?.takeIf { it.type == type } ?: fallback(type)
}
