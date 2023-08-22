package com.ivy.wallet.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.frp.sumOfSuspend
import com.ivy.frp.test.TestIdlingResource
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.budget.BudgetsAct
import com.ivy.wallet.domain.action.category.CategoriesAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.action.transaction.HistoryTrnsAct
import com.ivy.wallet.domain.data.TransactionType
import com.ivy.wallet.domain.data.core.Account
import com.ivy.wallet.domain.data.core.Budget
import com.ivy.wallet.domain.data.core.Category
import com.ivy.wallet.domain.data.core.Transaction
import com.ivy.wallet.domain.deprecated.logic.BudgetCreator
import com.ivy.wallet.domain.deprecated.logic.model.CreateBudgetData
import com.ivy.wallet.domain.deprecated.sync.item.BudgetSync
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.wallet.domain.pure.transaction.trnCurrency
import com.ivy.wallet.io.persistence.SharedPrefs
import com.ivy.wallet.io.persistence.dao.AccountDao
import com.ivy.wallet.io.persistence.dao.BudgetDao
import com.ivy.wallet.io.persistence.dao.CategoryDao
import com.ivy.wallet.io.persistence.dao.SettingsDao
import com.ivy.wallet.ui.IvyWalletCtx
import com.ivy.wallet.ui.budget.model.DisplayBudget
import com.ivy.wallet.ui.onboarding.model.TimePeriod
import com.ivy.wallet.ui.onboarding.model.toCloseTimeRange
import com.ivy.wallet.utils.dateNowUTC
import com.ivy.wallet.utils.getDefaultFIATCurrency
import com.ivy.wallet.utils.ioThread
import com.ivy.wallet.utils.isNotNullOrBlank
import com.ivy.wallet.utils.readOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val settingsDao: SettingsDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val budgetCreator: BudgetCreator,
    private val budgetSync: BudgetSync,
    private val ivyContext: IvyWalletCtx,
    private val accountsAct: AccountsAct,
    private val categoriesAct: CategoriesAct,
    private val budgetsAct: BudgetsAct,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val historyTrnsAct: HistoryTrnsAct,
    private val exchangeAct: ExchangeAct
) : ViewModel() {

    private val _timeRange = MutableStateFlow(ivyContext.selectedPeriod.toRange(1))
    val timeRange = _timeRange.readOnly()

    private val _period = MutableStateFlow(ivyContext.selectedPeriod)
    val period = _period.readOnly()

    private val _baseCurrencyCode = MutableStateFlow(getDefaultFIATCurrency().currencyCode)
    val baseCurrencyCode = _baseCurrencyCode.readOnly()

    private val _budgets = MutableStateFlow<List<DisplayBudget>>(emptyList())
    val budgets = _budgets.readOnly()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.readOnly()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts = _accounts.readOnly()

    private val _categoryBudgetsTotal = MutableStateFlow(0.0)
    val categoryBudgetsTotal = _categoryBudgetsTotal.readOnly()

    private val _appBudgetMax = MutableStateFlow(0.0)
    val appBudgetMax = _appBudgetMax.readOnly()

    fun start(period: TimePeriod = ivyContext.selectedPeriod) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            _categories.value = categoriesAct(Unit)

            val accounts = accountsAct(Unit)
            _accounts.value = accounts

            val baseCurrency = baseCurrencyAct(Unit)
            _baseCurrencyCode.value = baseCurrency

            _period.value = period
            _timeRange.value = period.toRange(ivyContext.startDayOfMonth)

            val budgets = budgetsAct(Unit)

            _appBudgetMax.value = budgets
                .filter { it.categoryIdsSerialized.isNullOrBlank() }
                .maxOfOrNull { it.amount } ?: 0.0

            _categoryBudgetsTotal.value = budgets
                .filter { it.categoryIdsSerialized.isNotNullOrBlank() }
                .sumOf { it.amount }

            _budgets.value = ioThread {
                budgets.map {
                    DisplayBudget(
                        budget = it,
                        spentAmount = calculateSpentAmount(
                            budget = it,
                            transactions = historyTrnsAct(_timeRange.value.toCloseTimeRange()),
                            accounts = accounts,
                            baseCurrencyCode = baseCurrency
                        )
                    )
                }
            }

            TestIdlingResource.decrement()
        }
    }

    fun setPeriod(period: TimePeriod) {
        start(period = period)
    }

    fun previousPeriod() {
        val month = period.value.month
        val year = period.value.year
        if (month != null) {
            start(
                period = month.incrementMonthPeriod(ivyContext, -1L, year ?: dateNowUTC().year),
            )
        }
        else if (year != null) {
            start( period = TimePeriod(year = year - 1))
        }
    }

    fun nextPeriod() {
        val month = period.value.month
        val year = period.value.year
        if (month != null) {
            start(
                period = month.incrementMonthPeriod(ivyContext, 1L, year ?: dateNowUTC().year),
            )
        }
        else if (year != null) {
            start( period = TimePeriod(year = year + 1))
        }
    }

    private suspend fun calculateSpentAmount(
        budget: Budget,
        transactions: List<Transaction>,
        baseCurrencyCode: String,
        accounts: List<Account>
    ): Double {
        //TODO: Re-work this by creating an FPAction for it
        val accountsFilter = budget.parseAccountIds()
        val categoryFilter = budget.parseCategoryIds()

        return transactions
            .filter { accountsFilter.isEmpty() || accountsFilter.contains(it.accountId) }
            .filter { categoryFilter.isEmpty() || categoryFilter.contains(it.categoryId) }
            .sumOfSuspend {
                when (it.type) {
                    TransactionType.INCOME -> {
                        //decrement spent amount if it's not global budget
                        0.0 //ignore income
//                        if (categoryFilter.isEmpty()) 0.0 else -amountBaseCurrency
                    }
                    TransactionType.EXPENSE -> {
                        //increment spent amount
                        exchangeAct(
                            ExchangeAct.Input(
                                data = ExchangeData(
                                    baseCurrency = baseCurrencyCode,
                                    fromCurrency = trnCurrency(it, accounts, baseCurrencyCode)
                                ),
                                amount = it.amount
                            )
                        ).orNull()?.toDouble() ?: 0.0
                    }
                    TransactionType.TRANSFER -> {
                        //ignore transfers for simplicity
                        0.0
                    }
                }
            }
    }

    fun createBudget(data: CreateBudgetData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            budgetCreator.createBudget(data) {
                start()
            }

            TestIdlingResource.decrement()
        }
    }

    fun editBudget(budget: Budget) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            budgetCreator.editBudget(budget) {
                start()
            }

            TestIdlingResource.decrement()
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            budgetCreator.deleteBudget(budget) {
                start()
            }

            TestIdlingResource.decrement()
        }
    }


    fun reorder(newOrder: List<DisplayBudget>) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                newOrder.forEachIndexed { index, item ->
                    budgetDao.save(
                        item.budget.toEntity().copy(
                            orderId = index.toDouble(),
                            isSynced = false
                        )
                    )
                }
            }
            start()

            ioThread {
                budgetSync.sync()
            }

            TestIdlingResource.decrement()
        }
    }
}