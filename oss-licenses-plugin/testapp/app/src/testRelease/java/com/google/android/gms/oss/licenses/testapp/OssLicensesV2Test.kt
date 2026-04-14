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
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Color
import android.util.TypedValue
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OssLicensesV2Test {

    @get:Rule val composeTestRule = createEmptyComposeRule()

    @Test
    fun testV2ActivityMenuLoadsCorrectly() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use {
            // Verify a standard library is visible
            composeTestRule.onNodeWithText("Activity", ignoreCase = true).assertExists()
        }
    }

    @Test
    fun testV2DetailNavigation() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use {
            // Click on a visible entry
            composeTestRule.onNodeWithText("Activity", ignoreCase = true).performClick()

            // Verify detail screen shows license text
            try {
                composeTestRule
                    .onNodeWithText("Apache License", substring = true, ignoreCase = true)
                    .assertExists()
            } catch (e: AssertionError) {
                composeTestRule
                    .onNodeWithText("http", substring = true, ignoreCase = true)
                    .assertExists()
            }
        }
    }

    @Test
    fun testV2LicenseSourceTypes() {
        // Verifies that the plugin correctly extracts licenses from both sources:
        // 1. POM files (standard Maven deps like AndroidX) — license URL in the POM XML
        // 2. AAR-embedded license files (Google Play Services) — third_party_licenses.txt in the AAR
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val res = activity.resources
                val pkg = activity.packageName
                val metadataId = res.getIdentifier("third_party_license_metadata", "raw", pkg)
                val metadata = res.openRawResource(metadataId).bufferedReader().readText()

                // POM-based: AndroidX libraries have licenses declared in their POM XML
                assertTrue("Expected POM-based entry (e.g. AppCompat)", metadata.contains("AppCompat"))

                // AAR-embedded: Play Services bundles third_party_licenses.txt inside the AAR
                assertTrue("Expected AAR-embedded entry (e.g. play-services-base)", metadata.contains("play-services-base"))
            }
        }
    }



    @Test
    fun testV2ActivitySetActivityTitle() {
        val customTitle = "Test Title via API"
        OssLicensesMenuActivity.setActivityTitle(customTitle)

        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use {
            composeTestRule.onNodeWithText(customTitle).assertExists()
        }
    }

    @Test
    fun testV2ActivityUsesXmlThemeFallback() {
        // Ensure no programmatic theme is set
        OssLicensesMenuActivity.setTheme(null, null, null)

        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val typedValue = TypedValue()
                val theme = activity.theme
                val success = theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
                
                assertTrue("Failed to resolve windowBackground attribute", success)
                
                // The expected color is #FCE4EC (Light Pink) defined in Theme.CustomOssThemeV2
                val expectedColor = Color.parseColor("#FCE4EC")
                assertEquals("Theme background color mismatch", expectedColor, typedValue.data)
            }
        }
    }
}
