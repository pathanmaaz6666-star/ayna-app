package com.mazo.ayna.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mazo.ayna.MainActivity
import com.mazo.ayna.R
import com.mazo.ayna.brain.AynaVoice
import java.util.concurrent.TimeUnit

/**
 * Reminders: "10 minute baad yaad dilao ke ...".
 * Scheduled with WorkManager (survives app restarts). When fired, shows a
 * notification and speaks the reminder with Ayna's voice.
 */
object ReminderScheduler {
    const val KEY_NOTE = "note"

    fun schedule(context: Context, delayMs: Long, note: String) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_NOTE to note))
            .addTag("ayna_reminder")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** Exact-alarm variant for time-specific reminders (uses AlarmManager). */
    fun scheduleAt(context: Context, triggerAtMillis: Long, note: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(KEY_NOTE, note)
        }
        val pending = PendingIntent.getBroadcast(
            context, note.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val note = inputData.getString(ReminderScheduler.KEY_NOTE) ?: "Reminder"
        ReminderNotifier.show(applicationContext, note)
        return Result.success()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val note = intent?.getStringExtra(ReminderScheduler.KEY_NOTE) ?: "Reminder"
        ReminderNotifier.show(context, note)
    }
}

object ReminderNotifier {
    private const val CHANNEL_ID = "ayna_reminders"

    fun show(context: Context, note: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Ayna reminders", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_reminder))
            .setContentText(note)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(SystemClock.uptimeMillis().toInt(), notification)
        // Speak it too.
        val voice = AynaVoice(context)
        voice.init { voice.speak("Yaad dila rahi hoon: $note") }
    }
}
