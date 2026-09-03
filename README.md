# AttendWise 📱✨

AttendWise is a professional, high-integrity attendance management application for Android. Built with a "Gold Master" philosophy, it offers a robust, conflict-resilient environment for students to track their academic targets with 100% data reliability.

## 🌟 Core Features

*   🎯 **Attendance Targeting:** Set personalized goals (e.g., 75%, 85%) for each subject.
*   📊 **Bunk Analysis:** Advanced mathematical engine that calculates exactly how many classes you can safely miss or must attend to reach your target.
*   ⏰ **Intelligent Reminders:** Automated notifications fire precisely at the end of your scheduled classes.
*   ☁️ **Hardened Cloud Sync:** Robust REST-based synchronization with a Spring Boot backend, featuring offline-first support and conflict-resilient data restoration.
*   🗓️ **Dynamic Calendar:** A complete visual history of your attendance, including public holiday management and class cancellation support.
*   🚀 **High Performance:** Optimized for the latest Android versions, featuring extremely low battery impact and buttery-smooth Jetpack Compose UI.

## 🛡️ Professional Hardening (Audit Phases)

The codebase has undergone 13 rigorous phases of architectural refinement and stress-testing:

### 1. Data Integrity & Safety
*   **Safe Upsert Engine:** Prevented data loss during subject edits by replacing destructive `REPLACE` operations with high-integrity `upsert` logic.
*   **Strict Foreign Keys:** Every attendance record and class schedule is strictly linked in the database, preventing "orphaned" or corrupted data.
*   **Non-Destructive Migrations:** Professional migration scripts preserve local history during app updates, ensuring data safety for both online and offline users.

### 2. Reliability & Resilience
*   **Self-Healing Reminders:** A startup routine that automatically verifies and repairs system alarms every time the app is opened.
*   **Global Time Support:** Listeners for Time Zone changes and manual clock updates ensure reminders are always aligned with your local time.
*   **Battery Saver Compatibility:** Integrated custom guidance for aggressive manufacturers (Samsung, Xiaomi, Vivo, Oppo) to ensure 100% reminder delivery.

### 3. Logic & Accuracy
*   **Conflict Detection:** Built-in validation prevents overlapping class schedules across different subjects.
*   **Holiday Intelligence:** The app correctly "leap-frogs" reminders to the following week when a holiday is marked, maintaining the weekly chain.
*   **Mathematical Edge Cases:** Formulas are hardened against "100% target" scenarios to prevent division-by-zero crashes.

### 4. Advanced UX
*   **Buffered Navigation:** Uses Kotlin Channels to handle deep-links from notifications, ensuring the correct screen opens instantly even during a cold start.
*   **Silent Action Updates:** Notification interactions are silent and fast, providing immediate feedback without redundant "ping" sounds.
*   **Tactile Polish:** Integrated haptic feedback and clear success confirmations for all manual user actions.

## 🛠 Tech Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose (Material 3)
*   **Local Database:** Room (SQLite) with Reactive Flow and high-precision SQL joins.
*   **Backend:** Kotlin Spring Boot REST API with Spring Security (JWT).
*   **Cloud Database:** MongoDB Atlas.
*   **Networking:** Retrofit & OkHttp.
*   **Concurrency:** Kotlin Coroutines with specialized Mutex protection for sync operations.

## 🛡 Permissions

*   **POST_NOTIFICATIONS:** To deliver class reminders.
*   **SCHEDULE_EXACT_ALARM:** For millisecond-precise reminder timing.
*   **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:** To keep reminders active on aggressive battery-saving devices.

---

## 📄 License

Copyright © 2026 Ankit. All rights reserved.  
**Proprietary and Confidential.** Unauthorized copying, modification, or distribution of this software is strictly prohibited.
