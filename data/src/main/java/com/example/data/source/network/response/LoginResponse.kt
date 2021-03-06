package com.example.data.source.network.response

import com.google.gson.annotations.SerializedName

data class LoginResponse (

    @field:SerializedName("localId")
    var userId : String,

    //@field:Json(name = "email")
    @field:SerializedName("email")
    var email : String?,

    //@field:Json(name = "password")
    @field:SerializedName("password")
    var password : String?,

    //@field:Json(name = "displayName")
    @field:SerializedName("displayName")
    var displayName : String?,

    //@field:Json(name = "idToken")
    @field:SerializedName("idToken")
    var token : String?,

    //@field:Json(name= "refreshToken")
    @field:SerializedName("refreshToken")
    var refreshToken : String?
)