package com.example

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sambung Ayat", appName)
  }

  @Test
  fun `launch main activity successfully`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }

  @Test
  fun `full user flow of starting the app and starting quiz`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        // Retrieve view references from activity
        val btnStart = activity.findViewById<View>(R.id.btnStartApp)
        val panelIntro = activity.findViewById<View>(R.id.panelIntro)
        val panelMenu = activity.findViewById<View>(R.id.panelMenu)
        val btnStartQuiz = activity.findViewById<View>(R.id.btnStartQuiz)
        val panelQuiz = activity.findViewById<View>(R.id.panelQuiz)

        // 1. Initially splash/intro panel is visible, menu is gone
        assertEquals(View.VISIBLE, panelIntro.visibility)
        assertEquals(View.GONE, panelMenu.visibility)

        // 2. Click start button -> Intro gets hidden, menu becomes visible
        btnStart.performClick()
        assertEquals(View.GONE, panelIntro.visibility)
        assertEquals(View.VISIBLE, panelMenu.visibility)

        // 3. Click start quiz -> Game starts, Quiz Panel becomes visible
        btnStartQuiz.performClick()
        assertEquals(View.VISIBLE, panelQuiz.visibility)

        // 4. Check option button setup
        val btnOption0 = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOption0)
        val btnOption1 = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOption1)
        val btnOption2 = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOption2)
        val btnOption3 = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOption3)

        assertNotNull(btnOption0.text)
        assertNotNull(btnOption1.text)
        assertNotNull(btnOption2.text)
        assertNotNull(btnOption3.text)

        // Options should be clickable and filled
        assertTrue(btnOption0.isClickable)
        assertTrue(btnOption1.isClickable)
        assertTrue(btnOption2.isClickable)
        assertTrue(btnOption3.isClickable)
      }
    }
  }
}
