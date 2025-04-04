package io.horizontalsystems.bankwallet.modules.createaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.IAccountFactory
import io.horizontalsystems.bankwallet.core.IAccountManager
import io.horizontalsystems.bankwallet.core.managers.PassphraseValidator
import io.horizontalsystems.bankwallet.core.managers.WalletActivator
import io.horizontalsystems.bankwallet.core.managers.WordsManager
import io.horizontalsystems.bankwallet.core.providers.PredefinedBlockchainSettingsProvider
import io.horizontalsystems.bankwallet.core.providers.Translator
import io.horizontalsystems.bankwallet.entities.Account
import io.horizontalsystems.bankwallet.entities.AccountOrigin
import io.horizontalsystems.bankwallet.entities.AccountType
import io.horizontalsystems.bankwallet.entities.DataState
import io.horizontalsystems.bankwallet.entities.normalizeNFKD
import io.horizontalsystems.bankwallet.modules.createaccount.CreateAccountModule.Kind.Mnemonic12
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType

class CreateAccountViewModel(
    private val accountFactory: IAccountFactory,
    private val wordsManager: WordsManager,
    private val accountManager: IAccountManager,
    private val walletActivator: WalletActivator,
    private val passphraseValidator: PassphraseValidator,
    private val predefinedBlockchainSettingsProvider: PredefinedBlockchainSettingsProvider,
) : ViewModel() {

    private var passphrase = ""
    private var passphraseConfirmation = ""

    val mnemonicKinds = CreateAccountModule.Kind.values().toList()

    val defaultAccountName = accountFactory.getNextAccountName()
    var accountName: String = defaultAccountName
        get() = field.ifBlank { defaultAccountName }
        private set

    var selectedKind: CreateAccountModule.Kind = Mnemonic12
        private set

    var passphraseEnabled by mutableStateOf(false)
        private set

    var passphraseConfirmState by mutableStateOf<DataState.Error?>(null)
        private set

    var passphraseState by mutableStateOf<DataState.Error?>(null)
        private set

    var success by mutableStateOf<AccountType?>(null)
        private set

    fun createAccount() {
        if (passphraseEnabled && passphraseIsInvalid()) {
            return
        }

        val accountType = mnemonicAccountType(selectedKind.wordsCount)
        val account = accountFactory.account(
            accountName,
            accountType,
            AccountOrigin.Created,
            false,
            false,
        )

        accountManager.save(account)
        activateDefaultWallets(account)
        predefinedBlockchainSettingsProvider.prepareNew(account, BlockchainType.Zcash)
        success = accountType
    }

    fun onChangeAccountName(name: String) {
        accountName = name
    }

    fun onChangePassphrase(v: String) {
        if (passphraseValidator.containsValidCharacters(v)) {
            passphraseState = null
            passphrase = v
        } else {
            passphraseState = DataState.Error(
                Exception(
                    Translator.getString(R.string.CreateWallet_Error_PassphraseForbiddenSymbols)
                )
            )
        }
    }

    fun onChangePassphraseConfirmation(v: String) {
        passphraseConfirmState = null
        passphraseConfirmation = v
    }

    fun setMnemonicKind(kind: CreateAccountModule.Kind) {
        selectedKind = kind
    }

    fun setPassphraseEnabledState(enabled: Boolean) {
        passphraseEnabled = enabled
        if (!enabled) {
            passphrase = ""
            passphraseConfirmation = ""
        }
    }

    fun onSuccessMessageShown() {
        success = null
    }

    private fun passphraseIsInvalid(): Boolean {
        if (passphraseState is DataState.Error) {
            return true
        }

        if (passphrase.isBlank()) {
            passphraseState = DataState.Error(
                Exception(
                    Translator.getString(R.string.CreateWallet_Error_EmptyPassphrase)
                )
            )
            return true
        }
        if (passphrase != passphraseConfirmation) {
            passphraseConfirmState = DataState.Error(
                Exception(
                    Translator.getString(R.string.CreateWallet_Error_InvalidConfirmation)
                )
            )
            return true
        }
        return false
    }

    private fun activateDefaultWallets(account: Account) {
        val tokenQueries = listOfNotNull(
            TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)),
            TokenQuery(BlockchainType.Ethereum, TokenType.Native),
            TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native),
            TokenQuery(BlockchainType.Tron, TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")),//USDT(TRC20)
            TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0x55d398326f99059ff775485246999027b3197955")), //USDT(bep20)
            TokenQuery(BlockchainType.Polygon, TokenType.Native),
            TokenQuery(BlockchainType.Tron, TokenType.Native),
            TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7")),//USDT(erc20)
            TokenQuery(BlockchainType.Base, TokenType.Eip20("0x833589fcd6edb6e08f4c7c32d4f71b54bda02913")),//USDC(Base)
        )
        walletActivator.activateWallets(account, tokenQueries)
    }

    private fun mnemonicAccountType(wordCount: Int): AccountType {
        // A new account can be created only using an English wordlist and limited chars in the passphrase.
        // Despite it, we add text normalizing.
        // It is to avoid potential issues if we allow non-English wordlists on account creation.
        val words = wordsManager.generateWords(wordCount).map { it.normalizeNFKD() }
        return AccountType.Mnemonic(words, passphrase.normalizeNFKD())
    }

}
