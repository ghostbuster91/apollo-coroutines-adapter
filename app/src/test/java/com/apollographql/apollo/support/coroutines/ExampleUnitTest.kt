package com.apollographql.apollo.support.coroutines

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.support.coroutines.type.Episode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CoroutinesSupportTest {

    @JvmField
    @Rule
    val rule = MockWebServer()
    private lateinit var apolloClient: ApolloClient

    @Before
    fun setup() {
        val okHttpClient = OkHttpClient.Builder()
                .dispatcher(Dispatcher(currentThreadExecutorService()))
                .build()

        apolloClient = ApolloClient.builder()
                .serverUrl(rule.url("/"))
                .dispatcher(currentThreadExecutorService())
                .okHttpClient(okHttpClient)
                .build()
    }

    private val FILE_EPISODE_HERO_NAME_WITH_ID = "EpisodeHeroNameResponseWithId.json"

    @Test
    fun callProducesValue() = runBlocking {
        rule.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))
        val response = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).await()
        assertThat(response.hero?.name).isEqualTo("R2-D2")
    }
}

private fun mockResponse(fileName: String): MockResponse {
    return MockResponse().setChunkedBody(readJson(fileName), 32)
}

private fun currentThreadExecutorService(): ExecutorService {
    val callerRunsPolicy = ThreadPoolExecutor.CallerRunsPolicy()
    return object : ThreadPoolExecutor(0, 1, 0L, TimeUnit.SECONDS, SynchronousQueue(), callerRunsPolicy) {
        override fun execute(command: Runnable) {
            callerRunsPolicy.rejectedExecution(command, this)
        }
    }
}

private fun readJson(jsonFileName: String) = File(ClassLoader.getSystemClassLoader().getResource(jsonFileName).file).readText()