package com.likethesalad.android.aaper.api.base

import com.likethesalad.android.aaper.api.data.PermissionsRequest
import com.likethesalad.android.aaper.api.data.PermissionsResult
import com.likethesalad.android.aaper.internal.utils.RequestRunner

/**
 * Created by César Muñoz on 29/07/20.
 */
@Suppress("UNCHECKED_CAST")
abstract class RequestStrategy<T> {

    companion object {
        const val DEFAULT_REQUEST_CODE = 1202
    }

    internal fun internalOnBeforeLaunchingRequest(
        host: Any,
        data: PermissionsRequest,
        request: RequestRunner
    ): Boolean {
        return onBeforeLaunchingRequest(host as T, data, request)
    }

    internal fun internalOnPermissionsRequestResults(
        host: Any,
        data: PermissionsResult
    ): Boolean {
        return onPermissionsRequestResults(host as T, data)
    }

    open fun getRequestCode(): Int {
        return DEFAULT_REQUEST_CODE
    }

    open fun onBeforeLaunchingRequest(
        host: T,
        data: PermissionsRequest,
        request: RequestRunner
    ): Boolean {
        return false
    }

    abstract fun onPermissionsRequestResults(
        host: T,
        data: PermissionsResult
    ): Boolean

    abstract fun getName(): String

    abstract fun getRequestLauncher(): RequestLauncher<T>

    abstract fun getPermissionStatusProvider(): PermissionStatusProvider<T>
}