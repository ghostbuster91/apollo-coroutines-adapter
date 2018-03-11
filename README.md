# Apollo GraphQL Coroutines Adapter

[![Build Status](https://travis-ci.org/ghostbuster91/apollo-coroutines-adapter.svg?branch=master)](https://travis-ci.org/ghostbuster91/apollo-coroutines-adapter)
[![JitPack](https://jitpack.io/v/ghostbuster91/apollo-coroutines-adapter.svg)](https://jitpack.io/#ghostbuster91/apollo-coroutines-adapter)

An adapter for Apollo GraphQL client which adds support for kotlin coroutines.
Apollo types such as ApolloCall, ApolloPrefetch & ApolloWatcher can be converted to their 
corresponding suspending functions and channels by using extension functions provided in CoroutineApollo file respectively.

## Download

```
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
        compile 'com.github.ghostbuster91:apollo-coroutines-adapter:master-SNAPSHOT'
}
```

## Usage
Converting ApolloCall to a suspending function:

```
//Create a query object
EpisodeHeroName query = EpisodeHeroName.builder().episode(Episode.EMPIRE).build();

//Create an ApolloCall object
ApolloCall<EpisodeHeroName.Data> apolloCall = apolloClient.query(query);

//Convert to suspending function
val result = apolloCall.await();
```

Converting ApolloPrefetch to a suspending function:

```
//Create a query object
EpisodeHeroName query = EpisodeHeroName.builder().episode(Episode.EMPIRE).build();

//Create an ApolloPrefetch object
ApolloPrefetch<EpisodeHeroName.Data> apolloPrefetch = apolloClient.prefetch(query);

//Convert to suspending function
apolloPrefetch.await()
```

Converting ApolloWatcher to a Channel:

```
//Create a query object
EpisodeHeroName query = EpisodeHeroName.builder().episode(Episode.EMPIRE).build();

//Create an ApolloWatcher object
ApolloWatcher<EpisodeHeroName.Data> apolloWatcher = apolloClient.query(query).watcher();

//Convert to channel
for(heroName : apolloWatcher.await()) {
    // here goes your sequential processing
};
```

Remember to cancel all your jobs which operate on UI not later then in onDestroy:

```
job = launch(UI) {
    val result = apolloCall().await()
    myTextView.setText(result.toString())
}

...
job?.cancel()
```