package com.hereliesaz.ideaz.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.NotificationManagerCompat
import com.hereliesaz.ideaz.BubbleActivity
import com.hereliesaz.ideaz.MainActivity
import com.hereliesaz.ideaz.R

object BubbleUtils {

    private const val CHANNEL_ID = "ideaz_bubble_channel"
    private const val NOTIFICATION_ID = 1001
    private const val SHORTCUT_ID = "ideaz_bubble_shortcut"

    fun createBubbleNotification(context: Context) {
        createNotificationChannel(context)

        val target = Intent(context, BubbleActivity::class.java)
        val bubbleIntent = PendingIntent.getActivity(
            context,
            0,
            target,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val bubbleData = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
            .setDesiredHeight(600)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        val person = Person.Builder()
            .setName("IDEaz")
            .setIcon(icon)
            .setBot(true)
            .setImportant(true)
            .build()

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setLongLabel("IDEaz Overlay")
            .setShortLabel("IDEaz")
            .setIcon(icon)
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .setPerson(person)
            .setAlwaysBadged()
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("IDEaz Overlay")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setBubbleMetadata(bubbleData)
            .setShortcutId(SHORTCUT_ID)
            .addPerson(person)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setStyle(NotificationCompat.MessagingStyle(person)
                .setConversationTitle("IDEaz")
                .addMessage("Tap to open IDE", System.currentTimeMillis(), person)
            )

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "IDEaz Bubbles"
            val descriptionText = "Channel for IDEaz overlay bubble"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}