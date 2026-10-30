package com.aiplayground.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        composeRule.onNodeWithText("ARIA: Creating new ideas").assertIsDisplayed()
        composeRule.onNodeWithText("NOVA: Exploring available spaces").assertIsDisplayed()

        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Ready to explore").assertIsDisplayed()

        composeRule.onNodeWithText("Sleep").performClick()
        composeRule.onNodeWithText("Sleeping").assertIsDisplayed()
    }
}
