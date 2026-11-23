package com.project.queuesense.ui.checkin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.queuesense.data.model.CheckInRequest
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CheckInViewModel(
    private val repository: QueueRepository = QueueRepository()
) : ViewModel() {

    private val _departments = MutableStateFlow<List<String>>(emptyList())
    val departments: StateFlow<List<String>> = _departments.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _createdTicket = MutableStateFlow<TicketResponse?>(null)
    val createdTicket: StateFlow<TicketResponse?> = _createdTicket.asStateFlow()

    init {
        loadDepartments()
    }

    private fun loadDepartments() {
        viewModelScope.launch {
            try {
                _departments.value = repository.getDepartments()
            } catch (e: Exception) {
                Log.e(TAG, "Could not load departments", e)
            }
        }
    }

    fun checkIn(fullName: String, mobile: String, department: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                val request = CheckInRequest(fullName, mobile.ifBlank { null }, department)
                _createdTicket.value = repository.checkIn(request)
            } catch (e: Exception) {
                Log.e(TAG, "Check in failed", e)
                _errorMessage.value = "Could not check in. Please check your connection and try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun ticketHandled() {
        _createdTicket.value = null
    }

    private companion object {
        const val TAG = "CheckInViewModel"
    }
}