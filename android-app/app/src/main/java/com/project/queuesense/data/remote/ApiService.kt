
package com.project.queuesense.data.remote

import com.project.queuesense.data.model.ActualWaitRequest
import com.project.queuesense.data.model.AccuracyResponse
import com.project.queuesense.data.model.CheckInRequest
import com.project.queuesense.data.model.TicketResponse
import com.project.queuesense.data.model.RegisterRequest
import com.project.queuesense.data.model.LoginRequest
import com.project.queuesense.data.model.AuthResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import com.project.queuesense.data.model.AuthUser


interface ApiService {

    @GET("departments")
    suspend fun getDepartments(): List<String>

    @GET("tickets/current")
    suspend fun getCurrentTicket(): TicketResponse?

    @POST("tickets")
    suspend fun checkIn(
        @Body request: CheckInRequest
    ): TicketResponse

    @POST("tickets/{id}/refresh")
    suspend fun refreshTicket(
        @Path("id") id: Long
    ): TicketResponse


    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): AuthResponse

    @GET("auth/me")
    suspend fun getCurrentUser(): AuthUser


    @POST("tickets/{id}/actual")
    suspend fun logActualWait(
        @Path("id") id: Long,
        @Body request: ActualWaitRequest
    ): AccuracyResponse



    @GET("tickets/{id}/accuracy")
    suspend fun getAccuracy(
        @Path("id") id: Long
    ): AccuracyResponse
}
