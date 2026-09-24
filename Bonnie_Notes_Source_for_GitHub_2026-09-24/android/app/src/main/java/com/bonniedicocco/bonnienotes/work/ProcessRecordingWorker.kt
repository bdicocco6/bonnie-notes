package com.bonniedicocco.bonnienotes.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bonniedicocco.bonnienotes.data.AppDatabase
import com.bonniedicocco.bonnienotes.network.ApiFactory
import java.io.File

class ProcessRecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val dao = AppDatabase.get(applicationContext).meetingDao()
        val meeting = dao.get(id) ?: return Result.failure()
        val audio = File(meeting.audioPath)
        if (!audio.exists()) {
            dao.update(meeting.copy(status = "FAILED", error = "Audio file is missing"))
            return Result.failure()
        }
        dao.update(meeting.copy(status = "PROCESSING", error = null))
        return try {
            val response = ApiFactory.api.process(
                ApiFactory.audioPart(audio),
                ApiFactory.textFields(mapOf("id" to id, "title" to meeting.title, "recordedAt" to meeting.recordedAt))
            )
            dao.update(meeting.copy(
                status = "READY",
                transcript = response.transcript,
                segmentsJson = ApiFactory.gson.toJson(response.segments),
                tasksJson = ApiFactory.gson.toJson(response.tasks),
                driveUrl = response.drive?.note?.webViewLink,
                error = null
            ))
            Result.success()
        } catch (error: Exception) {
            dao.update(meeting.copy(status = "FAILED", error = error.message ?: "Processing failed"))
            Result.retry()
        }
    }

    companion object { const val KEY_MEETING_ID = "meeting_id" }
}

