package com.ljworks.animemitv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExitConfirmDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cancelIsFocusedAndDismissesTheExitConfirmation() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            AnimeMiTVTheme {
                if (visible) {
                    ExitConfirmDialog(
                        onDismiss = { visible = false },
                        onConfirm = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("exit-confirm-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("exit-confirm-dismiss").assertIsFocused()

        composeRule.onNodeWithTag("exit-confirm-dismiss").performClick()

        composeRule.onNodeWithTag("exit-confirm-dialog").assertDoesNotExist()
    }
}
