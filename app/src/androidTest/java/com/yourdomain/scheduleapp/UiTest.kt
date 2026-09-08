package com.yourdomain.scheduleapp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun navigateAndCreateSubject(){
        rule.waitUntil(20000){rule.onAllNodesWithText("Впереди свободное время").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Предметы").performClick()
        rule.onNodeWithContentDescription("Добавить предмет").performClick()
        rule.onNodeWithText("Название").performTextInput("Тест ОАП")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Тест ОАП").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Ещё").performClick()
        rule.onNodeWithText("Сохранить и проверить JSON").assertExists()
        rule.onNodeWithText("Неделя").performClick()
        rule.onNodeWithText("Показать обе недели").assertExists()
        rule.onNodeWithText("ВТ").performClick()
        rule.onNodeWithText("1 пара · 08:00–09:20").performClick()
        rule.onNodeWithText("Одна").performClick()
        rule.onNodeWithText("Предмет").performTextInput("Тест ОАП")
        rule.onNodeWithText("Аудитория").performTextInput("105")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Ауд. 105").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("ДЗ").performClick()
        rule.onNodeWithText("Добавить задание").assertDoesNotExist()
        rule.onNodeWithContentDescription("Добавить задание").assertExists()
        rule.onNodeWithContentDescription("Добавить задание").performClick()
        rule.onNodeWithText("Предмет").performTextInput("Тест ОАП")
        rule.onNodeWithText("Что нужно сделать?").performTextInput("Практическая работа 1")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Практическая работа 1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Практическая работа 1").assertExists()
    }
}
