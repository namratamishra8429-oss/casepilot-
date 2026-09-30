package com.casepilot.app

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.os.Bundle
import android.os.Build

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casepilot.app.ui.theme.CasePilotTheme
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.CustomerInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBar


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CasePilotTheme {
                CasePilotApp()
            }
        }
    }
}

data class CaseItem(
    val title: String,
    val category: String,
    val deadline: String,
    val priority: String,
    val action: String,
    val status: String = "Open",
    val progress: Int = 0,
    val attachmentUri: String? = null,
    val documentText: String? = null,
    val followUpDate: String? = null,
    val followUpNote: String? = null
)

fun extractTextFromImage(
    context: Context,
    imageUri: android.net.Uri,
    onResult: (String) -> Unit
) {
    val image = try {
        com.google.mlkit.vision.common.InputImage.fromFilePath(
            context,
            imageUri
        )
    } catch (e: Exception) {
        onResult("")
        return
    }

    val recognizer =
        com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )

    recognizer.process(image)
        .addOnSuccessListener { result ->
            onResult(result.text)
        }
        .addOnFailureListener {
            onResult("")
        }
}

data class DocumentInsights(
    val deadline: String? = null,
    val amount: String? = null,
    val organization: String? = null,
    val requiredAction: String? = null,
    val priority: String = "Medium",
    val category: String = "Other"
)

fun analyzeDocumentText(text: String): DocumentInsights {
    if (text.isBlank()) {
        return DocumentInsights()
    }

    val normalizedText = text.replace("\n", " ")

    val dateRegex = Regex(
        """\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b"""
    )

    val amountRegex = Regex(
        """(?:₹|Rs\.?|INR)\s?[\d,]+(?:\.\d{1,2})?"""
    )

    val deadline = dateRegex.find(normalizedText)?.value
    val amount = amountRegex.find(normalizedText)?.value

    val organization = when {
        normalizedText.contains("passport", ignoreCase = true) ->
            "Passport Office"

        normalizedText.contains("income tax", ignoreCase = true) ->
            "Income Tax Department"

        normalizedText.contains("university", ignoreCase = true) ->
            "University"

        normalizedText.contains("bank", ignoreCase = true) ->
            "Bank"

        else -> null
    }

    val requiredAction = when {
        normalizedText.contains("submit", ignoreCase = true) ->
            "Submit the required documents"

        normalizedText.contains("pay", ignoreCase = true) ->
            "Make the required payment"

        normalizedText.contains("renew", ignoreCase = true) ->
            "Complete the renewal"

        normalizedText.contains("apply", ignoreCase = true) ->
            "Complete the application"

        normalizedText.contains("reply", ignoreCase = true) ->
            "Send a reply"

        else -> "Review and complete the required action"
    }

    val priority = when {
        normalizedText.contains("urgent", ignoreCase = true) ||
                normalizedText.contains("immediately", ignoreCase = true) ||
                normalizedText.contains("final notice", ignoreCase = true) ->
            "High"

        normalizedText.contains("important", ignoreCase = true) ||
                normalizedText.contains("deadline", ignoreCase = true) ->
            "Medium"

        else ->
            "Low"
    }

    val category = if (
        normalizedText.contains("passport", ignoreCase = true) ||
        normalizedText.contains("certificate", ignoreCase = true) ||
        normalizedText.contains("document", ignoreCase = true) ||
        normalizedText.contains("application", ignoreCase = true)
    ) {
        "Documents"
    } else {
        "Other"
    }

    return DocumentInsights(
        deadline = deadline,
        amount = amount,
        organization = organization,
        requiredAction = requiredAction,
        priority = priority,
                category = category
    )
}

