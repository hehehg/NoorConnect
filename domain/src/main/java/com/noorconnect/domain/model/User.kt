package com.noorconnect.domain.model

data class User(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phoneNumber: String?,
)
