package uk.elizabeth.aac

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.elizabeth.aac.core.touch.SelectOn

/** The main screen, driven through the real touch layer and the accessibility actions. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    @Before
    fun waitForStart() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("SPEAK")).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread {
            vm.onClear()
            vm.selectTab("cat-needs")
            vm.updateSettings { it.copy(touch = it.touch.copy(selectOn = SelectOn.RELEASE, minHoldMs = 80, repeatGuardMs = 450)) }
        }
        rule.waitForIdle()
    }

    private fun message(text: String) = hasContentDescription("Message: $text", substring = true)

    private fun hold(label: String, ms: Long) {
        rule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            advanceEventTime(ms)
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aDeliberatePressAddsThePhrase() {
        hold("I'm in pain", 200)
        rule.onNode(message("I'm in pain")).assertExists()
    }

    @Test
    fun aBriefBrushIsIgnored() {
        hold("I'm tired", 20)
        rule.onNode(message("I'm tired")).assertDoesNotExist()
    }

    @Test
    fun undoRemovesAWrongPress() {
        hold("I'm too hot", 200)
        rule.onNode(message("I'm too hot")).assertExists()
        rule.onNodeWithContentDescription("Undo").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNode(message("I'm too hot")).assertDoesNotExist()
    }

    @Test
    fun buttonsWorkThroughAccessibilityServices() {
        // TalkBack and Switch Access use the accessibility click action, not touches.
        rule.onNodeWithContentDescription("I'm too cold").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNode(message("I'm too cold")).assertExists()
    }

    @Test
    fun keyboardAndPrediction() {
        rule.runOnUiThread { vm.selectTab(Tabs.KEYBOARD) }
        rule.waitForIdle()
        for (key in listOf("t", "e")) rule.onNodeWithContentDescription(key).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNode(message("Te")).assertExists()
        rule.onNodeWithContentDescription("tea").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNode(message("Tea")).assertExists()
    }
}
