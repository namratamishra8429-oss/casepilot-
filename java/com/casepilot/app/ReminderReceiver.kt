package com.casepilot.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val title = intent.getStringExtra("title")
            ?: "CasePilot Reminder"

        val message = intent.getStringExtra("message")
            ?: "You have something that needs your attention."

        val channelId = "casepilot_reminders"

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "CasePilot Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )

            notificationManager.createNotificationChannel(channel)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}

fun scheduleReminder(
    context: Context,
    title: String,
    deadline: String
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

    val formatter = java.text.SimpleDateFormat(
        "dd MMM yyyy",
        java.util.Locale.getDefault()
    )

    val deadlineDate = try {
        formatter.parse(deadline)
    } catch (e: Exception) {
        null
    } ?: return

    val deadlineMillis = deadlineDate.time

    val reminderDays = listOf(
        7 to "7 days left",
        5 to "5 days left",
        1 to "1 day left",
        0 to "Due today",
                -1 to "1 day overdue"
    )

    for ((daysBefore, reminderLabel) in reminderDays) {

        val reminderCalendar = java.util.Calendar.getInstance()
        reminderCalendar.timeInMillis = deadlineMillis

        reminderCalendar.add(
            java.util.Calendar.DAY_OF_YEAR,
            -daysBefore
        )

        // CasePilot reminder time = 9:00 AM
        reminderCalendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            9
        )

        reminderCalendar.set(
            java.util.Calendar.MINUTE,
            0
        )

        reminderCalendar.set(
            java.util.Calendar.SECOND,
            0
        )

        reminderCalendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        val triggerTime = reminderCalendar.timeInMillis

        // Past reminders ko schedule nahi karna
        if (triggerTime <= System.currentTimeMillis()) {
            continue
        }

        val intent = android.content.Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            putExtra(
                "title",
                title
            )

            putExtra(
                "message",
                "$title — $reminderLabel. Open CasePilot to handle it."
            )
        }

        val requestCode =
            ("$deadlineMillis-$daysBefore-$title").hashCode()

        val pendingIntent =
            android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }
}

fun cancelReminders(
    context: Context,
    title: String,
    deadline: String
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

    val formatter = java.text.SimpleDateFormat(
        "dd MMM yyyy",
        java.util.Locale.getDefault()
    )

    val deadlineDate = try {
        formatter.parse(deadline)
    } catch (e: Exception) {
        null
    } ?: return

    val deadlineMillis = deadlineDate.time

    val reminderDays = listOf(
        7,
        5,
        1,
        0,
        -1
    )

    for (daysBefore in reminderDays) {
        val requestCode =
            ("$deadlineMillis-$daysBefore-$title").hashCode()

        val intent = android.content.Intent(
            context,
            ReminderReceiver::class.java
        )

        val pendingIntent =
            android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
            )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}