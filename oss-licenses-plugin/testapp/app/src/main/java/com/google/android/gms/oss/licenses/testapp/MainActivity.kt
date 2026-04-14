/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.oss.licenses.testapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity as V1Activity
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity as V2Activity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Library Version: ${getLibraryVersion()}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { startActivity(Intent(this@MainActivity, V1Activity::class.java)) }) {
                        Text("Launch V1 (XML Theme)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val customColors = lightColorScheme(
                            background = Color(0xFFE0F7FA), // Light Cyan
                            surface = Color(0xFFE0F7FA),    // Light Cyan for TopAppBar
                            onBackground = Color.Black,
                            onSurface = Color.Black
                        )
                        V2Activity.setTheme(customColors, customColors, null)
                        V2Activity.setActivityTitle("Custom Title from App")
                        startActivity(Intent(this@MainActivity, V2Activity::class.java))
                    }) {
                        Text("Launch V2 (Compose Theme)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        V2Activity.setTheme(null, null, null)
                        V2Activity.setActivityTitle("Title from XML Theme")
                        startActivity(Intent(this@MainActivity, V2Activity::class.java))
                    }) {
                        Text("Launch V2 (XML Theme)")
                    }
                }
            }
        }
    }

    private fun getLibraryVersion(): String {
        val id = resources.getIdentifier("version", "raw", packageName)
        if (id == 0) return "UNKNOWN"
        return try {
            resources.openRawResource(id).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
