package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.ChatMessage
import com.example.ui.ChatMessageBubble
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleMessage = ChatMessage(
        id = 1L,
        senderAddress = "mock-alice-device",
        senderName = "Alice (Mesh Peer)",
        receiverAddress = "self",
        messageText = com.example.data.CryptoUtils.encrypt("Hey teammate! Checked the airwave locks, we are fully encrypted.", "1234"),
        isIncoming = true,
        encryptionKeyUsed = "1234",
        deliveryStatus = "READ"
    )
    
    composeTestRule.setContent { 
        MyApplicationTheme { 
            ChatMessageBubble(
                message = sampleMessage,
                roomPasscode = "1234",
                onInspect = {}
            ) 
        } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
