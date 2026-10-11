# Lost & Found (Kotlin / Android)

This Jetpack Compose application supports:

1. **Lost reports:** required description and last known location, optional photo.
2. **Found reports:** required photo and found location, optional description. A finder can select an open lost report or use **I found this item** from the report list.
3. **Notifications:** linking a found report creates an unread in-app alert, marks the lost report as reported found, and posts an Android notification if enabled. Tap the notification to open Alerts. The alert shows the found photo and location and can be marked as read.

Locations are entered as text (for example, Library, second floor). Matching is explicitly selected by the finder; similar descriptions alone do not trigger alerts. The owner should check the photo and details to confirm the item.

Reports, photos, alerts, and read status are stored on this device and survive app restarts. This is a local prototype: reports are not shared between users or devices, and it does not receive remote/background updates. Cross-device use needs a shared backend, user identity/report ownership, and push notifications.

Images are selected with the Android [photo picker](https://developer.android.com/training/data-storage/shared/photo-picker) and copied into private app storage. Maximum image size: 10 MB. No broad photo-library or location permission is needed. Android 13+ requires [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission); use **Alerts → Enable notifications**. In-app alerts work even if notification permission is denied.

## Build and test

Use Android Studio's bundled Java runtime and the Android SDK configured in `local.properties`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected tests require a running emulator or device. They cover lost-form validation, required found photos, selecting/importing a photo and linking a report, persistence, alert details, and read status. Tests preserve existing reports in the test device's local store.
