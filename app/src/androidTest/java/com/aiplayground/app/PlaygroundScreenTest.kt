package com.aiplayground.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PlaygroundScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreenShowsAgentsAndPlaygroundAreas() {
        composeRule.onNodeWithText("AI PLAYGROUND").assertIsDisplayed()
        composeRule.onNodeWithText("ARIA").assertIsDisplayed()
        composeRule.onNodeWithText("NOVA").assertIsDisplayed()
        composeRule.onNodeWithText("ATLAS").assertIsDisplayed()
        composeRule.onNodeWithText("ECHO").assertIsDisplayed()
        composeRule.onNodeWithText("PLAYGROUND AREAS").assertIsDisplayed()
        composeRule.onNodeWithText("Ready to explore").assertIsDisplayed()
    }

    @Test
    fun startPauseAndSleepButtonsUpdateStatus() {
        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithText("Agents active").assertIsDisplayed()
        composeRule.onNodeWithText("AGENT ACTIVITY").assertIsDisplayed()

        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Ready to explore").assertIsDisplayed()

        composeRule.onNodeWithText("Sleep").performClick()
        composeRule.onNodeWithText("Sleeping").assertIsDisplayed()
    }

    @Test
    fun eachAgentOpensItsOwnWorkspace() {
        listOf("ARIA", "NOVA", "ATLAS", "ECHO").forEach { agent ->
            composeRule.onNodeWithText(agent).performClick()
            composeRule.onNodeWithText("$agent WORKSPACE").assertIsDisplayed()
            composeRule.onNodeWithText("OPENAI-POWERED AGENT").assertIsDisplayed()
            composeRule.onNodeWithText("Send to $agent").assertIsDisplayed()
            composeRule.onNodeWithText("Close workspace").performClick()
        }
    }
}
