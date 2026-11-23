package com.project.queuesense.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.queuesense.data.model.AccuracyResponse
import com.project.queuesense.data.model.TicketResponse
import retrofit2.HttpException
import com.project.queuesense.data.repository.QueueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AppNotification(
    val title: String,
    val message: String,
    val timeMillis: Long
)

/** Holds the patient's current ticket (and events about it) so every screen can read it. */
class QueueSessionViewModel(
    private val repository: QueueRepository = QueueRepository()
) : ViewModel() {

    private val _current = MutableStateFlow<TicketResponse?>(null)
    val current: StateFlow<TicketResponse?> = _current.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRestoringTicket = MutableStateFlow(false)
    val isRestoringTicket: StateFlow<Boolean> = _isRestoringTicket.asStateFlow()

    private val _restoreTicketError = MutableStateFlow<String?>(null)
    val restoreTicketError: StateFlow<String?> = _restoreTicketError.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()
    private val _accuracy = MutableStateFlow<AccuracyResponse?>(null)
    val accuracy: StateFlow<AccuracyResponse?> = _accuracy.asStateFlow()

    private val _isLoadingAccuracy = MutableStateFlow(false)
    val isLoadingAccuracy: StateFlow<Boolean> = _isLoadingAccuracy.asStateFlow()

    private val _isSubmittingActual = MutableStateFlow(false)
    val isSubmittingActual: StateFlow<Boolean> = _isSubmittingActual.asStateFlow()

    private val _accuracyError = MutableStateFlow<String?>(null)
    val accuracyError: StateFlow<String?> = _accuracyError.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    fun setTicket(ticket: TicketResponse) {
        _accuracy.value = null
        _accuracyError.value = null
        _isLoadingAccuracy.value = false
        _isSubmittingActual.value = false
        _current.value = ticket
        _refreshError.value = null
        _notifications.value = listOf(
            AppNotification(
                title = "Check-in confirmed",
                message = "You joined the ${ticket.ticket.department} queue as ticket ${ticket.ticket.ticketCode}.",
                timeMillis = System.currentTimeMillis()
            )
        )
    }
    fun restoreActiveTicket() {
        if (_isRestoringTicket.value) return

        viewModelScope.launch {
            _isRestoringTicket.value = true
            _restoreTicketError.value = null

            try {
                val ticket = repository.getCurrentTicket()

                _current.value = ticket

                // Clear ticket-specific state when no active ticket exists.
                if (ticket == null) {
                    _accuracy.value = null
                    _accuracyError.value = null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ticket restoration failed", e)
                _restoreTicketError.value =
                    "Could not restore your queue session. Please try again."
            } finally {
                _isRestoringTicket.value = false
            }
        }
    }

    fun refresh() {
        val before = _current.value ?: return
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                val after = repository.refreshTicket(before.ticket.id)
                _current.value = after
                addRefreshNotifications(before, after)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                _refreshError.value = "Could not refresh the prediction. Please try again."
            } finally {
                _isRefreshing.value = false
            }
        }
    }


    fun loadAccuracy(ticketId: Long) {
        viewModelScope.launch {
            _isLoadingAccuracy.value = true
            _accuracyError.value = null
            _accuracy.value = null

            try {
                val result = repository.getAccuracy(ticketId)
                _accuracy.value = result
                markTicketCompleted()
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // No actual wait has been logged yet.
                    _accuracy.value = null
                } else {
                    Log.e(TAG, "Accuracy lookup failed", e)
                    _accuracyError.value =
                        "Could not load your accuracy result. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Accuracy lookup failed", e)
                _accuracyError.value =
                    "Could not load your accuracy result. Please try again."
            } finally {
                _isLoadingAccuracy.value = false
            }
        }
    }

    fun submitActualWait(actualWaitMin: Double) {
        val ticketId = _current.value?.ticket?.id ?: return

        if (_isSubmittingActual.value) return

        if (actualWaitMin !in 0.0..300.0) {
            _accuracyError.value = "Enter a wait between 0 and 300 minutes."
            return
        }

        viewModelScope.launch {
            _isSubmittingActual.value = true
            _accuracyError.value = null

            try {
                val result = repository.logActualWait(ticketId, actualWaitMin)

                _accuracy.value = result
                markTicketCompleted()

                _notifications.value = listOf(
                    AppNotification(
                        title = "Actual wait logged",
                        message = "Your actual waiting time has been recorded.",
                        timeMillis = System.currentTimeMillis()
                    )
                ) + _notifications.value

            } catch (e: HttpException) {
                if (e.code() == 409) {
                    // The actual wait was already recorded.
                    // Retrieve the saved result instead of asking again.
                    try {
                        val savedResult = repository.getAccuracy(ticketId)
                        _accuracy.value = savedResult
                        _accuracyError.value = null
                        markTicketCompleted()
                    } catch (lookupError: Exception) {
                        Log.e(TAG, "Could not retrieve saved accuracy", lookupError)
                        _accuracyError.value =
                            "Your wait may already be recorded. Please reopen this screen to check."
                    }
                } else {
                    Log.e(TAG, "Actual wait submission failed", e)
                    _accuracyError.value =
                        "Could not record your actual waiting time. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Actual wait submission failed", e)
                _accuracyError.value =
                    "Could not record your actual waiting time. Please try again."
            } finally {
                _isSubmittingActual.value = false
            }
        }
    }

    private fun markTicketCompleted() {
        val currentTicket = _current.value ?: return

        _current.value = currentTicket.copy(
            ticket = currentTicket.ticket.copy(status = "completed")
        )
    }


    fun clear() {
        _current.value = null
        _refreshError.value = null
        _notifications.value = emptyList()
        _accuracy.value = null
        _accuracyError.value = null
        _isLoadingAccuracy.value = false
        _isSubmittingActual.value = false
        _isRestoringTicket.value = false
        _restoreTicketError.value = null
    }

    private fun addRefreshNotifications(before: TicketResponse, after: TicketResponse) {
        val now = System.currentTimeMillis()
        val added = mutableListOf<AppNotification>()
        val beforeRange = rangeText(before)
        val afterRange = rangeText(after)
        if (beforeRange != afterRange) {
            added += AppNotification(
                "Wait estimate changed",
                "Your predicted wait is now $afterRange (previously $beforeRange).",
                now
            )
        }
        if (before.queue.queuePosition != after.queue.queuePosition) {
            added += AppNotification(
                "Queue position updated",
                "You are now #${after.queue.queuePosition}. ${after.queue.patientsAhead} patients ahead.",
                now
            )
        }
        _notifications.value = added + _notifications.value
    }

    private fun rangeText(t: TicketResponse): String =
        "${t.prediction.rangeLowMin.roundToInt()}–${t.prediction.rangeHighMin.roundToInt()} min"

    private companion object {
        const val TAG = "QueueSessionViewModel"
    }
}