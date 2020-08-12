package com.likethesalad.android.aaper.api

import com.google.common.truth.Truth
import com.likethesalad.android.aaper.api.base.PermissionStatusProvider
import com.likethesalad.android.aaper.api.base.RequestLauncher
import com.likethesalad.android.aaper.api.base.RequestStrategy
import com.likethesalad.android.aaper.api.base.RequestStrategyProvider
import com.likethesalad.android.aaper.api.data.PermissionsRequest
import com.likethesalad.android.aaper.api.data.PermissionsResult
import com.likethesalad.android.aaper.internal.base.RequestStrategyProviderSource
import com.likethesalad.android.aaper.internal.data.CurrentRequest
import com.likethesalad.android.aaper.internal.utils.RequestRunner
import com.likethesalad.android.aaper.internal.utils.testutils.BaseMockable
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.lang.reflect.Field

/**
 * Created by César Muñoz on 10/08/20.
 */
class PermissionManagerTest : BaseMockable() {

    @MockK
    lateinit var originalMethod: Runnable

    @MockK
    lateinit var permissionStatusProvider: PermissionStatusProvider<Any>

    @MockK
    lateinit var requestLauncher: RequestLauncher<Any>

    @MockK
    lateinit var strategy: RequestStrategy<Any>

    @MockK
    lateinit var host: Any


    private val requestCode = 1202
    private val strategyName = "someStrategyName"

    companion object {

        private lateinit var sourceMock: RequestStrategyProviderSource
        private lateinit var strategyProvider: RequestStrategyProvider

        @JvmStatic
        @BeforeClass
        fun init() {
            sourceMock = mockk(relaxUnitFun = true)
            strategyProvider = mockk(relaxUnitFun = true)
            every { sourceMock.getRequestStrategyProvider() }.returns(strategyProvider)
            PermissionManager.setStrategyProviderSource(sourceMock)
        }
    }

    @Before
    fun setUp() {
        cleanUp()
        every { strategyProvider.getStrategy(host, strategyName) }.returns(strategy)
        every {
            strategy.internalGetPermissionStatusProvider(host)
        }.returns(permissionStatusProvider)
        every { strategy.internalGetRequestLauncher(host) }.returns(requestLauncher)
        every { strategy.getRequestCode() }.returns(requestCode)
    }

    @Test
    fun `Process request with missing permissions and no pre-processing`() {
        val permissions = arrayOf("one", "two", "three", "four")
        val missingPermissions = arrayOf("one", "two")
        setUpPermissions(permissions, missingPermissions)
        every {
            strategy.internalOnBeforeLaunchingRequest(host, any(), any())
        }.returns(false)

        PermissionManager.processPermissionRequest(host, permissions, originalMethod, strategyName)

        verify {
            requestLauncher.internalLaunchPermissionsRequest(
                host,
                missingPermissions.toList(),
                requestCode
            )
        }
    }

    @Test
    fun `Process request with missing permissions and pre-processing`() {
        val permissions = arrayOf("one", "two", "three", "four")
        val missingPermissions = arrayOf("one", "two")
        val dataCaptor = slot<PermissionsRequest>()
        val runnerCaptor = slot<RequestRunner>()
        setUpPermissions(permissions, missingPermissions)
        every {
            strategy.internalOnBeforeLaunchingRequest(
                host, capture(dataCaptor), capture(runnerCaptor)
            )
        }.returns(true)

        PermissionManager.processPermissionRequest(host, permissions, originalMethod, strategyName)

        val capturedData = dataCaptor.captured
        Truth.assertThat(capturedData.missingPermissions).isEqualTo(missingPermissions.toList())
        Truth.assertThat(capturedData.permissions).isEqualTo(permissions.toList())
        verify(exactly = 0) {
            requestLauncher.internalLaunchPermissionsRequest(
                host,
                missingPermissions.toList(),
                requestCode
            )
        }

        // Check runnable
        runnerCaptor.captured.run()
        verify {
            requestLauncher.internalLaunchPermissionsRequest(
                host,
                missingPermissions.toList(),
                requestCode
            )
        }
    }

