package com.kitsuneapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MealSlot(val label: String) { BREAKFAST("早餐"), LUNCH("午餐"), DINNER("晚餐") }
enum class Scope(val label: String) { PREPARATION("制作分类"), INCOME("收入"), EXPENSE("支出") }
enum class EntryType(val label: String) {
    INCOME("收入"), EXPENSE("支出"), TRANSFER("转账"), REFUND("退款");

    val scope get() = when (this) {
        INCOME -> Scope.INCOME
        EXPENSE -> Scope.EXPENSE
        else -> null
    }
}
enum class CategoryMode { DEFAULT, CUSTOM }
enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class ColorSource(val label: String) { PRESET("预设调色板"), CUSTOM("自选主色"), SYSTEM("跟随系统壁纸") }
enum class CsvKind { MEALS, LEDGER }

fun newId(): String = UUID.randomUUID().toString()

@Entity(indices = [Index(value = ["normalizedName"], unique = true)])
data class MealMethod(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val archived: Boolean,
    val showPreparationCategories: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Wallet::class, parentColumns = ["id"], childColumns = ["walletId"], deferred = true)],
    indices = [Index(value = ["scope", "ownerKey", "normalizedName"], unique = true), Index("walletId")],
)
data class Category(
    @PrimaryKey val id: String,
    val scope: Scope,
    val walletId: String?,
    val ownerKey: String,
    val name: String,
    val normalizedName: String,
    val sortOrder: Int,
    val archived: Boolean,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val icon: CategoryIcon? = null,
)

enum class CategoryIcon {
    FOOD, TRANSPORT, HOME, WORK, MONEY, SHOPPING, HEALTH, GIFT, EDUCATION, TRAVEL, ENTERTAINMENT,
}

@Entity(
    foreignKeys = [ForeignKey(entity = MealMethod::class, parentColumns = ["id"], childColumns = ["mealMethodId"], deferred = true)],
    indices = [Index(value = ["date", "mealSlot"], unique = true), Index("mealMethodId")],
)
data class MealRecord(
    @PrimaryKey val id: String,
    val date: String,
    val mealSlot: MealSlot,
    val plannedAtHome: Boolean,
    val mealMethodId: String?,
    val dishes: String,
    val note: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    primaryKeys = ["mealId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = MealRecord::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE, deferred = true),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], deferred = true),
    ],
    indices = [Index("categoryId")],
)
data class MealPreparationCategory(val mealId: String, val categoryId: String)

@Entity(indices = [Index(value = ["normalizedName", "currencyCode"], unique = true), Index("currencyCode")])
data class Wallet(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val currencyCode: String,
    val currencyScale: Int,
    val openingBalanceMinor: Long,
    val sortOrder: Int,
    val archived: Boolean,
    val separateAccounting: Boolean,
    val incomeCategoryMode: CategoryMode,
    val expenseCategoryMode: CategoryMode,
    val createdAt: String,
    val updatedAt: String,
    @ColumnInfo(defaultValue = "0") val defaultToIncome: Boolean = false,
) {
    fun categoryMode(scope: Scope): CategoryMode = when (scope) {
        Scope.INCOME -> incomeCategoryMode
        Scope.EXPENSE -> expenseCategoryMode
        Scope.PREPARATION -> error("制作分类不属于钱包")
    }
}

@Entity(
    foreignKeys = [
        ForeignKey(entity = Wallet::class, parentColumns = ["id"], childColumns = ["walletId"], deferred = true),
        ForeignKey(entity = Wallet::class, parentColumns = ["id"], childColumns = ["toWalletId"], deferred = true),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], deferred = true),
        ForeignKey(entity = FinancialEntry::class, parentColumns = ["id"], childColumns = ["originalExpenseId"], deferred = true),
    ],
    indices = [Index("walletId"), Index("toWalletId"), Index("categoryId"),
        Index("originalExpenseId"), Index(value = ["date", "time", "createdAt"])],
)
data class FinancialEntry(
    @PrimaryKey val id: String,
    val date: String,
    val time: String,
    val type: EntryType,
    val walletId: String,
    val amountMinor: Long,
    val categoryId: String?,
    val toWalletId: String?,
    val toAmountMinor: Long?,
    val originalExpenseId: String?,
    val note: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity
data class ThemePreferences(
    @PrimaryKey val key: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.PRESET,
    val presetId: String = "apricot",
    val customSeedColor: String = "#C47A4A",
)

@Entity(primaryKeys = ["kind", "sha256"])
data class ImportReceipt(val kind: CsvKind, val sha256: String, val importedAt: String)

@Entity
data class DataState(
    @PrimaryKey val key: Int = 0,
    val installationId: String,
    val datasetId: String,
    val revision: Long = 0,
    val generation: Long = 0,
    val arrayElements: Int,
    val defaultWalletId: String? = null,
)
