package com.app.gettube

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Android 기기에서 실행되는 계측 테스트.
 *
 * [테스트 문서](http://d.android.com/tools/testing) 참고.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 테스트 대상 앱의 Context.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.app.gettube", appContext.packageName)
    }
}