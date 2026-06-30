package com.pepe.archivosync.di

import com.pepe.archivosync.data.destination.DestinationResolverImpl
import com.pepe.archivosync.data.repository.P2pRepositoryImpl
import com.pepe.archivosync.data.repository.TransferRepositoryImpl
import com.pepe.archivosync.data.settings.SettingsRepositoryImpl
import com.pepe.archivosync.data.source.SafSourceRepository
import com.pepe.archivosync.domain.repository.BackupScheduler
import com.pepe.archivosync.domain.repository.DestinationResolver
import com.pepe.archivosync.domain.repository.P2pRepository
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.domain.repository.SourceRepository
import com.pepe.archivosync.domain.repository.TransferRepository
import com.pepe.archivosync.work.BackupSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository

    @Binds
    @Singleton
    abstract fun bindSourceRepository(impl: SafSourceRepository): SourceRepository

    @Binds
    @Singleton
    abstract fun bindP2pRepository(impl: P2pRepositoryImpl): P2pRepository

    @Binds
    @Singleton
    abstract fun bindDestinationResolver(impl: DestinationResolverImpl): DestinationResolver

    @Binds
    @Singleton
    abstract fun bindBackupScheduler(impl: BackupSchedulerImpl): BackupScheduler
}
