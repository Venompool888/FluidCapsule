package io.github.venompool888.fluidcapsule

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.venompool888.fluidcapsule.rules.RuleCheckResult
import io.github.venompool888.fluidcapsule.rules.RuleInstallResult
import io.github.venompool888.fluidcapsule.rules.RuleSource
import io.github.venompool888.fluidcapsule.rules.RuleStore
import io.github.venompool888.fluidcapsule.rules.RuleSubscriptionPrefs
import io.github.venompool888.fluidcapsule.rules.RuleTrust
import io.github.venompool888.fluidcapsule.rules.RuleUpdateCoordinator
import io.github.venompool888.fluidcapsule.rules.RuleUpdateGateway
import io.github.venompool888.fluidcapsule.rules.RuleUpdateGatewayProvider
import io.github.venompool888.fluidcapsule.rules.RuleUpdateStateStore
import io.github.venompool888.fluidcapsule.rules.VerifiedManifest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RuleSubscriptionUiTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var fake: FakeGateway

    @Before
    fun setup() {
        context.getSharedPreferences("rule_subscription", 0).edit().clear().commit()
        fake = FakeGateway(available())
        RuleUpdateGatewayProvider.testFactory = { fake }
    }

    @After
    fun cleanup() {
        RuleUpdateGatewayProvider.testFactory = null
        context.getSharedPreferences("rule_subscription", 0).edit().clear().commit()
    }

    @Test
    fun rulesPageShowsDefaultOnAndBundledVersion() {
        fake.offerUpdate = false
        ActivityScenario.launch(MainActivity::class.java).use {
            openRulesSheet()
            composeRule.onNodeWithContentDescription("订阅规则更新").assertIsOn()
            composeRule.onNodeWithText("已安装规则：1", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun availableUpdateShowsOneDialogAndLaterSuppressesRepeat() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { fake.checks.get() >= 1 }
            awaitNodeText("有新的规则更新")
            composeRule.onNodeWithText("有新的规则更新").assertIsDisplayed()
            composeRule.onNodeWithText("稍后").performClick()
            awaitNodeAbsent("有新的规则更新")
            scenario.recreate()
            await { fake.checks.get() >= 2 }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("有新的规则更新").assertDoesNotExist()
            assertEquals(1, fake.dismissals.get())
        }
    }

    @Test
    fun canceledDialogDoesNotReprompt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { fake.checks.get() >= 1 }
            awaitNodeText("有新的规则更新")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitNodeAbsent("有新的规则更新")
            scenario.recreate()
            await { fake.checks.get() >= 2 }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("有新的规则更新").assertDoesNotExist()
            assertEquals(1, fake.dismissals.get())
        }
    }

    @Test
    fun failedInstallDoesNotReprompt() {
        fake.failInstall = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { fake.checks.get() >= 1 }
            awaitNodeText("立即更新")
            composeRule.onNodeWithText("立即更新").performClick()
            await { fake.installs.get() >= 1 }
            scenario.recreate()
            await { fake.checks.get() >= 2 }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("有新的规则更新").assertDoesNotExist()
        }
    }

    @Test
    fun updateTapInstallsExactlyOnce() {
        ActivityScenario.launch(MainActivity::class.java).use {
            await { fake.checks.get() >= 1 }
            awaitNodeText("立即更新")
            composeRule.onNodeWithText("立即更新").performClick()
            await { fake.installs.get() == 1 }
            assertEquals(1, fake.installs.get())
        }
    }

    @Test
    fun signedFixtureInstallsThroughUiAndVerifiedStore() {
        val keys = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider()).generateKeyPair()
        val trust = RuleTrust(keys.public)
        val pack = """{"schemaVersion":1,"version":2,"otpKeywords":[{"id":"synthetic","phrase":"Synthetic secure number"}],"otpBindings":[],"verificationRequests":[],"otpExclusions":[],"linkExclusions":[]}""".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(pack).joinToString("") { "%02x".format(it) }
        val manifest = """{"schemaVersion":1,"version":2,"minAppVersionCode":38,"packSha256":"$hash","notes":"Synthetic test"}""".toByteArray()
        val signer = Signature.getInstance("Ed25519", BouncyCastleProvider())
        signer.initSign(keys.private)
        signer.update(manifest)
        val signature = Base64.getEncoder().encode(signer.sign())
        val dir = File(context.filesDir, "test-rule-ui-${UUID.randomUUID()}")
        val store = RuleStore(context, trust, dir, 38)
        val source = object : RuleSource {
            override fun fetchManifest() = manifest to signature
            override fun fetchPack(version: Int) = pack
        }
        val state = object : RuleUpdateStateStore {
            override var subscriptionEnabled = true
            override var lastSuccessfulCheckMillis = 0L
            override var manifest: ByteArray? = null
            override var signature: ByteArray? = null
            override var dismissedVersion = 0
            override var restoredVersion = 0
        }
        RuleUpdateGatewayProvider.testFactory = {
            RuleUpdateCoordinator(
                source,
                trust,
                { store.loadLatest()?.pack?.version ?: 1 },
                { store.install(it) },
                { store.restoreBuiltIn() },
                state,
                38,
            )
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                await { state.lastSuccessfulCheckMillis > 0 }
                awaitNodeText("立即更新")
                composeRule.onNodeWithText("立即更新").performClick()
                await { store.loadLatest()?.pack?.version == 2 }
                assertEquals("Synthetic secure number", store.loadLatest()!!.pack.otpKeywords.single().phrase)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun subscriptionOffSkipsAutomaticButManualCheckWorks() {
        RuleSubscriptionPrefs(context).subscriptionEnabled = false
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(300)
            assertEquals(0, fake.checks.get())
            openRulesSheet()
            composeRule.onNodeWithText("检查规则更新").performClick()
            await { fake.checks.get() == 1 }
            assertTrue(fake.lastForce)
        }
    }

    private fun openRulesSheet() {
        composeRule.onNodeWithText("应用与规则").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("rule_subscription_pill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("rule_subscription_pill").performClick()
        awaitNodeText("订阅规则更新")
    }

    private fun awaitNodeText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitNodeAbsent(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for UI state")
    }

    private fun available(): VerifiedManifest {
        val keys = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider()).generateKeyPair()
        val manifest = """{"schemaVersion":1,"version":2,"minAppVersionCode":38,"packSha256":"${"0".repeat(64)}","notes":"test"}""".toByteArray()
        val signer = Signature.getInstance("Ed25519", BouncyCastleProvider())
        signer.initSign(keys.private)
        signer.update(manifest)
        return RuleTrust(keys.public).verifyManifest(manifest, Base64.getEncoder().encode(signer.sign()))
    }

    private class FakeGateway(private val available: VerifiedManifest) : RuleUpdateGateway {
        val checks = AtomicInteger()
        val installs = AtomicInteger()
        val dismissals = AtomicInteger()
        @Volatile var lastForce = false
        @Volatile var offerUpdate = true
        @Volatile var failInstall = false

        override fun check(force: Boolean): RuleCheckResult {
            lastForce = force
            checks.incrementAndGet()
            return if (offerUpdate) {
                RuleCheckResult.Available(available, dismissals.get() == 0)
            } else {
                RuleCheckResult.UpToDate(1)
            }
        }

        override fun install(available: VerifiedManifest): RuleInstallResult {
            installs.incrementAndGet()
            return if (failInstall) RuleInstallResult.Failed else RuleInstallResult.Installed(2)
        }

        override fun dismiss(version: Int) {
            dismissals.incrementAndGet()
        }

        override fun restoreBuiltIn() = Unit
    }
}
