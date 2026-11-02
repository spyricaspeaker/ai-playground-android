package com.aiplayground.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PlaygroundScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsCurrentAgentNetworkAndConnectionControls() {
        composeRule.onNodeWithText("AI PLAYGROUND").assertIsDisplayed()
        listOf("ARIA", "NOVA", "ATLAS", "ECHO").forEach { agent ->
            composeRule.onNodeWithText(agent, substring = true).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("NETWORK STATUS").assertIsDisplayed()
        composeRule.onNodeWithText("Ready for your message").assertIsDisplayed()
        composeRule.onNodeWithText("SERVER CONNECTION").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pauseAndResumeUpdateNetworkStatus() {
        composeRule.onNodeWithText("Pause").performScrollTo().performClick()
        composeRule.onNodeWithText("Paused").assertIsDisplayed()

        composeRule.onNodeWithText("Resume").performScrollTo().performClick()
        composeRule.onNodeWithText("Ready for your message").assertIsDisplayed()
    }

    @Test
    fun handshakePanelAuthenticatesMessageAndRejectsReplay() {
        composeRule.onNodeWithText("SPYRICA HANDSHAKE ENGINE")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Run local test").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "Authentication: PASS · Authenticated handshake accepted · spyrica-core-01"
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Replay protection: PASS · Repeated nonce rejected"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingEachAgentCardIsSupported() {
        listOf("ARIA", "NOVA", "ATLAS", "ECHO").forEach { agent ->
            composeRule.onNodeWithText(agent, substring = true)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithText("YOUR MESSAGE").performScrollTo().assertIsDisplayed()
        }
    }
}
