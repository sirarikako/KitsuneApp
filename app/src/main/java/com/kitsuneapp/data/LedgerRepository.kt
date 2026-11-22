package com.kitsuneapp.data

import java.math.BigInteger
import java.util.Currency

data class WalletDraft(
    val id: String?,
    val name: String,
    val currencyCode: String,
    val openingBalance: String,
    val sortOrder: Int,
    val archived: Boolean,
    val separateAccounting: Boolean,
)

data class EntryDraft(
    val id: String?,
    val date: String,
    val time: String,
    val type: EntryType,
    val walletId: String?,
    val amount: String,
    val categoryId: String?,
    val newCategory: String,
    val toWalletId: String?,
    val toAmount: String,
    val originalExpenseId: String?,
    val note: String,
)

val availableCurrencies: List<Currency> by lazy {
    Currency.getAvailableCurrencies().filter { it.defaultFractionDigits in 0..4 }.sortedBy { it.currencyCode }
}

suspend fun Repository.saveWallet(draft: WalletDraft) = change {
    val previous = draft.id?.let { requireNotNull(dao.wallet(it)) { "钱包已不存在" } }
    val id = previous?.id ?: newId()
    val name = cleanName(draft.name)
    val normalized = normalizedName(name)
    require(draft.sortOrder >= 0) { "排序必须为非负整数" }
    val code = draft.currencyCode
    val peer = dao.peerWallet(code, id)
    val scale = if (previous != null && code == previous.currencyCode) previous.currencyScale
        else peer?.currencyScale ?: requireNotNull(availableCurrencies.find { it.currencyCode == code }) { "请选择币种" }.defaultFractionDigits
    validateCurrency(code, scale)
    require(dao.walletByName(normalized, code)?.id.let { it == null || it == id }) { "已有同名同币种的钱包" }
    if (previous != null && (code != previous.currencyCode || scale != previous.currencyScale)) {
        require(dao.walletUses(id) == 0) { "已有流水的钱包不能更改币种" }
    }
    val now = utcNow()
    val wallet = Wallet(id, name, normalized, code, scale, decimalMinor(draft.openingBalance, scale, positive = false),
        draft.sortOrder, draft.archived, draft.separateAccounting, previous?.incomeCategoryMode ?: CategoryMode.DEFAULT,
        previous?.expenseCategoryMode ?: CategoryMode.DEFAULT, previous?.createdAt ?: now, now)
    if (previous != null && wallet.copy(updatedAt = previous.updatedAt) == previous) return@change false
    dao.put(wallet)
    true
}

suspend fun Repository.removeWallet(id: String) = change {
    require(dao.walletUses(id) == 0) { "已有流水的钱包请归档" }
    require(dao.ownedCategories(id) == 0) { "请先删除钱包专属分类，或归档钱包" }
    dao.deleteWallet(id) > 0
}

suspend fun Repository.setCategoryMode(walletId: String, scope: Scope, mode: CategoryMode) = change {
    val wallet = requireNotNull(dao.wallet(walletId)) { "钱包已不存在" }
    if (wallet.categoryMode(scope) == mode) return@change false
    dao.put(when (scope) {
        Scope.INCOME -> wallet.copy(incomeCategoryMode = mode, updatedAt = utcNow())
        Scope.EXPENSE -> wallet.copy(expenseCategoryMode = mode, updatedAt = utcNow())
        Scope.PREPARATION -> error("制作分类不属于钱包")
    })
    true
}

suspend fun Repository.saveEntry(draft: EntryDraft) = change {
    val original = draft.id?.let { requireNotNull(dao.entry(it)) { "此账目已不存在" } }
    require(original == null || original.type == draft.type) { "已有账目不能更改类型" }
    val wallet = draft.walletId?.let { dao.wallet(it) } ?: throw IllegalArgumentException("请选择钱包")
    require(!wallet.archived || wallet.id == original?.walletId ||
        (draft.type == EntryType.REFUND && draft.originalExpenseId?.let { dao.entry(it)?.walletId } == wallet.id)) {
        "请选择未归档的钱包"
    }
    val amount = decimalMinor(draft.amount, wallet.currencyScale)
    val scope = draft.type.scope
    val category = if (scope != null) {
        val keepOriginal = draft.newCategory.isBlank() && original != null && original.walletId == wallet.id &&
            original.categoryId == draft.categoryId
        val selected = if (draft.newCategory.isNotBlank()) {
            val owner = wallet.id.takeIf { wallet.categoryMode(scope) == CategoryMode.CUSTOM }
            resolveCategory(scope, owner, draft.newCategory)
        } else draft.categoryId?.let { dao.category(it) } ?: throw IllegalArgumentException("请选择分类")
        require(keepOriginal || allowedCategory(selected, wallet, scope)) { "请选择当前钱包可用的分类" }
        selected.id
    } else null

    val target = if (draft.type == EntryType.TRANSFER) {
        draft.toWalletId?.let { dao.wallet(it) } ?: throw IllegalArgumentException("请选择到账钱包")
    } else null
    if (target != null) {
        require(target.id != wallet.id) { "转出与到账钱包不能相同" }
        require(!target.archived || target.id == original?.toWalletId) { "请选择未归档的到账钱包" }
    }
    val targetAmount = target?.let {
        if (it.currencyCode == wallet.currencyCode) amount else decimalMinor(draft.toAmount, it.currencyScale)
    }
    val now = utcNow()
    val entry = FinancialEntry(original?.id ?: newId(), validateDate(draft.date), validateTime(draft.time), draft.type,
        wallet.id, amount, category, target?.id, targetAmount,
        draft.originalExpenseId.takeIf { draft.type == EntryType.REFUND }, draft.note, original?.createdAt ?: now, now)
    validateField("备注", entry.note)
    when (entry.type) {
        EntryType.REFUND -> {
            val expense = entry.originalExpenseId?.let { dao.entry(it) }
            requireNotNull(expense) { "原支出不存在" }
            require(expense.type == EntryType.EXPENSE) { "原支出不存在" }
            require(wallet.currencyCode == dao.wallet(expense.walletId)?.currencyCode) { "退款与原支出必须为同一币种" }
            require(entry.amountMinor.toBigInteger() + refunded(expense.id, entry.id) <= expense.amountMinor.toBigInteger()) {
                "退款累计超过原支出金额"
            }
        }
        EntryType.EXPENSE -> {
            val related = dao.refunds(entry.id)
            require(related.all { dao.wallet(it.walletId)?.currencyCode == wallet.currencyCode }) { "原支出的币种与退款不兼容" }
            require(related.fold(BigInteger.ZERO) { sum, refund -> sum + refund.amountMinor.toBigInteger() } <= amount.toBigInteger()) {
                "金额不能小于已退款合计"
            }
        }
        else -> Unit
    }
    if (original != null && entry.copy(updatedAt = original.updatedAt) == original) return@change false
    dao.put(entry)
    true
}

private suspend fun Repository.refunded(expenseId: String, exceptId: String) = dao.refunds(expenseId)
    .filterNot { it.id == exceptId }.fold(BigInteger.ZERO) { sum, entry -> sum + entry.amountMinor.toBigInteger() }

suspend fun Repository.removeEntry(id: String) = change {
    require(dao.refunds(id).isEmpty()) { "请先删除关联退款" }
    dao.deleteEntry(id) > 0
}
