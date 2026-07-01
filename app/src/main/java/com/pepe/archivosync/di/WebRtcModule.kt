package com.pepe.archivosync.di

import android.content.Context
import com.pepe.archivosync.data.remote.OrchestratorApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.webrtc.PeerConnectionFactory
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    /**
     * The process-wide WebRTC factory. `initialize` must run once before any
     * PeerConnection is built; we only need DataChannels, so no audio/video
     * encoder/decoder factories are configured.
     */
    @Provides
    @Singleton
    fun providePeerConnectionFactory(@ApplicationContext context: Context): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        return PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    @Provides
    @Singleton
    fun provideOrchestratorApi(retrofit: Retrofit): OrchestratorApi =
        retrofit.create(OrchestratorApi::class.java)
}
