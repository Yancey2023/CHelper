package yancey.chelper.ui.feature.command.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import yancey.chelper.domain.history.CopyHistoryRepository

class HistoryViewModel(repository: CopyHistoryRepository) : ViewModel() {
    val state =
        repository
            .observe()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
