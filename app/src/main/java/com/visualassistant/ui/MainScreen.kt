package com.visualassistant.ui

import android.view.HapticFeedbackConstants
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.visualassistant.VisualAssistantViewModel

/**
 * MainScreen — the primary UI for the Visual Assistant.
 *
 * Design principles:
 * - Minimal visual complexity (primary users are visually impaired)
 * - Very large touch targets (>120dp minimum)
 * - High contrast colors
 * - TalkBack-compatible semantics
 * - Camera preview as background (for low-vision users)
 * - Single action: START / STOP
 * - Microphone button for voice commands
 */
@Composable
fun MainScreen(
    viewModel: VisualAssistantViewModel,
    previewView: PreviewView
) {
    val isActive by viewModel.isAssistantActive.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val view = LocalView.current

    // Pulsing animation for listening indicator
    val infiniteTransition = rememberInfiniteTransition(label = "listening_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Button color animation
    val startButtonColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFFFF4444) else Color(0xFFFFD700),
        label = "button_color"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview as background
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App title
            Text(
                text = "VISUAL\nASSISTANT",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .padding(top = 40.dp)
                    .semantics { contentDescription = "Visual Assistant App" }
            )

            // Center: START/STOP button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main START/STOP button (large, circular, accessible)
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.toggleAssistant()
                    },
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isActive) {
                                "Stop Visual Assistant. Double tap to stop."
                            } else {
                                "Start Visual Assistant. Double tap to start."
                            }
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = startButtonColor,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        text = if (isActive) "STOP" else "START",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Microphone button — only visible when assistant is active
                if (isActive) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                            viewModel.startListening()
                        },
                        modifier = Modifier
                            .size(if (isListening) (140 * pulseScale).dp else 140.dp)
                            .clip(CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = if (isListening) {
                                    "Listening for your command"
                                } else {
                                    "Tap to speak a command"
                                }
                            },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) Color(0xFF00FF00) else Color(0xFF333333),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (isListening) "🎙️" else "🎤",
                            fontSize = 48.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Status text at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = statusText }
                )
            }
        }

        // Listening pulse ring overlay
        if (isListening) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size((280 * pulseScale).dp)
                        .border(4.dp, Color(0x8800FF00), CircleShape)
                )
            }
        }
    }
}
