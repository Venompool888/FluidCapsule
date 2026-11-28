package io.github.venompool888.fluidcapsule.settings

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.venompool888.fluidcapsule.ui.screens.AppsAndRulesContent
import io.github.venompool888.fluidcapsule.ui.state.AppsCatalogController
import io.github.venompool888.fluidcapsule.ui.theme.FluidCapsuleTheme
import io.github.venompool888.fluidcapsule.ui.theme.configureEdgeToEdgeForTheme
import io.github.venompool888.fluidcapsule.ui.theme.isDarkThemeForContext

class WhitelistViewModel(application: Application) : AndroidViewModel(application) {
    val appsCatalog = AppsCatalogController(application, viewModelScope)
}

class WhitelistActivity : ComponentActivity() {
    private val viewModel: WhitelistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "通知来源白名单"
        configureEdgeToEdgeForTheme(this, isDarkThemeForContext(this))

        setContent {
            val appsState by viewModel.appsCatalog.state.collectAsStateWithLifecycle()
            FluidCapsuleTheme(
                themeMode = UserSettings.themeMode(this),
                dynamicColor = UserSettings.dynamicColorEnabled(this),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ) { innerPadding ->
                    AppsAndRulesContent(
                        title = "通知来源白名单",
                        appsState = appsState,
                        ruleState = null,
                        bottomContentPadding = innerPadding.calculateBottomPadding(),
                        showBackButton = true,
                        onNavigateBack = { finish() },
                        onOpenRuleSheet = null,
                        onSearchQueryChange = viewModel.appsCatalog::setSearchQuery,
                        onFilterChange = viewModel.appsCatalog::setFilter,
                        onToggleApp = viewModel.appsCatalog::setAppEnabled,
                        onRefreshAndSort = viewModel.appsCatalog::refreshAndSortApps,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.appsCatalog.refreshRulesInPlace()
    }
}
