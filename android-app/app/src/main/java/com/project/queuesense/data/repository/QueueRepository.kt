
package com.project.queuesense.data.repository

import com.project.queuesense.data.model.AccuracyResponse
import com.project.queuesense.data.model.ActualWaitRequest
import com.project.queuesense.data.model.CheckInRequest
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.data.remote.ApiClient
import com.project.queuesense.data.remote.ApiService
import com.project.queuesense.data.model.LoginRequest
import com.project.queuesense.data.model.RegisterRequest
import com.project.queuesense.data.model.AuthResponse
import com.project.queuesense.data.model.AuthUser
class QueueRepository(
    private val api: ApiService = ApiClient.service
) {

    suspend fun getDepartments(): List<String> =
        api.getDepartments()

    suspend fun checkIn(
        request: CheckInRequest
    ): TicketResponse =
        api.checkIn(request)

    suspend fun refreshTicket(
        id: Long
    ): TicketResponse =
        api.refreshTicket(id)

    suspend fun logActualWait(
        id: Long,
        actualWaitMin: Double
    ): AccuracyResponse {
        return api.logActualWait(
            id,
            ActualWaitRequest(actualWaitMin)
        )
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        return api.login(request)
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        return api.register(request)
    }

    suspend fun getCurrentUser(): AuthUser {
        return api.getCurrentUser()
    }

    suspend fun getCurrentTicket(): TicketResponse? {
        return api.getCurrentTicket()
    }

    suspend fun getAccuracy(
        id: Long
    ): AccuracyResponse {
        return api.getAccuracy(id)
    }
}