@OptIn(ExperimentalMaterial3Api::class)
fun showTestNotification(context: Context) {
    val channelId = "casepilot_reminders"

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "CasePilot Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(channel)
    }

    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("CasePilot Reminder")
        .setContentText("You have something that needs your attention.")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001
        )
        return
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(1001, notification)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasePilotApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = context.getSharedPreferences(
        "casepilot",
        Context.MODE_PRIVATE
    )

    var selectedDocumentUri by remember {
        mutableStateOf<android.net.Uri?>(null)
    }

    var documentText by remember {
        mutableStateOf("")
    }

    var documentDeadline by remember {
        mutableStateOf("")
    }

    var documentPriority by remember {
        mutableStateOf("")
    }

    var documentAction by remember {
        mutableStateOf("")
    }

    var documentAmount by remember {
        mutableStateOf("")
    }

    var documentInsights by remember {
        mutableStateOf<DocumentInsights?>(null)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedDocumentUri = uri

        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission may not be persistable for every provider
            }

            val mimeType = context.contentResolver.getType(uri)

            if (mimeType?.startsWith("image/") == true) {
                extractTextFromImage(
                    context = context,
                    imageUri = uri
                ) { text ->
                    documentText = text

                    val insights = analyzeDocumentText(text)

                    documentInsights = insights

                    documentDeadline = insights.deadline ?: ""
                    documentPriority = insights.priority
                    documentAction = insights.requiredAction ?: ""
                    documentAmount = insights.amount ?: ""
                }

            } else {
                documentText = ""
                documentInsights = null
            }
        }
    }

    var showAddMessage by remember {
        mutableStateOf(false)
    }

    var showChat by remember {
        mutableStateOf(false)
    }

    var showPaywall by remember {
        mutableStateOf(false)
    }

    var isProUser by remember { mutableStateOf(false) }
    var showTaskForm by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var taskDeadline by remember { mutableStateOf("") }
    var taskAction by remember { mutableStateOf("Complete task") }
    var taskPriority by remember { mutableStateOf("MEDIUM") }
    var taskCategory by remember { mutableStateOf("Other") }
    var deadlineMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(documentInsights) {
        documentInsights?.deadline?.let {
            taskDeadline = it
        }

        documentInsights?.organization?.let {
            taskTitle = it
        }

        documentInsights?.requiredAction?.let {
            taskAction = it
        }

        documentInsights?.priority?.let {
            taskPriority = it
        }

        documentInsights?.category?.let {
            taskCategory = it
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    var selectedCategory by remember { mutableStateOf("All") }

    var handledCount by remember { mutableStateOf(prefs.getInt("handled_count", 12)) }

    var selectedCase by remember { mutableStateOf<CaseItem?>(null) }

    var history by remember {
        mutableStateOf<List<CaseItem>>(emptyList())
    }

    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedHistory = prefs.getString("history_data", null)

        if (!savedHistory.isNullOrBlank()) {
            history = savedHistory.split("\u001E").mapNotNull { record ->
                val parts = record.split("\u001F")

                if (parts.size >= 5) {
                    CaseItem(
                        title = parts[0],
                        category = parts[1],
                        deadline = parts[2],
                        priority = parts[3],
                        action = parts[4],
                        status = if (parts.size >= 9) parts[5] else "Completed",
                        progress = if (parts.size >= 9) {
                            parts[6].toIntOrNull() ?: 100
                        } else {
                            100
                        },
                        attachmentUri = if (parts.size >= 9) parts[7] else null,
                        documentText = if (parts.size >= 9) parts[8] else null
                    )
                } else {
                    null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    isProUser =
                        customerInfo.entitlements["casepilot_pro"]?.isActive == true
                }

                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                    isProUser = false
                }
            }
        )
    }

    val cases = remember {
        mutableStateListOf<CaseItem>().apply {
            val saved = prefs.getString("cases_data", null)

            if (saved != null) {
                saved.split("\u001E").forEach { record ->
                    val parts = record.split("\u001F")

                    if (parts.size >= 5) {
                        add(
                            CaseItem(
                                title = parts[0],
                                category = parts[1],
                                deadline = parts[2],
                                priority = parts[3],
                                action = parts[4],
                                status = if (parts.size >= 9) parts[5] else "Open",
                                progress = if (parts.size >= 9) {
                                    parts[6].toIntOrNull() ?: 0
                                } else {
                                    0
                                },
                                attachmentUri = if (parts.size >= 9) {
                                    parts[7]
                                } else {
                                    parts.getOrNull(5)
                                },
                                documentText = if (parts.size >= 9) {
                                    parts[8]
                                } else {
                                    parts.getOrNull(6)
                                },
                                followUpDate = if (parts.size >= 11) {
                                    parts[9].ifBlank { null }
                                } else {
                                    null
                                },
                                followUpNote = if (parts.size >= 11) {
                                    parts[10].ifBlank { null }
                                } else {
                                    null
                                }
                            )
                        )
                    }
                }
            } else {
                add(
                    CaseItem(
                        title = "College Fee Payment",
                        category = "Education",
                        deadline = "Due tomorrow",
                        priority = "HIGH",
                        action = "Pay fee"
                    )
                )

                add(
                    CaseItem(
                        title = "Internship Application",
                        category = "Career",
                        deadline = "Due in 3 days",
                        priority = "MEDIUM",
                        action = "Complete application"
                    )
                )

                add(
                    CaseItem(
                        title = "Passport Document",
                        category = "Documents",
                        deadline = "Due in 8 days",
                        priority = "MEDIUM",
                        action = "Upload document"
                    )
                )
            }
        }
    }

    if (selectedCase != null) {
        CaseDetailScreen(
            item = selectedCase!!,
            onBack = {
                selectedCase = null
            },
            onComplete = {
                val completedCase = selectedCase

                if (completedCase != null) {
                    cases.remove(completedCase)
                    history = history + completedCase

                    handledCount += 1
                    prefs.edit()
                        .putInt("handled_count", handledCount)
                        .apply()

                    val serialized = cases.joinToString("\u001E") {
                        listOf(
                            it.title,
                            it.category,
                            it.deadline,
                            it.priority,
                            it.action,
                            it.status,
                            it.progress.toString(),
                            it.attachmentUri ?: "",
                            it.documentText ?: "",
                            it.followUpDate ?: "",
                            it.followUpNote ?: ""
                        ).joinToString("\u001F")
                    }

                    prefs.edit()
                        .putString("cases_data", serialized)
                        .apply()
                }

                selectedCase = null
            }
        )

        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CasePilot",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your life, organised.",
                            fontSize = 12.sp
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Good evening 👋",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Let's get your important things handled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "TODAY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "3 things need your attention",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "CasePilot finds deadlines, documents and actions before they become problems.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                showAddMessage = !showAddMessage
                            }
                        ) {
                            Text("Add something")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        showChat = !showChat
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CasePilot AI Chat")
                }
            }

            item {
                Button(
                    onClick = {
                        showHistory = !showHistory
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("History")
                }
            }

            item {
                if (isProUser) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("⭐ CasePilot Pro Active")
                            Text("Your Pro access is enabled.")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            showPaywall = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⭐ Upgrade to Pro")
                    }
                }
            }

            if (showPaywall) {
                item {
                    PaywallDialog(
                        PaywallDialogOptions.Builder()
                            .setRequiredEntitlementIdentifier("casepilot_pro")
                            .setListener(
                                object : PaywallListener {
                                    override fun onPurchaseCompleted(
                                        customerInfo: com.revenuecat.purchases.CustomerInfo,
                                        storeTransaction: com.revenuecat.purchases.models.StoreTransaction
                                    ) {
                                        isProUser =
                                            customerInfo.entitlements["casepilot_pro"]?.isActive == true

                                        showPaywall = false
                                    }

                                    override fun onRestoreCompleted(
                                        customerInfo: com.revenuecat.purchases.CustomerInfo
                                    ) {
                                        isProUser =
                                            customerInfo.entitlements["casepilot_pro"]?.isActive == true

                                        showPaywall = false
                                    }
                                }
                            )
                            .build()
                    )
                }
            }

            if (showHistory) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Completed Cases",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (history.isEmpty()) {
                                Text(
                                    text = "No completed cases yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                history.forEach { historyItem ->
                                    Text(
                                        text = historyItem.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = "${historyItem.category} • ${historyItem.deadline}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            }

            val caseContext = cases.joinToString(
                separator = "\n"
            ) { caseItem ->
                "- ${caseItem.title} | ${caseItem.category} | Deadline: ${caseItem.deadline} | Priority: ${caseItem.priority} | Status: ${caseItem.status}"
            }

            if (showChat) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(size = 18.dp)
                    ) {
                        var chatInput by remember { mutableStateOf("") }
                        val coroutineScope = rememberCoroutineScope()

                        var chatReply by remember {
                            mutableStateOf(
                                value = "Hi! I'm CasePilot AI.\n\nYour current cases:\n$caseContext"
                            )
                        }


                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(
                                text = "CasePilot AI Chat",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = chatReply,
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text("Ask CasePilot anything...")
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (chatInput.isNotBlank()) {
                                        val message = chatInput.trim()
                                        val lowerMessage = message.lowercase()
                                        val completeCommand =
                                            lowerMessage.contains("complete case") ||
                                                    lowerMessage.contains("mark case complete") ||
                                                    lowerMessage.contains("finish case")

                                        if (completeCommand) {

                                            val matchedCase = cases.firstOrNull { caseItem ->
                                                lowerMessage.contains(
                                                    caseItem.title.lowercase()
                                                )
                                            }

                                            if (matchedCase != null) {

                                                cancelReminders(
                                                    context = context,
                                                    title = matchedCase.title,
                                                    deadline = matchedCase.deadline
                                                )

                                                cases.remove(matchedCase)

                                                history = history + matchedCase.copy(
                                                    status = "Completed",
                                                    progress = 100
                                                )

                                                handledCount += 1

                                                prefs.edit()
                                                    .putInt(
                                                        "handled_count",
                                                        handledCount
                                                    )
                                                    .apply()

                                                val serializedCases = cases.joinToString("\u001E") {
                                                    listOf(
                                                        it.title,
                                                        it.category,
                                                        it.deadline,
                                                        it.priority,
                                                        it.action,
                                                        it.status,
                                                        it.progress.toString(),
                                                        it.attachmentUri ?: "",
                                                        it.documentText ?: "",
                                                        it.followUpDate ?: "",
                                                        it.followUpNote ?: ""
                                                    ).joinToString("\u001F")
                                                }

                                                prefs.edit()
                                                    .putString(
                                                        "cases_data",
                                                        serializedCases
                                                    )
                                                    .apply()

                                                chatReply =
                                                    "Case completed successfully: ${matchedCase.title}"

                                            } else {

                                                chatReply =
                                                    "I couldn't find a matching case. Please mention the case name."
                                            }

                                            chatInput = ""

                                            return@Button
                                        }

                                        if (
                                            lowerMessage.contains("create case") ||
                                            lowerMessage.contains("create a case") ||
                                            lowerMessage.contains("add case") ||
                                            lowerMessage.contains("add a case")
                                        ) {
                                            val caseTitle = message
                                                .replace(
                                                    Regex("(?i)create\\s+(a\\s+)?case"),
                                                    ""
                                                )
                                                .replace(
                                                    Regex("(?i)add\\s+(a\\s+)?case"),
                                                    ""
                                                )
                                                .trim()
                                                .ifBlank {
                                                    "New Case"
                                                }

                                            val isReminderCommand =
                                                lowerMessage.contains("remind me") ||
                                                        lowerMessage.contains("reminder") ||
                                                        lowerMessage.contains("set reminder")

                                            if (isReminderCommand) {

                                                val reminderTitle = message
                                                    .replace(
                                                        Regex("(?i)remind\\s+me\\s+about\\s*"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex("(?i)remind\\s+me\\s*"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex("(?i)set\\s+reminder\\s*"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex("(?i)reminder\\s*"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex("(?i)\\s+(today|tomorrow)\\s*$"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex("(?i)\\s+in\\s+\\d+\\s+days?\\s*"),
                                                        ""
                                                    )
                                                    .replace(
                                                        Regex(
                                                            "(?i)\\s+(next|on)\\s+" +
                                                                    "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s*"
                                                        ),
                                                        ""
                                                    )
                                                    .trim()
                                                    .ifBlank {
                                                        "CasePilot reminder"
                                                    }

                                                val reminderCalendar =
                                                    java.util.Calendar.getInstance()

                                                if (lowerMessage.contains("tomorrow")) {
                                                    reminderCalendar.add(
                                                        java.util.Calendar.DAY_OF_YEAR,
                                                        1
                                                    )
                                                }

                                                val daysMatch = Regex(
                                                    "(?i)in\\s+(\\d+)\\s+days?"
                                                ).find(lowerMessage)

                                                if (daysMatch != null) {
                                                    val daysToAdd =
                                                        daysMatch.groupValues[1].toIntOrNull() ?: 0

                                                    if (daysToAdd > 0) {
                                                        reminderCalendar.add(
                                                            java.util.Calendar.DAY_OF_YEAR,
                                                            daysToAdd
                                                        )
                                                    }
                                                }

                                                val weekdays = mapOf(
                                                    "monday" to java.util.Calendar.MONDAY,
                                                    "tuesday" to java.util.Calendar.TUESDAY,
                                                    "wednesday" to java.util.Calendar.WEDNESDAY,
                                                    "thursday" to java.util.Calendar.THURSDAY,
                                                    "friday" to java.util.Calendar.FRIDAY,
                                                    "saturday" to java.util.Calendar.SATURDAY,
                                                    "sunday" to java.util.Calendar.SUNDAY
                                                )

                                                for ((dayName, dayOfWeek) in weekdays) {
                                                    if (
                                                        lowerMessage.contains("next $dayName") ||
                                                        lowerMessage.contains("on $dayName")
                                                    ) {
                                                        val currentDay =
                                                            reminderCalendar.get(java.util.Calendar.DAY_OF_WEEK)

                                                        var daysUntil =
                                                            (dayOfWeek - currentDay + 7) % 7

                                                        if (daysUntil == 0) {
                                                            daysUntil = 7
                                                        }

                                                        reminderCalendar.add(
                                                            java.util.Calendar.DAY_OF_YEAR,
                                                            daysUntil
                                                        )

                                                        break
                                                    }
                                                }

                                                val reminderDateFormat =
                                                    java.text.SimpleDateFormat(
                                                        "dd MMM yyyy",
                                                        java.util.Locale.ENGLISH
                                                    )

                                                val reminderDeadline =
                                                    reminderDateFormat.format(
                                                        reminderCalendar.time
                                                    )

                                                scheduleReminder(
                                                    context = context,
                                                    title = reminderTitle,
                                                    deadline = reminderDeadline
                                                )

                                                chatReply =
                                                    "Reminder set for $reminderTitle on $reminderDeadline"

                                                chatInput = ""

                                                return@Button
                                            }

                                            cases.add(
                                                CaseItem(
                                                    title = caseTitle,
                                                    category = "Other",
                                                    deadline = "No deadline",
                                                    priority = "MEDIUM",
                                                    action = "Complete task"
                                                )
                                            )

                                            val serialized = cases.joinToString("\u001E") {
                                                listOf(
                                                    it.title,
                                                    it.category,
                                                    it.deadline,
                                                    it.priority,
                                                    it.action,
                                                    it.status,
                                                    it.progress.toString(),
                                                    it.attachmentUri ?: "",
                                                    it.documentText ?: "",
                                                    it.followUpDate ?: "",
                                                    it.followUpNote ?: ""
                                                ).joinToString("\u001F")
                                            }

                                            prefs.edit()
                                                .putString("cases_data", serialized)
                                                .apply()

                                            chatReply = "Case created successfully: $caseTitle"
                                            chatInput = ""
                                            return@Button
                                        }

                                        chatInput = ""
                                        chatReply = "Thinking..."

                                        coroutineScope.launch {
                                            chatReply = AiApi.askCasePilot(
                                                userMessage = message,
                                                caseContext = caseContext
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Send")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    showChat = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close Chat")
                            }
                        }
                    }
                }
            }

            if (showTaskForm) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(size = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(all = 18.dp)
                        ) {
                            Text(
                                text = "Create a task",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Task name") },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = taskDeadline,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Deadline") },
                                placeholder = { Text("Select deadline date") },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    showDatePicker = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Deadline")
                            }

                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState(
                                    initialSelectedDateMillis = deadlineMillis
                                        ?: System.currentTimeMillis()
                                )

                                DatePickerDialog(
                                    onDismissRequest = {
                                        showDatePicker = false
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                datePickerState.selectedDateMillis?.let { selectedDate ->
                                                    deadlineMillis = selectedDate
                                                    taskDeadline =
                                                        java.text.SimpleDateFormat(
                                                            "dd MMM yyyy",
                                                            java.util.Locale.getDefault()
                                                        ).format(java.util.Date(selectedDate))
                                                }

                                                showDatePicker = false
                                            }
                                        ) {
                                            Text("OK")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                showDatePicker = false
                                            }
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                ) {
                                    DatePicker(
                                        state = datePickerState
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (taskTitle.isNotBlank()) {
                                            cases.add(
                                                CaseItem(
                                                    title = taskTitle,
                                                    category = if (documentInsights?.category != null &&
                                                        documentInsights?.category != "Other"
                                                    ) {
                                                        documentInsights?.category ?: taskCategory
                                                    } else {
                                                        taskCategory
                                                    },
                                                    deadline = if (documentDeadline.isNotBlank()) {
                                                        documentDeadline
                                                    } else {
                                                        taskDeadline
                                                    },
                                                    priority = if (documentPriority.isNotBlank()) {
                                                        documentPriority
                                                    } else {
                                                        taskPriority
                                                    },
                                                    action = if (documentAction.isNotBlank()) {
                                                        documentAction
                                                    } else {
                                                        taskAction
                                                    },
                                                    attachmentUri = selectedDocumentUri?.toString(),
                                                    documentText = documentText
                                                )
                                            )

                                            scheduleReminder(
                                                context = context,
                                                title = taskTitle,
                                                deadline = taskDeadline
                                            )

                                            val serialized = cases.joinToString("\u001E") {
                                                listOf(
                                                    it.title,
                                                    it.category,
                                                    it.deadline,
                                                    it.priority,
                                                    it.action,
                                                    it.status,
                                                    it.progress.toString(),
                                                    it.attachmentUri ?: "",
                                                    it.documentText ?: ""
                                                ).joinToString("\u001F")
                                            }

                                            prefs.edit()
                                                .putString("cases_data", serialized)
                                                .apply()
                                        }

                                        showTaskForm = false
                                        taskTitle = ""
                                        taskDeadline = ""
                                        documentDeadline = ""
                                        documentPriority = ""
                                        documentAction = ""
                                        documentAmount = ""
                                    }
                                ) {
                                    Text("Save Task")
                                }

                                OutlinedButton(
                                    onClick = {
                                        showTaskForm = false
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
            if (showAddMessage) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(
                                text = "What do you want to add?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        documentPicker.launch(
                                            arrayOf("application/pdf", "image/*")
                                        )
                                    }
                                ) {
                                    Text("Document")
                                }

                                if (selectedDocumentUri != null) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "✓ ${selectedDocumentUri?.lastPathSegment ?: "Document attached"}",
                                        fontSize = 14.sp
                                    )
                                    if (documentText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "EXTRACTED DOCUMENT TEXT",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = documentText,
                                            fontSize = 14.sp
                                        )
                                    }

                                    if (documentInsights != null) {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "DOCUMENT INSIGHTS",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Deadline: ${documentInsights?.deadline ?: "Not detected"}",
                                            fontSize = 14.sp
                                        )

                                        Text(
                                            text = "Amount: ${documentInsights?.amount ?: "Not detected"}",
                                            fontSize = 14.sp
                                        )

                                        Text(
                                            text = "Organization: ${documentInsights?.organization ?: "Not detected"}",
                                            fontSize = 14.sp
                                        )

                                        Text(
                                            text = "Required Action: ${documentInsights?.requiredAction ?: "Not detected"}",
                                            fontSize = 14.sp
                                        )

                                        Text(
                                            text = "Priority: ${documentInsights?.priority ?: "Not detected"}",
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        showAddMessage = false
                                        showTaskForm = true
                                    }
                                ) {
                                    Text("Task")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Needs attention",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {


                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search cases") },
                    placeholder = { Text("Search by case name") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedCategory = "All" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("All")
                    }

                    Button(
                        onClick = { selectedCategory = "Documents" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Documents")
                    }

                    Button(
                        onClick = { selectedCategory = "Other" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Other")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            items(
                cases.filter { caseItem ->
                    val matchesSearch =
                        caseItem.title.contains(searchQuery, ignoreCase = true)

                    val matchesCategory =
                        selectedCategory == "All" ||
                                caseItem.category.equals(
                                    selectedCategory,
                                    ignoreCase = true
                                )

                    matchesSearch && matchesCategory
                }
            ) { item ->
                CaseCard(
                    item = item,
                    onClick = {
                        selectedCase = item
                    },
                    onComplete = {

                        cancelReminders(
                            context = context,
                            title = item.title,
                            deadline = item.deadline
                        )

                        val historyData = prefs.getString("history_data", "") ?: ""

                        val historyEntry = listOf(
                            item.title,
                            item.category,
                            item.deadline,
                            item.priority,
                            item.action,
                            item.status,
                            item.progress.toString(),
                            item.attachmentUri ?: "",
                            item.documentText ?: ""
                        ).joinToString("\u001F")

                        val updatedHistory =
                            if (historyData.isBlank()) {
                                historyEntry
                            } else {
                                "$historyData\u001E$historyEntry"
                            }

                        prefs.edit()
                            .putString("history_data", updatedHistory)
                            .apply()

                        cases.remove(item)

                        handledCount += 1

                        prefs.edit()
                            .putInt("handled_count", handledCount)
                            .apply()

                        val serialized = cases.joinToString("\u001E") {
                            listOf(
                                it.title,
                                it.category,
                                it.deadline,
                                it.priority,
                                it.action
                            ).joinToString("\u001F")
                        }

                        prefs.edit()
                            .putString("cases_data", serialized)
                            .apply()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your CasePilot",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallStatCard(
                        number = handledCount.toString(),
                        label = "Handled",
                        modifier = Modifier.weight(1f)
                    )

                    SmallStatCard(
                        number = cases.size.toString(),
                        label = "Open",
                        modifier = Modifier.weight(1f)
                    )

                    SmallStatCard(
                        number = "86%",
                        label = "On track",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "CasePilot",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun CaseDetailScreen(
    item: CaseItem,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {

    val context = LocalContext.current

    val prefs = context.getSharedPreferences(
        "casepilot_cases",
        Context.MODE_PRIVATE
    )

    var step1Done by remember {
        mutableStateOf(
            prefs.getBoolean("step1_${item.title}", false)
        )
    }

    var step2Done by remember {
        mutableStateOf(
            prefs.getBoolean("step2_${item.title}", false)
        )
    }

    var step3Done by remember {
        mutableStateOf(
            prefs.getBoolean("step3_${item.title}", false)
        )
    }

    var followUpDate by remember {
        mutableStateOf(item.followUpDate ?: "")
    }

    var followUpNote by remember {
        mutableStateOf(item.followUpNote ?: "")
    }

    var showFollowUpDatePicker by remember {
        mutableStateOf(false)
    }

    var doItWithMeStep by remember {
        mutableStateOf(0)
    }

    val currentProgress = when {
        step1Done && step2Done && step3Done -> 100
        step1Done && step2Done -> 67
        step1Done -> 33
        else -> 0
    }

    LaunchedEffect(currentProgress) {
        val savedCases = prefs.getString("cases_data", null)

        if (!savedCases.isNullOrBlank()) {
            val updatedCases = savedCases
                .split("\u001E")
                .map { record ->
                    val parts = record.split("\u001F").toMutableList()

                    if (parts.isNotEmpty() && parts[0] == item.title) {
                        while (parts.size < 11) {
                            parts.add("")
                        }

                        parts[5] = item.status
                        parts[6] = currentProgress.toString()
                    }

                    parts.joinToString("\u001F")
                }
                .joinToString("\u001E")

            prefs.edit()
                .putString("cases_data", updatedCases)
                .apply()
        }
    }

    val deadlineStatus = try {
        val dateFormat = java.text.SimpleDateFormat(
            "dd MMM yyyy",
            java.util.Locale.ENGLISH
        )
        dateFormat.isLenient = false

        val deadlineDate = dateFormat.parse(item.deadline)

        val todayCalendar = java.util.Calendar.getInstance()
        todayCalendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            0
        )
        todayCalendar.set(
            java.util.Calendar.MINUTE,
            0
        )
        todayCalendar.set(
            java.util.Calendar.SECOND,
            0
        )
        todayCalendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        val todayMillis = todayCalendar.timeInMillis
        val deadlineMillis = deadlineDate?.time

        when {
            deadlineMillis == null -> "NO DEADLINE"
            deadlineMillis < todayMillis -> "OVERDUE"
            deadlineMillis == todayMillis -> "DUE TODAY"
            deadlineMillis <= todayMillis +
                    (7L * 24L * 60L * 60L * 1000L) -> "DUE SOON"
            else -> "ON TRACK"
        }
    } catch (e: Exception) {
        "NO DEADLINE"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Case Details",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = item.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.category,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.status,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "PROGRESS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${currentProgress}%",
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = currentProgress / 100f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "DO IT WITH ME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val guidedStep = when {
                        !step1Done -> 1
                        !step2Done -> 2
                        !step3Done -> 3
                        else -> 3
                    }

                    Text(
                        text = when {
                            currentProgress == 100 ->
                                "All steps are completed. Your case is ready to close."

                            guidedStep == 1 ->
                                "Let's complete Step 1 together: Prepare documents."

                            guidedStep == 2 ->
                                "Step 1 is done. Now let's submit the application."

                            else ->
                                "Almost there. Let's track the application."
                        },
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            when {
                                !step1Done -> {
                                    step1Done = true
                                    doItWithMeStep = 1

                                    prefs.edit()
                                        .putBoolean(
                                            "step1_${item.title}",
                                            true
                                        )
                                        .apply()
                                }

                                !step2Done -> {
                                    step2Done = true
                                    doItWithMeStep = 2

                                    prefs.edit()
                                        .putBoolean(
                                            "step2_${item.title}",
                                            true
                                        )
                                        .apply()
                                }

                                !step3Done -> {
                                    step3Done = true
                                    doItWithMeStep = 3

                                    prefs.edit()
                                        .putBoolean(
                                            "step3_${item.title}",
                                            true
                                        )
                                        .apply()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = when {
                                currentProgress == 100 -> "Completed"
                                !step1Done -> "Start Step 1"
                                !step2Done -> "Continue to Step 2"
                                else -> "Complete Step 3"
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "STEPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (step1Done) "✓ Step 1 — Prepare documents"
                        else "Step 1 — Prepare documents",
                        fontSize = 16.sp
                    )

                    Button(
                        onClick = {
                            step1Done = !step1Done

                            prefs.edit()
                                .putBoolean(
                                    "step1_${item.title}",
                                    step1Done
                                )
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (step1Done) "Completed"
                            else "Mark Step 1 Done"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = "FOLLOW-UP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (followUpDate.isBlank()) {
                                    "No follow-up scheduled"
                                } else {
                                    "Follow-up: $followUpDate"
                                },
                                fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    showFollowUpDatePicker = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (followUpDate.isBlank()) {
                                        "Choose Follow-up Date"
                                    } else {
                                        "Change Follow-up Date"
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = followUpNote,
                                onValueChange = {
                                    followUpNote = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Follow-up note")
                                },
                                placeholder = {
                                    Text("e.g. Ask for application status")
                                },
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val casePrefs = context.getSharedPreferences(
                                        "casepilot",
                                        Context.MODE_PRIVATE
                                    )

                                    val savedCases = casePrefs.getString("cases_data", null)

                                    if (!savedCases.isNullOrBlank()) {
                                        val updatedCases = savedCases
                                            .split("\u001E")
                                            .map { record ->
                                                val parts = record.split("\u001F").toMutableList()

                                                if (parts.isNotEmpty() && parts[0] == item.title) {
                                                    while (parts.size < 11) {
                                                        parts.add("")
                                                    }

                                                    parts[9] = followUpDate
                                                    parts[10] = followUpNote
                                                }

                                                parts.joinToString("\u001F")
                                            }
                                            .joinToString("\u001E")

                                        casePrefs.edit()
                                            .putString("cases_data", updatedCases)
                                            .apply()
                                    }

                                    showFollowUpDatePicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Save Follow-up")
                            }
                        }

                        if (showFollowUpDatePicker) {

                            val followUpDatePickerState =
                                rememberDatePickerState()

                            DatePickerDialog(
                                onDismissRequest = {
                                    showFollowUpDatePicker = false
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            followUpDatePickerState.selectedDateMillis?.let { millis ->

                                                val calendar =
                                                    java.util.Calendar.getInstance()

                                                calendar.timeInMillis = millis

                                                val dateFormat =
                                                    java.text.SimpleDateFormat(
                                                        "dd MMM yyyy",
                                                        java.util.Locale.ENGLISH
                                                    )

                                                followUpDate =
                                                    dateFormat.format(calendar.time)
                                            }

                                            showFollowUpDatePicker = false
                                        }
                                    ) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showFollowUpDatePicker = false
                                        }
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            ) {
                                DatePicker(
                                    state = followUpDatePickerState
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (step2Done) "✓ Step 2 — Submit application"
                        else "Step 2 — Submit application",
                        fontSize = 16.sp
                    )

                    Button(
                        onClick = {
                            step2Done = !step2Done

                            prefs.edit()
                                .putBoolean(
                                    "step2_${item.title}",
                                    step2Done
                                )
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (step2Done) "Completed"
                            else "Mark Step 2 Done"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (step3Done) "✓ Step 3 — Track application"
                        else "Step 3 — Track application",
                        fontSize = 16.sp
                    )

                    Button(
                        onClick = {
                            step3Done = !step3Done

                            prefs.edit()
                                .putBoolean(
                                    "step3_${item.title}",
                                    step3Done
                                )
                                .apply()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (step3Done) "Completed"
                            else "Mark Step 3 Done"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "DEADLINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.deadline,
                        fontSize = 18.sp
                    )

                    Text(
                        text = when (deadlineStatus) {
                            "OVERDUE" -> "Follow-up needed"
                            "DUE TODAY" -> "Due today"
                            "DUE SOON" -> "Due within 7 days"
                            "ON TRACK" -> "On track"
                            else -> "No deadline set"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "PRIORITY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.priority,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "NEXT ACTION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (item.attachmentUri != null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        TextButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW
                                ).apply {
                                    data = android.net.Uri.parse(item.attachmentUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(intent)
                            }
                        ) {
                            Text(
                                text = "📎 ${item.attachmentUri!!.substringAfterLast("/")}",
                                fontSize = 14.sp
                            )
                        }
                    }
                    if (!item.documentText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "EXTRACTED DOCUMENT TEXT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.documentText!!,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.action,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (currentProgress == 100) {
                        onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (currentProgress == 100) {
                        "Complete Case"
                    } else {
                        "Complete all steps first"
                    }
                )
            }
        }
    }
}

@Composable
fun CaseCard(
    item: CaseItem,
    onClick: () -> Unit,
    onComplete: () -> Unit
) {
    val deadlineStatus = try {
        val dateFormat = java.text.SimpleDateFormat(
            "dd MMM yyyy",
            java.util.Locale.ENGLISH
        )
        dateFormat.isLenient = false

        val deadlineDate = dateFormat.parse(item.deadline)

        val todayCalendar = java.util.Calendar.getInstance()
        todayCalendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            0
        )
        todayCalendar.set(
            java.util.Calendar.MINUTE,
            0
        )
        todayCalendar.set(
            java.util.Calendar.SECOND,
            0
        )
        todayCalendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        val todayMillis = todayCalendar.timeInMillis
        val deadlineMillis = deadlineDate?.time

        when {
            deadlineMillis == null -> "NO DEADLINE"

            deadlineMillis < todayMillis -> "OVERDUE"

            deadlineMillis == todayMillis -> "DUE TODAY"

            deadlineMillis <= todayMillis +
                    (7L * 24L * 60L * 60L * 1000L) -> "DUE SOON"

            else -> "ON TRACK"
        }
    } catch (e: Exception) {
        "NO DEADLINE"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = when (deadlineStatus) {
                            "OVERDUE" -> MaterialTheme.colorScheme.errorContainer
                            "DUE TODAY" -> MaterialTheme.colorScheme.tertiaryContainer
                            "DUE SOON" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = deadlineStatus,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.category,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = item.priority,
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = item.deadline,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (deadlineStatus) {
                    "OVERDUE" -> "Follow-up needed"
                    "DUE TODAY" -> "Due today"
                    "DUE SOON" -> "Due within 7 days"
                    "ON TRACK" -> "On track"
                    else -> "No deadline set"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(item.action)
            }
        }
    }
}

@Composable
fun SmallStatCard(
    number: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}