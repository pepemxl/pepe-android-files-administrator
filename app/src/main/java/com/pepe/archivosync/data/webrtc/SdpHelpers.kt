package com.pepe.archivosync.data.webrtc

import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Coroutine bridges over WebRTC's callback-based [SdpObserver]. Each helper
 * suspends until the underlying async operation reports success or failure.
 */

suspend fun PeerConnection.createOfferSdp(): SessionDescription =
    suspendCoroutine { cont ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) =
                cont.resumeWithException(IllegalStateException("createOffer: $error"))
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

suspend fun PeerConnection.createAnswerSdp(): SessionDescription =
    suspendCoroutine { cont ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) =
                cont.resumeWithException(IllegalStateException("createAnswer: $error"))
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

suspend fun PeerConnection.setLocalSdp(sdp: SessionDescription): Unit =
    suspendCoroutine { cont ->
        setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) =
                cont.resumeWithException(IllegalStateException("setLocal: $error"))
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

suspend fun PeerConnection.setRemoteSdp(sdp: SessionDescription): Unit =
    suspendCoroutine { cont ->
        setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) =
                cont.resumeWithException(IllegalStateException("setRemote: $error"))
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }
