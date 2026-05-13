package com.mediassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.R
import com.mediassist.app.ui.theme.*

@Composable
fun WelcomeScreen(
    hasProfile: Boolean,
    isModelReady: Boolean,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.ic_pam_logo),
                contentDescription = "PAM Assist logo",
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Positive Aging",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = PoppinsFamily,
                color = TealDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Mission",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = PoppinsFamily,
                color = TealPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tu asistente de salud personal",
                fontSize = 14.sp,
                fontFamily = PoppinsFamily,
                color = TextHint,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    when {
                        !hasProfile -> onNavigate("basic_profile")
                        !isModelReady -> onNavigate("model_load")
                        else -> onNavigate("main_menu")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Entrar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PoppinsFamily,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
