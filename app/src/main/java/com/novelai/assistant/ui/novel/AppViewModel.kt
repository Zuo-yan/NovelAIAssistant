package com.novelai.assistant.ui.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.prefs.AppThemeMode
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 应用级状态：新手引导完成标记、全局主题模式等 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val readingPreferencesRepository: ReadingPreferencesRepository
) : ViewModel() {

    /** null = 加载中；true = 已完成引导 */
    val onboardingDone: StateFlow<Boolean?> = readingPreferencesRepository.onboardingDoneFlow
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appThemeMode: StateFlow<AppThemeMode> = readingPreferencesRepository.settings
        .map { it.appThemeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.FOLLOW_SYSTEM)

    fun completeOnboarding() {
        viewModelScope.launch { readingPreferencesRepository.setOnboardingDone() }
    }
}
