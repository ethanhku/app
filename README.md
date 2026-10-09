# Mossling 🌱

A virtual companion that grows when you spend **less** time on your phone.

Mossy is a little moss creature with big eyes. You pick the apps you'd like to use less
(Instagram, TikTok, YouTube…). Staying under your daily budget earns sunlight, and sunlight
makes Mossy grow from a Spore into an Ancient Grove and find keepsakes along the way.

It's built on the design findings in the virtual-companion research brief:

| Research finding | How Mossling applies it |
| --- | --- |
| "Gamify subtraction" is an untapped gap | Rewards are for screen time you **don't** spend in the apps you pick |
| Punitive mechanics cause "gamification exhaustion" and churn | No damage, death, decay or runaway pet. Over budget, Mossy just gets drowsy and is fine tomorrow. Sunlight never goes down |
| Make rewards informational, not controlling (CET / overjustification) | Notes like "Yesterday: 42 of 60 min. +28 sunlight". Keepsakes are things Mossy "found" because you were consistent |
| Adaptive difficulty keeps people in flow | Budget starts 10% under your own measured last-week average. Each met day eases it down 5%, each missed day eases it back up 5%. It never goes above your old baseline or below the floor you choose |
| Streaks need to forgive rest and sick days | Every 7 days in budget you earn a **rest day** (hold up to 2) that protects your streak on an off day |
| A missed day still deserves credit (Competence) | Over budget but under your old baseline still earns half the improvement as sunlight |
| Baby-schema cues create caregiving attachment | Big low-set eyes, round small body, blush, breathing animation |
| Focus mode as recovery | **Quiet time** (25/45/90 min) tucks Mossy in for a nap. Stay out of your picked apps for bonus sunlight |
| Gentle nudges, not shields | One notification at 80% of budget and one at 100%, at most, per day |

## Install it on your Android phone (free)

Android lets you read screen time with the free `PACKAGE_USAGE_STATS` permission, so no paid
developer account is needed. (iOS's Screen Time API needs Apple's Family Controls entitlement and
a paid developer account, so this version is Android only.)

1. Open this repo's **Actions** tab on GitHub, select the latest **Build APK** run, and download
   the `mossling-debug-apk` artifact (a zip containing `app-debug.apk`).
2. Copy the APK to your phone and open it. Allow "Install unknown apps" for your browser or file
   manager when Android asks.
3. In Mossling, tap **Open Usage access**, switch Mossling on, and come back.
4. Pick your apps, choose the lowest daily budget you'd want, and plant Mossy.

Requires Android 8.0+. Everything stays on the phone: no account, no network, no analytics.

## How it works

- `core/`: the rules engine in plain Kotlin with JVM unit tests: usage math, adaptive budget,
  sunlight, streaks and rest days, stages, moods and keepsakes.
- `app/`: the Android app (Jetpack Compose).
  - `UsageSource.kt` turns `UsageStatsManager` foreground and background events into minutes
    per day for the picked apps, and measures the starting baseline from the last 7 days.
  - `Mossling.kt` settles finished days (also catching up after the app was closed),
    finishes quiet-time sessions, and runs a WorkManager check every 15 minutes for nudges.
  - `MossyView.kt` draws Mossy on a Canvas; `MainActivity.kt` holds the screens.

Days are settled from the OS's own usage history, so Mossy is never penalised for the app
being closed or the background check being delayed by battery saver. Android keeps that history
for about a week, so after a longer absence only the last 7 days are settled.

## Building locally

```sh
MOSSLING_CORE_ONLY=1 ./gradlew :core:test   # rules engine tests, no Android SDK needed
./gradlew :app:assembleDebug                # needs the Android SDK (compileSdk 35)
```