    @Test
    fun `Process request with no missing permissions`() {
        val permissions = arrayOf("one", "two")
        setUpPermissions(permissions, emptyArray())

        PermissionManager.processPermissionRequest(host, permissions, originalMethod, strategyName)

        verify {
            originalMethod.run()
        }
        verify(exactly = 0) {
            strategy.internalOnBeforeLaunchingRequest(any(), any(), any())
            strategy.internalGetRequestLauncher(any())
        }
    }

    @Test
    fun `Ignore request when another request is ongoing`() {
        val permissions = arrayOf("one", "two")
        val missingPermissions = arrayOf("one")
        setUpPermissions(permissions, missingPermissions)
        every {
            strategy.internalOnBeforeLaunchingRequest(
                host, any(), any()
            )
        }.returns(false)

        PermissionManager.processPermissionRequest(host, permissions, originalMethod, strategyName)

        verify {
            strategy.internalOnBeforeLaunchingRequest(host, any(), any())
            strategy.internalGetRequestLauncher(host)
        }
        clearMocks(strategy)

        // Second request
        val secondPermissions = arrayOf("three", "four")
        val secondMissingPermissions = arrayOf("three")
        val secondMethod = mockk<Runnable>()
        setUpPermissions(secondPermissions, secondMissingPermissions)
        every {
            strategy.internalGetPermissionStatusProvider(host)
        }.returns(permissionStatusProvider)

        PermissionManager.processPermissionRequest(
            host,
            secondPermissions,
            secondMethod,
            strategyName
        )

        verify(exactly = 0) {
            secondMethod.run()
            strategy.internalOnBeforeLaunchingRequest(any(), any(), any())
            strategy.internalGetRequestLauncher(any())
        }
    }

    @Test
    fun `Call original method of second request with no permissions missing when another request is ongoing`() {
        val permissions = arrayOf("one", "two")
        val missingPermissions = arrayOf("one")
        setUpPermissions(permissions, missingPermissions)
        every {
            strategy.internalOnBeforeLaunchingRequest(
                host, any(), any()
            )
        }.returns(false)

        PermissionManager.processPermissionRequest(host, permissions, originalMethod, strategyName)

        verify {
            strategy.internalOnBeforeLaunchingRequest(host, any(), any())
            strategy.internalGetRequestLauncher(host)
        }
        clearMocks(strategy)

        // Second request
        val secondPermissions = arrayOf("three", "four")
        val secondMethod = mockk<Runnable>()
        setUpPermissions(secondPermissions, emptyArray())
        every {
            strategy.internalGetPermissionStatusProvider(host)
        }.returns(permissionStatusProvider)

        PermissionManager.processPermissionRequest(
            host,
            secondPermissions,
            secondMethod,
            strategyName
        )

        verify {
            secondMethod.run()
        }
        verify(exactly = 0) {
            strategy.internalOnBeforeLaunchingRequest(any(), any(), any())
            strategy.internalGetRequestLauncher(any())
        }
    }

    @Test
    fun `Do nothing if no current request is ongoing when processing response`() {
        PermissionManager.processPermissionResponse(host, requestCode, arrayOf("one"))

        verify(exactly = 0) {
            strategy.internalOnPermissionsRequestResults(any(), any())
        }
    }

    @Test
    fun `Do nothing if current request host and provided host are different for response`() {
        val currentRequest = mockk<CurrentRequest>()
        val notRequestingHost = mockk<Any>()
        every { currentRequest.host }.returns(host)
        setCurrentRequestValue(currentRequest)

        PermissionManager.processPermissionResponse(notRequestingHost, requestCode, arrayOf())

        Truth.assertThat(getCurrentRequestValue()).isEqualTo(currentRequest)
        verify(exactly = 0) {
            strategy.internalOnPermissionsRequestResults(any(), any())
        }
    }

