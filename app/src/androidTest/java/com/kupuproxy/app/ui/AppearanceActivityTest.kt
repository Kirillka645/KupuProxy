package com.kupuproxy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kupuproxy.app.R
import org.junit.Rule
import org.junit.Test

class AppearanceActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<AppearanceActivity>()

    @Test
    fun showsPreviewAndResetAction() {
        val activity = composeRule.activity
        composeRule
            .onNodeWithText(activity.getString(R.string.appearance_preview_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(activity.getString(R.string.appearance_reset))
            .assertIsDisplayed()
    }
}
