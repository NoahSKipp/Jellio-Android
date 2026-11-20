package com.jellio.android.data.books

enum class ChaptarrStatus {
    LOADING,
    CONFIGURED,
    NOT_CONFIGURED,
    UNREACHABLE,
}

// "Not configured" is only what the server said. A request still in flight
// or one that failed (timeout, 502 while Chaptarr is down) is not that.
fun resolveChaptarrStatus(response: BookRequestsResponseDto?, failure: Throwable?): ChaptarrStatus = when {
    response != null -> if (response.Configured) ChaptarrStatus.CONFIGURED else ChaptarrStatus.NOT_CONFIGURED
    failure != null -> ChaptarrStatus.UNREACHABLE
    else -> ChaptarrStatus.LOADING
}
