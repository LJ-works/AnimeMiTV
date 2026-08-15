package com.ljworks.animemitv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ExitConfirmDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.onNodeWithTag("exit-confirm-dismiss").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertFalse(visible)
    }
}
