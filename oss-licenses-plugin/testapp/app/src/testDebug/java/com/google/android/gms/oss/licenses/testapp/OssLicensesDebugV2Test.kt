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

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OssLicensesDebugV2Test {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    @Test
    fun testV2DebugActivityLoadsCorrectly() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use {
            // In debug mode, the plugin injects a placeholder entry
            composeTestRule.onNodeWithText("Debug License Info").assertExists()
        }
    }
}
