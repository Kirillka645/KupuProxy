package com.kupuproxy.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kupuproxy.app.ui.components.channel.ChannelPromoCard
import com.kupuproxy.app.ui.theme.KupuProxyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelPromoCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dismissHidesCard() {
        var dismissed = false
        composeRule.setContent {
            KupuProxyTheme {
                if (!dismissed) {
                    ChannelPromoCard(
                        onSubscribe = {},
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Скрыть").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun subscribeButtonVisible() {
        composeRule.setContent {
            KupuProxyTheme {
                ChannelPromoCard(onSubscribe = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Подписаться").assertExists()
    }
}
