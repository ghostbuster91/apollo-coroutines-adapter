package com.apollographql.apollo.support.coroutines

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.apollographql.apollo.support.coroutines.type.Episode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.consumeEach
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
    val server = MockWebServer()
    private lateinit var apolloClient: ApolloClient

    @Before
    fun setup() {
        val okHttpClient = OkHttpClient.Builder()
                .dispatcher(Dispatcher(currentThreadExecutorService()))
                .build()

        apolloClient = ApolloClient.builder()
                .serverUrl(server.url("/"))
                .dispatcher(currentThreadExecutorService())
                .okHttpClient(okHttpClient)
                .normalizedCache(LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION), IdFieldCacheKeyResolver())
                .build()
    }

    private val FILE_EPISODE_HERO_NAME_WITH_ID = "EpisodeHeroNameResponseWithId.json"
    private val FILE_EPISODE_HERO_NAME_CHANGE = "EpisodeHeroNameResponseNameChange.json"

    @Test
    fun callProducesValue() = runBlocking {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))
        val response = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).await()
        assertThat(response.hero?.name).isEqualTo("R2-D2")
    }

    @Test(expected = ApolloHttpException::class)
    fun callProducesError() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID).setResponseCode(401))
        apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).await()
    }

    @Test
    fun callIsCanceledWhenCancellingCoroutine() = runBlocking<Unit> {
        val query = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE)))
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID).setBodyDelay(1,TimeUnit.MILLISECONDS))

        val call = async(start = CoroutineStart.ATOMIC) { query.await() }
        call.cancelAndJoin()

        assertThat(query.isCanceled).isTrue()
    }

    @Test
    fun prefetchCompletes() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))

        val prefetch = apolloClient.prefetch(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE)))
        val call = async { prefetch.await() }

        call.join()
        assertThat(call.isCompleted).isTrue()
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }

    @Test(expected = ApolloHttpException::class)
    fun prefetchFails() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID).setResponseCode(401))
        apolloClient.prefetch(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).await()
    }

    @Test
    fun prefetchIsCanceledWhenDisposed() = runBlocking<Unit> {
        val prefetch = apolloClient.prefetch(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE)))
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID).setBodyDelay(1,TimeUnit.MILLISECONDS))

        val job = async(start = CoroutineStart.ATOMIC) { prefetch.await() }
        job.cancelAndJoin()

        assertThat(prefetch.isCanceled).isTrue()
    }

    @Test
    fun queryWatcherUpdatedSameQueryDifferentResults() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))

        val queryWatcher = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).watcher()

        val results = mutableListOf<EpisodeHeroNameQuery.Data>()
        val channel = queryWatcher.await(coroutineContext)
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_CHANGE))
        apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE)))
                .responseFetcher(ApolloResponseFetchers.NETWORK_ONLY)
                .enqueue(null)
        val job = launch {
            channel.consumeEach { results.add(it) }
        }
        channel.close()
        job.join()
        assertThat(results.size).isEqualTo(2)
        assertThat(results.map { it.hero!!.name }).containsExactly("R2-D2", "Artoo").inOrder()
    }

    @Test
    fun queryWatcherNotUpdatedSameQuerySameResults() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))

        val queryWatcher = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).watcher()

        val results = mutableListOf<EpisodeHeroNameQuery.Data>()
        val channel = queryWatcher.await(coroutineContext)
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))
        apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE)))
                .responseFetcher(ApolloResponseFetchers.NETWORK_ONLY)
                .enqueue(null)
        val job = launch {
            channel.consumeEach { results.add(it) }
        }
        channel.close()
        job.join()
        assertThat(results.size).isEqualTo(1)
        assertThat(results.map { it.hero!!.name }).containsExactly("R2-D2")
    }

    @Test
    fun queryWatcherUpdatedDifferentQueryDifferentResults() = runBlocking {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))

        val queryWatcher = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).watcher()

        val results = mutableListOf<EpisodeHeroNameQuery.Data>()
        val channel = queryWatcher.await(coroutineContext)
        server.enqueue(mockResponse("HeroAndFriendsNameWithIdsNameChange.json"))
        apolloClient.query(HeroAndFriendsNamesWithIDsQuery(Input.fromNullable(Episode.NEWHOPE))).enqueue(null)

        val job = launch {
            channel.consumeEach { results.add(it) }
        }
        channel.close()
        job.join()
        assertThat(results.size).isEqualTo(2)
        assertThat(results.map { it.hero!!.name }).containsExactly("R2-D2", "Artoo").inOrder()
    }

    @Test
    fun queryWatcherNotCalledWhenCanceled() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID))
        val queryWatcher = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).watcher()
        val results = mutableListOf<EpisodeHeroNameQuery.Data>()
        val channel = queryWatcher.await(coroutineContext)
        val job = launch {
            channel.consumeEach { results.add(it) }
        }
        channel.close()
        server.enqueue(mockResponse("HeroAndFriendsNameWithIdsNameChange.json"))
        apolloClient.query(HeroAndFriendsNamesWithIDsQuery(Input.fromNullable(Episode.NEWHOPE))).enqueue(null)

        job.join()
        assertThat(results.size).isEqualTo(1)
        assertThat(results.map { it.hero!!.name }).containsExactly("R2-D2")
    }

    @Test(expected = ApolloHttpException::class)
    fun queryWatcherFails() = runBlocking<Unit> {
        server.enqueue(mockResponse(FILE_EPISODE_HERO_NAME_WITH_ID).setResponseCode(401))
        val queryWatcher = apolloClient.query(EpisodeHeroNameQuery(Input.fromNullable(Episode.EMPIRE))).watcher()
        val channel = queryWatcher.await(coroutineContext)
        channel.consumeEach { println(it) }
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