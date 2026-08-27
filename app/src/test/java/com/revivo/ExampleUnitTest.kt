package com.revivo

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun parseUploadLog_userExample() {
    val log = "[upload] esempio_chat_export.zip: 42% (1288.4MB / 3072.0MB)"
    val result = UploadForegroundService.parseUploadLog(log)
    assertNotNull(result)
    assertEquals("esempio_chat_export.zip", result?.fileName)
    assertEquals(42, result?.percent)
    assertEquals("1288.4MB / 3072.0MB", result?.detail)
    assertFalse(result?.isComplete == true)
  }

  @Test
  fun parseUploadLog_completion() {
    val log = "[upload] esempio_chat_export.zip: 100% (3072.0MB / 3072.0MB)"
    val result = UploadForegroundService.parseUploadLog(log)
    assertNotNull(result)
    assertEquals(100, result?.percent)
    assertTrue(result?.isComplete == true)
  }

  @Test
  fun parseUploadLog_completeWord() {
    val log = "[upload] upload completed"
    val result = UploadForegroundService.parseUploadLog(log)
    assertNotNull(result)
    assertEquals(100, result?.percent)
    assertTrue(result?.isComplete == true)
  }

  @Test
  fun parseUploadLog_unrelated() {
    val log = "user clicked button"
    val result = UploadForegroundService.parseUploadLog(log)
    assertNull(result)
  }
}