    @Test
    fun `Do nothing if current request code is not the same as the one provided for response`() {
        val currentRequest = mockk<CurrentRequest>()
        every { currentRequest.host }.returns(host)
        every { currentRequest.strategy }.returns(strategy)
        setCurrentRequestValue(currentRequest)

        PermissionManager.processPermissionResponse(host, 404, arrayOf())

        Truth.assertThat(getCurrentRequestValue()).isEqualTo(currentRequest)
        verify(exactly = 0) {
            strategy.internalOnPermissionsRequestResults(any(), any())
        }
    }

    @Test
    fun `Delegate response handling to currentRequest strategy and clean up, don't call method when response is false`() {
        val currentRequest = mockk<CurrentRequest>()
        val requestData = mockk<PermissionsRequest>()
        val resultDataCaptor = slot<PermissionsResult>()
        val permissionsRequested = arrayOf("one", "two", "three", "four")
        val permissionsDenied = arrayOf("one", "three")
        setUpPermissions(permissionsRequested, permissionsDenied)
        every { currentRequest.host }.returns(host)
        every { currentRequest.strategy }.returns(strategy)
        every { currentRequest.originalMethod }.returns(originalMethod)
        every { currentRequest.data }.returns(requestData)
        every { strategy.internalOnPermissionsRequestResults(any(), any()) }.returns(false)
        setCurrentRequestValue(currentRequest)

        PermissionManager.processPermissionResponse(host, requestCode, permissionsRequested)

        Truth.assertThat(getCurrentRequestValue()).isNull()
        verify {
            strategy.internalOnPermissionsRequestResults(host, capture(resultDataCaptor))
        }
        verify(exactly = 0) {
            originalMethod.run()
        }
        val capturedResponse = resultDataCaptor.captured
        Truth.assertThat(capturedResponse.granted).containsExactly("two", "four")
        Truth.assertThat(capturedResponse.denied).containsExactly("one", "three")
        Truth.assertThat(capturedResponse.request).isEqualTo(requestData)
    }

    @Test
    fun `Delegate response handling to currentRequest strategy and clean up, do call method when response is true`() {
        val currentRequest = mockk<CurrentRequest>()
        val requestData = mockk<PermissionsRequest>()
        val resultDataCaptor = slot<PermissionsResult>()
        val permissionsRequested = arrayOf("one", "two", "three", "four")
        setUpPermissions(permissionsRequested, emptyArray())
        every { currentRequest.host }.returns(host)
        every { currentRequest.strategy }.returns(strategy)
        every { currentRequest.originalMethod }.returns(originalMethod)
        every { currentRequest.data }.returns(requestData)
        every { strategy.internalOnPermissionsRequestResults(any(), any()) }.returns(true)
        setCurrentRequestValue(currentRequest)

        PermissionManager.processPermissionResponse(host, requestCode, permissionsRequested)

        Truth.assertThat(getCurrentRequestValue()).isNull()
        verify {
            strategy.internalOnPermissionsRequestResults(host, capture(resultDataCaptor))
            originalMethod.run()
        }
        val capturedResponse = resultDataCaptor.captured
        Truth.assertThat(capturedResponse.granted).containsExactlyElementsIn(permissionsRequested)
        Truth.assertThat(capturedResponse.denied).isEmpty()
        Truth.assertThat(capturedResponse.request).isEqualTo(requestData)
    }

    private fun setUpPermissions(permissions: Array<String>, missingPermissions: Array<String>) {
        permissions.forEach {
            every {
                permissionStatusProvider.internalIsPermissionGranted(host, it)
            }.returns(it !in missingPermissions)
        }
    }

    private fun cleanUp() {
        clearMocks(strategyProvider)
        val method = PermissionManager::class.java.getDeclaredMethod("cleanUp")
        method.isAccessible = true
        method.invoke(PermissionManager)
    }

    private fun setCurrentRequestValue(currentRequest: CurrentRequest?) {
        val field = getCurrentRequestField()
        field.set(PermissionManager, currentRequest)
    }

    private fun getCurrentRequestValue(): CurrentRequest? {
        return getCurrentRequestField().get(PermissionManager) as? CurrentRequest
    }

    private fun getCurrentRequestField(): Field {
        return PermissionManager::class.java.getDeclaredField("currentRequest").apply {
            isAccessible = true
        }
    }
}