package dev.chuds.stillcontacts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS,
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Permissions must be granted before MainActivity starts, otherwise the list
    // screen renders its permission prompt instead of the contacts list.
    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun listScreen_rendersStaticHeaderAndFooterVerb() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("CONTACTS").assertIsDisplayed()
        composeRule.onNodeWithText("settings").assertIsDisplayed()
    }
}
