package com.noorconnect.domain.model

data class AccountSession(
    val id: String,
    val phoneNumber: String?,
    val isActive: Boolean,
)