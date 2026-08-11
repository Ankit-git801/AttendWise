# 📗 AttendWise Encyclopedia - Vol 4: The Sentinel
**Alarms, Precise Notifications, and Real-Time Cloud Sync.**

---

## 🏛️ Part 1: Precision Timing

### 1.1 Why `setAlarmClock`?
In Android, the system tries to "Batch" alarms together to save battery. This is why some apps have reminders that are 5-10 minutes late.
*   **The Logic**: AttendWise uses `AlarmManager.setAlarmClock()`.
*   **The Benefit**: This is the highest priority level in Android. It tells the OS: "Show an alarm icon in the status bar and wake the CPU exactly on the second." For a student, 30 seconds late is too late.

### 1.2 The "Session Date" Logic
```kotlin
var sessionDate = alarmDateTime.toLocalDate()
if (isOvernight) {
    alarmDateTime = alarmDateTime.plusDays(1)
}
// Pass sessionDate to the Receiver
intent.putExtra("EXTRA_SESSION_DATE", sessionDate.toEpochDay())
```
**Logic Breakdown**:
*   An overnight class starts Monday at 11:30 PM and ends Tuesday at 12:30 AM.
*   **The Problem**: The alarm fires on **Tuesday**, but the attendance belongs to **Monday**.
*   **The Fix**: The `AlarmScheduler` calculates the "Start Date" (Monday) and packs it into the alarm "suitcase". When you click "Present" on Tuesday morning, the app looks in the suitcase and says: "Ah, this is for Monday's class!".

---

## 📂 Part 2: The Notification Strategy

### 2.1 Bold Names (HTML Styling)
```kotlin
val title = Html.fromHtml("Attendance for <b>${subject.name}</b>", Html.FROM_HTML_MODE_LEGACY)
```
**Logic Breakdown**: Most apps have boring notifications. AttendWise uses `Html.fromHtml` to make the subject name **bold**. This makes the notification easier to scan at a glance when a student is busy.

### 2.2 Smart Routing (Intents)
```kotlin
val mainIntent = Intent(context, MainActivity::class.java).apply {
    putExtra("subject_id", subject.id)
}
```
**Logic Breakdown**: If you tap the notification body, the app doesn't just open the home screen. It reads the `subject_id` and **routes you directly** to that subject's detailed history. We call this "Deep Linking".

---

## 📂 Part 3: The Race Condition Shield

### 3.1 `NotificationProcessingTracker`
```kotlin
object NotificationProcessingTracker {
    private val processedNotifications = ConcurrentHashMap<String, Long>()

    fun markAsProcessed(subjectId: String, scheduleId: String) {
        val key = "${subjectId}_${scheduleId}"
        processedNotifications[key] = System.currentTimeMillis()
    }
}
```
**Logic Breakdown**:
*   **The Bug**: You click "Present" in the app. Simultaneously, you swipe away the notification. The phone "hears" the swipe and tries to re-post the notification because it thinks you ignored it.
*   **The Shield**: When you click "Present", the app writes a "Don't Re-post" note in this `ConcurrentHashMap`.
*   **Safety**: We use `ConcurrentHashMap` because notifications happen on background threads. It ensures the app doesn't crash if two things happen at the exact same microsecond.

---

## 📂 Part 4: The Cloud Engine

### 4.1 Firestore Listeners
```kotlin
userDoc.collection("subjects").addSnapshotListener { snapshot, e ->
    for (dc in it.documentChanges) {
        // Handle ADDED, MODIFIED, REMOVED
    }
}
```
**Logic Breakdown**: AttendWise doesn't "Check for updates". It "Listens".
*   If you have the app open on your Tablet and your Phone, and you add a subject on your Phone, the Tablet **receives a signal** from Google and draws the new subject instantly. There is no "Pull to Refresh" needed.

### 4.2 The Atomic Logout
```kotlin
viewModelScope.launch {
    withContext(NonCancellable) {
        attendanceDao.deleteAllSubjects()
        ...
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
    }
}
```
**Logic Breakdown**:
*   **The Problem**: If you log out and the app is killed halfway through, you might have "Ghost Data" from the old user appearing for the new user.
*   **The Fix**: `NonCancellable` ensures that every single local table is wiped clean before the user is actually signed out.

---

**This concludes the AttendWise Encyclopedia.**
*You now have a line-by-line understanding of the most advanced logic in your application. Study these volumes to master the architecture.*
