package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.PolicyGrantDialog
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.SecureTextField
import yancey.chelper.ui.designsystem.widget.Switch
import yancey.chelper.ui.platform.ClientInteractionHost

class SharedComponentsTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    @Test
    fun disabledButtonsCannotBeActivatedAndSwitchExposesItsState() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Column {
                    Switch(checked.value, { checked.value = it })
                    Button("提交", enabled = enabled.value) { clicks++ }
                }
            }
        }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("提交").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, clicks)
            enabled.value = true
        }
        compose.onNodeWithText("提交").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun passwordUsesSecureSemanticsWithoutRewritingItsValue() {
        val password = TextFieldState("secret")
        val visible = mutableStateOf(false)
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                SecureTextField(state = password, visible = visible.value)
            }
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.runOnIdle { password.edit { selection = TextRange(0, length) } }
        compose
            .onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CopyText))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CutText))
        compose.runOnIdle { visible.value = true }
        compose.runOnIdle { assertEquals("secret", password.text.toString()) }
    }

    @Test
    fun policyConfirmationIsDisabledUntilTheSharedCheckboxIsChecked() {
        var confirmations = 0
        compose.setContent {
            navigation.Content {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost { PolicyGrantDialog(onConfirm = { confirmations++ }) }
                }
            }
        }
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.onNodeWithText("确认").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }
}
