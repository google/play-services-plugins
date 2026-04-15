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

import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import org.hamcrest.CoreMatchers.anything
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class OssLicensesV1Test {

    @Test
    fun testV1ActivityLoadsLicenses() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("Open source licenses", activity.title)

                val res = activity.resources
                val packageName = activity.packageName
                val metadataId =
                    res.getIdentifier("third_party_license_metadata", "raw", packageName)
                val licensesId = res.getIdentifier("third_party_licenses", "raw", packageName)

                assertNotEquals(
                    "Resource 'raw/third_party_license_metadata' not found.",
                    0,
                    metadataId,
                )
                assertNotEquals("Resource 'raw/third_party_licenses' not found.", 0, licensesId)

                res.openRawResource(metadataId).use {
                    assertNotEquals("Metadata file is empty.", 0, it.available())
                }
                res.openRawResource(licensesId).use {
                    assertNotEquals("Licenses file is empty.", 0, it.available())
                }
            }
        }
    }

    @Test
    fun testV1DetailNavigation() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use { scenario ->
            // Use Espresso to click the first item in the list.
            // Targeting by class type (ListView) is more robust than using internal library IDs.
            onData(anything())
                .inAdapterView(isAssignableFrom(ListView::class.java))
                .atPosition(0)
                .perform(click())

            scenario.onActivity { activity ->
                // Use ShadowActivity to verify the next activity was started
                val shadowActivity = shadowOf(activity)
                val nextIntent = shadowActivity.nextStartedActivity
                assertNotEquals("Detail activity should have been started", null, nextIntent)
                assertTrue(
                    "Started activity should be OssLicensesActivity",
                    nextIntent.component?.className?.contains("OssLicensesActivity") == true,
                )
            }
        }
    }

    @Test
    fun testV1ActivityMenuLoadsCorrectly() {
        ActivityScenario.launch(OssLicensesMenuActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertNotEquals(null, activity) }
        }
    }
}
