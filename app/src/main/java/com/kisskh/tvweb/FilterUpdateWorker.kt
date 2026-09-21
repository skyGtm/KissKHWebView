package com.kisskh.tvweb

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FilterUpdateWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params){
    override suspend fun doWork(): Result = runCatching { AdBlocker(applicationContext).updateAll(); Result.success() }.getOrElse { Result.retry() }
}
