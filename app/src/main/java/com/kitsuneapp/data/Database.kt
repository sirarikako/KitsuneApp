package com.kitsuneapp.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Database(
    entities = [MealMethod::class, Category::class, MealRecord::class, MealPreparationCategory::class,
        Wallet::class, FinancialEntry::class, ThemePreferences::class, ImportReceipt::class, DataState::class],
    version = 1,
    exportSchema = true,
)
abstract class KitsuneDatabase : RoomDatabase() {
    abstract fun records(): RecordsDao
}

@Dao
interface RecordsDao {
    @Query("SELECT * FROM DataState WHERE `key` = 0") suspend fun state(): DataState?
    @Upsert suspend fun put(state: DataState)
    @Query("SELECT * FROM ThemePreferences WHERE `key` = 0") suspend fun theme(): ThemePreferences?
    @Upsert suspend fun put(theme: ThemePreferences)

    @Query("SELECT * FROM MealMethod ORDER BY sortOrder, id") suspend fun methods(): List<MealMethod>
    @Query("SELECT * FROM MealMethod WHERE id = :id") suspend fun method(id: String): MealMethod?
    @Query("SELECT * FROM MealMethod WHERE normalizedName = :name") suspend fun methodByName(name: String): MealMethod?
    @Upsert suspend fun put(method: MealMethod)
    @Insert suspend fun insert(methods: List<MealMethod>)
    @Query("SELECT COUNT(*) FROM MealRecord WHERE mealMethodId = :id") suspend fun methodUses(id: String): Int
    @Query("DELETE FROM MealMethod WHERE id = :id") suspend fun deleteMethod(id: String): Int

    @Query("SELECT * FROM Category ORDER BY sortOrder, id") suspend fun categories(): List<Category>
    @Query("SELECT * FROM Category WHERE id = :id") suspend fun category(id: String): Category?
    @Query("SELECT * FROM Category WHERE scope = :scope AND ownerKey = :owner AND normalizedName = :name")
    suspend fun categoryByName(scope: Scope, owner: String, name: String): Category?
    @Query("SELECT MAX(sortOrder) FROM Category WHERE scope = :scope AND ownerKey = :owner")
    suspend fun maxCategoryOrder(scope: Scope, owner: String): Int?
    @Upsert suspend fun put(category: Category)
    @Insert suspend fun insertCategories(categories: List<Category>)
    @Query("SELECT (SELECT COUNT(*) FROM MealPreparationCategory WHERE categoryId = :id) + (SELECT COUNT(*) FROM FinancialEntry WHERE categoryId = :id)")
    suspend fun categoryUses(id: String): Int
    @Query("SELECT COUNT(*) FROM Category WHERE walletId = :id") suspend fun ownedCategories(id: String): Int
    @Query("DELETE FROM Category WHERE id = :id") suspend fun deleteCategory(id: String): Int

    @Query("SELECT * FROM MealRecord ORDER BY date DESC, mealSlot, id") suspend fun meals(): List<MealRecord>
    @Query("SELECT * FROM MealRecord WHERE id = :id") suspend fun meal(id: String): MealRecord?
    @Query("SELECT * FROM MealRecord WHERE date = :date AND mealSlot = :slot")
    suspend fun mealAt(date: String, slot: MealSlot): MealRecord?
    @Query("SELECT categoryId FROM MealPreparationCategory WHERE mealId = :id ORDER BY categoryId")
    suspend fun preparationIds(id: String): List<String>
    @Query("SELECT * FROM MealPreparationCategory ORDER BY mealId, categoryId") suspend fun preparation(): List<MealPreparationCategory>
    @Query("DELETE FROM MealPreparationCategory WHERE mealId = :id") suspend fun deletePreparation(id: String): Int
    @Insert suspend fun insertPreparation(links: List<MealPreparationCategory>)
    @Upsert suspend fun put(meal: MealRecord)
    @Query("DELETE FROM MealRecord WHERE id = :id") suspend fun deleteMeal(id: String): Int

    @Query("SELECT * FROM Wallet ORDER BY sortOrder, id") suspend fun wallets(): List<Wallet>
    @Query("SELECT * FROM Wallet WHERE id = :id") suspend fun wallet(id: String): Wallet?
    @Query("SELECT * FROM Wallet WHERE normalizedName = :name AND currencyCode = :code")
    suspend fun walletByName(name: String, code: String): Wallet?
    @Query("SELECT * FROM Wallet WHERE currencyCode = :code AND id != :except LIMIT 1")
    suspend fun peerWallet(code: String, except: String): Wallet?
    @Query("SELECT COUNT(*) FROM FinancialEntry WHERE walletId = :id OR toWalletId = :id")
    suspend fun walletUses(id: String): Int
    @Upsert suspend fun put(wallet: Wallet)
    @Query("DELETE FROM Wallet WHERE id = :id") suspend fun deleteWallet(id: String): Int

    @Query("SELECT * FROM FinancialEntry ORDER BY date DESC, time DESC, createdAt DESC, id")
    suspend fun entries(): List<FinancialEntry>
    @Query("SELECT * FROM FinancialEntry WHERE id = :id") suspend fun entry(id: String): FinancialEntry?
    @Query("SELECT * FROM FinancialEntry WHERE originalExpenseId = :id")
    suspend fun refunds(id: String): List<FinancialEntry>
    @Upsert suspend fun put(entry: FinancialEntry)
    @Query("DELETE FROM FinancialEntry WHERE id = :id") suspend fun deleteEntry(id: String): Int

    @Query("SELECT * FROM ImportReceipt ORDER BY kind, sha256") suspend fun receipts(): List<ImportReceipt>
    @Query("SELECT * FROM ImportReceipt WHERE kind = :kind AND sha256 = :sha") suspend fun receipt(kind: CsvKind, sha: String): ImportReceipt?
    @Insert suspend fun insert(receipt: ImportReceipt)

    @Query("SELECT (SELECT COUNT(*) FROM MealMethod) + (SELECT COUNT(*) FROM Category) + (SELECT COUNT(*) FROM MealRecord) + (SELECT COUNT(*) FROM MealPreparationCategory) + (SELECT COUNT(*) FROM Wallet) + (SELECT COUNT(*) FROM FinancialEntry) + (SELECT COUNT(*) FROM ImportReceipt)")
    suspend fun countElements(): Int
}
