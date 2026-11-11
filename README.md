# ReadLoop

A personal reading-timetable app for Android. It reminds you what to read at
**12:00, 15:00, 18:00, 21:00, 00:00, 03:00, 06:00 and 09:00** — every 3 hours,
around the clock — and shows the current block on a home-screen widget.

The timetable changes per day of the week. Everything is built in the cloud by
GitHub Actions; nothing needs to be installed on your computer.

## Install

1. Open the **Releases** page of this repo on your phone.
2. Open the newest `build-N` release.
3. Download **ReadLoop.apk**.
4. Allow "install unknown apps" for your browser when prompted, then install.
5. Open the app once so it can schedule the alarms.
6. Long-press your home screen → **Widgets** → **ReadLoop** → drag it out.

## Your timetable (defaults)

Each day's subjects are spread across the 8 three-hour blocks, with the reading
focus rotating: *read + notes* → *practice problems* → *recap* → *active recall*.

| Day | Subjects |
|---|---|
| Monday | Fluid Mechanics · Leadership |
| Tuesday | Applied Mechanics |
| Wednesday | Computer Programming · MATLAB |
| Thursday | Applied Electricity |
| Friday | Engineering Mathematics |
| Saturday | Engineering Drawing |
| Sunday | Entrepreneurship · General Engineering Laboratory |

## Using it

- The app shows today's current block, all 8 blocks, your streak, and when the
  next reminder fires.
- Tap **Mark as read** (or the ✓ on the widget, or **Mark done** on the
  notification) to tick a block off.
- A day keeps your streak when **6 of 8** blocks are done.

## Editing the timetable (hidden on purpose)

There is deliberately no obvious settings button.

1. Tap the big **ReadLoop** title **7 times** in a row (within ~3 seconds).
2. The first time, you'll be asked to choose a PIN. After that, the PIN unlocks it.
3. Pick a day from the **MON…SUN** strip, edit its 8 blocks, tap **Save schedule**.

There is **no way to turn the reminders off** from inside the app. The only way
to stop them is to uninstall the app.

## Notes on Android restrictions

- Android 12+ may ask you to allow **exact alarms**. The app shows a banner if
  it hasn't been granted. Without it, reminders can drift by a few minutes.
- Android 13+ asks for notification permission on first launch.
- Some phone brands (Xiaomi, Samsung, Oppo, Infinix, Tecno) kill background
  alarms aggressively. If reminders stop, exclude ReadLoop from battery
  optimisation in system settings.

## Repo layout

```
app/src/main/java/com/dol/readloop/
  Slots.kt                         slots, days, defaults, storage, streak, PIN
  Alarms.kt                        exact-alarm scheduling (self-healing)
  Notifier.kt                      notification channel + per-slot notification
  SlotReceiver.kt                  fires on each 3-hour boundary
  DoneReceiver.kt                  "Mark done" from a notification
  BootReceiver.kt                  re-arms alarms after reboot / timezone change
  MainActivity.kt                  home screen + hidden editor unlock
  EditorActivity.kt                per-day, 8-block editor
  widget/ReadingWidgetProvider.kt  the home-screen widget
```

## Signing

Release builds are signed with a keystore that lives **only** in repository
secrets — it is never committed to the repo. The workflow decodes it into the
runner's temp directory at build time.

Required repository secrets:

| Secret | What it is |
|---|---|
| `READLOOP_KEYSTORE_BASE64` | `base64 -w0 readloop.jks` |
| `READLOOP_KEYSTORE_PASSWORD` | keystore password |
| `READLOOP_KEY_ALIAS` | key alias (`readloop`) |
| `READLOOP_KEY_PASSWORD` | key password |

A stable key means each new build installs straight over the previous one.
Lose the keystore and you can no longer update an installed copy in place —
back it up somewhere safe.
