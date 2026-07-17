# Parroty Player

An Android companion for [Parroty](https://github.com/pgotta/Parroty). It plays the
audiobook Parroty built, mp4 or mp3, from Google Drive or from the phone, and turns
the `youtube_chapters-….txt` file into a tappable chapter list.

It is the app version of the `drive-chapters-….html` page, without the copy-paste
step, and unlike Drive's own web player it keeps playing with the screen off.

## What it does

- **Two sources.** Browse Drive as a folder tree, or open a file already on the
  phone through the system file picker.
- **Auto-pairs the companion files.** Pick an mp4 in Drive and it reads the same
  folder, finds the chapter `.txt` and subtitle `.srt` from the same run, and
  pre-selects them. Both overridable.
- **Offers the mp3 instead.** If Parroty's combined audiobook is in the same folder,
  the setup screen offers it. Same narration, no cover-image video track, less data
  and less battery. Chapters work identically.
- **Streams, does not download.** A ranged read against the Drive API, so an 11-hour
  book starts instantly and uses no storage. Saving for offline is opt-in per book.
- **Keeps playing with the screen off**, via a MediaSessionService with lockscreen
  and notification controls.
- **Tap a chapter, land on the word.** Seeks are exact, not nearest-keyframe.
- **Remembers the exact second you stopped**, per book, keyed on the Drive file id so
  renaming the file in Drive does not lose your place. Written every five seconds and
  the moment audio stops for any reason: the in-app button, the notification, the
  lockscreen, a headset, or a call stealing focus.
- **Timestamp bookmarks**, labelled with the chapter you were in.
- **Subtitles** as a toggle, with the video expandable to full screen.
- **Clear the library**, per book or all at once.

## Requirements

- Android Studio Ladybug or newer
- A phone on Android 8.0 or newer
- A Google account, and about ten minutes in the Google Cloud console

---

# Setup

There are two parts: a Google Cloud OAuth client, and the build. The Cloud part is
the fiddly one. **Nothing in the app works until it is done**, and the failure mode
is a sign-in that silently does nothing.

## 1. Google Cloud

**Google moved all of this.** Guides written before 2025 say *APIs & Services ,
Credentials* and *OAuth consent screen*. Those pages are now a section called
**Google Auth Platform**, whose left nav reads Overview / Branding / Audience /
Clients / Data Access / Verification Center / Settings. The steps below use the
current names.

**Already have a Cloud project for another app of yours?** Use it. A project is a
container and holds as many OAuth clients as you like, one per package name, so only
step 1.4 is new. Reusing is not just less work: the 7-day expiry below runs **per
project**, so one project means one clock and one re-consent tap covering every app
in it, instead of two clocks drifting out of sync and nagging you twice as often.

### 1.1 Enable the Drive API first

Hamburger menu , **APIs & Services , Library** , search **Google Drive API** ,
**Enable**.

Do this first. It is the one step outside the Google Auth Platform section, so it is
the easy one to miss, and **skipping it produces the most confusing failure in this
whole setup**: sign-in succeeds, then every Drive call returns 403. It looks exactly
like an auth bug and is not.

### 1.2 Consent screen

In **Google Auth Platform**:

- **Branding**: app name and your email. Leave the app in **Testing**.
- **Audience** , **Test users** , add your own Google account. Miss this and sign-in
  fails with "app is blocked" even though everything else is correct.
- **Data Access** , add `https://www.googleapis.com/auth/drive.readonly`. It will
  warn you this is a restricted scope. That is expected, continue. If an existing
  project only lists `drive.file`, add this one as well; they are different scopes
  and this is the one that can browse everything.

### 1.3 Get your SHA-1

The easy way, no terminal:

**Gradle** tool window (right edge) , `ParrotyPlayer` , `app` , `Tasks` , `android`
, double-click **signingReport**. Read the **SHA1** line under `Variant: debug`.

<details>
<summary>The keytool way, and the two things that go wrong with it</summary>

```
keytool -list -v -alias androiddebugkey -keystore %USERPROFILE%\.android\debug.keystore -storepass android -keypass android
```

**That command fails in PowerShell**, which is what Windows Terminal and Android
Studio's Terminal tab open by default. Two separate reasons, and they stack:

1. PowerShell does not expand `%USERPROFILE%`. It needs `$env:USERPROFILE`. The
   error you get is about a missing keystore, which sends you looking in the wrong
   place entirely.
2. `keytool` ships with a JDK, not with Windows, so it is usually not on PATH.
   Android Studio keeps its own JDK.

The version that actually works in PowerShell, quoted path and all:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -alias androiddebugkey -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android -keypass android
```

If it says the keystore does not exist, it is because Android Studio creates
`debug.keystore` the first time it builds anything. Build once, then retry.

</details>

**What the SHA-1 actually identifies.** This trips up everyone, so plainly:

- It is a fingerprint of the **signing key**, not of your phone and not of your
  Google account. Your phone never enters the console. Install the APK on ten
  devices, no registration needed.
- Android Studio auto-creates a debug key per machine at
  `%USERPROFILE%\.android\debug.keystore`. Two laptops means two different keys and
  two different SHA-1s.
- **Only machines you build on matter.** A laptop that never opens the project needs
  nothing.

So if you build on one machine, register one fingerprint and stop reading. If you
build on two, either add a second Android client (they are free and unlimited), or
copy `%USERPROFILE%\.android\debug.keystore` from one machine to the same path on
the other so both sign identically. The copy is worth it if you have more than one
machine, since it also covers every future app and every future laptop.

### 1.4 Create the client

**Google Auth Platform , Clients , + Create client**:

- Application type: **Android**
- Package name: `com.parroty.player`
- SHA-1: from step 1.3

Then:

- **Skip "Download JSON".** Android OAuth clients do not carry a client ID into the
  code the way web ones do. There is no file to download and nothing to paste
  anywhere. Play Services matches the app by package name and signature at runtime.
  This is also why another app's client cannot be reused: it is bound to that app's
  package name.
- **Skip "Verify ownership".** That is for Play Store distribution.
- The console warns that changes take **5 minutes to a few hours** to propagate. It
  is usually minutes. If sign-in fails right after this, wait and retry **before**
  changing settings that were already correct.

> **The 7-day thing.** `drive.readonly` is a restricted scope. Google only lets an
> unverified app use it while the project is in Testing, and grants there expire
> after 7 days. Expect the account picker back about weekly. It is one tap. This is
> Google policy, not something the app can work around, short of OAuth verification.

## 2. Build

Open the folder in Android Studio and let it sync, then Run. Or `./gradlew
assembleDebug` for an APK to sideload.

Everything is pinned in the version catalog at `gradle/libs.versions.toml`. The
Gradle wrapper is committed, so a fresh clone can build without installing Gradle.

---

# Troubleshooting

Everything below is something that actually happened, in the order it tends to hit.

### "Could not connect to Kotlin compile daemon", usually on `:app:kspDebugKotlin`

**Not your code.** The Kotlin compiler forks its own JVM that does **not** inherit
`org.gradle.jvmargs`, so left unset it picks a default that often fails to launch on
Windows. Gradle reports the failure to launch as a failure to connect, which sends
you looking for a network problem that does not exist. KSP runs the compiler for
Room's codegen, which is why that task usually trips first.

Already fixed in `gradle.properties`:

```properties
kotlin.daemon.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

If it still fails, uncomment the in-process line in the same file. That runs the
compiler inside the Gradle daemon instead of forking one. Slower, sidesteps the
problem entirely. The usual reason you would need it is antivirus blocking the local
socket the daemon connects over.

If it fails a third time, File , **Invalidate Caches / Restart**. Dead daemons linger
as orphaned `java.exe` processes and Gradle keeps trying to reuse them.

### Where is gradle.properties?

Expand **Gradle Scripts** in the project panel. Pick the one labelled
**(Project Properties)**, not **(Global Properties)**. The second is
`C:\Users\<you>\.gradle\gradle.properties` and applies to every project on the
machine. Android Studio lists them next to each other and they look identical.

The Android view hides the real folder structure. Switching the dropdown at the top
of the panel from **Android** to **Project** shows the actual tree.

### Sign-in does nothing, or "app is blocked"

In order of likelihood: your account is not in **Audience , Test users**; the client
was created less than ten minutes ago and has not propagated; the SHA-1 belongs to a
different machine than the one that built the APK; or you are running a release
build against a debug client.

### Sign-in works, then everything in Drive fails

The Drive API is not enabled. Step 1.1.

### Playback stops with ERROR_CODE_IO_BAD_HTTP_STATUS, often after the screen was off

That is a 401 from Drive: the access token went stale. Fixed, but worth knowing the
shape of it because it is misleading. Playing from an already-filled buffer needs no
new requests, so a dead token causes no trouble until something opens a fresh one,
which is usually a seek. That makes it look like a background or screen-off bug when
it is really elapsed time.

Three things address it. `DriveDataSource` catches a 401 or 403 on open, drops the
cached token, and retries the same byte range once with a fresh one. The player holds
`WAKE_MODE_NETWORK`, so Android cannot sleep the Wi-Fi radio out from under a stream.
And `PlayerViewModel` re-prepares at the current position on a network error, backing
off, up to four times before it gives up and offers a tappable retry.

### It worked last week and now wants me to sign in again

The 7-day testing expiry. Working as intended.

### Chapters do not appear

The chapter file has to be a `.txt` with `chapter` in the name, sitting in the same
Drive folder as the audio, and its lines have to start with a timestamp. Both `MM:SS`
and `HH:MM:SS` are accepted, mixed in one file. On the local-file path nothing
auto-pairs: SAF hands back a single uri with no folder to look in, so you pick the
chapter file yourself.

### A local book stops opening after a reboot

The uri grant was not persisted. The app calls `takePersistableUriPermission`, but
some file providers do not offer persistable grants. Re-add the book.

### Release APK sign-in fails with no error

A release build is signed with a different keystore, so a different SHA-1, so it
needs its own Android client in the same project. Back the release keystore up:
losing it means never updating that APK.

---

# How it works

```
ui/MainActivity          auth gate, then the nav host
ui/library               books you have opened, resume progress, clearing
ui/browse                Drive folder tree
ui/pair                  pick source, chapter .txt and .srt for a Drive book
ui/local                 the same, for a book on the phone, via SAF
ui/player                transport, chapter list, bookmarks, subtitles

player/PlaybackService   MediaSessionService, owns ExoPlayer, survives the UI
player/DriveDataSource   attaches a fresh Drive token to every ranged request

data/ChapterParser       youtube_chapters.txt -> List<Chapter>
data/BookRepository      pairing, chapters, resume, offline, local imports
data/drive/DriveApi      Drive v3: list folder, read text, stream media
data/db                  Room: books + bookmarks
auth/DriveAuth           Play Services AuthorizationClient
```

### Decisions worth knowing about

**The token is fetched per request, and never trusted.** A Google access token lasts
about an hour, an audiobook does not, and tokens get invalidated ahead of their
nominal expiry anyway. So `DriveDataSource` resolves a token for every ranged request
**and** treats a 401 as "this token is dead" rather than as an error: it drops the
cache and retries the range once. Caching alone is not enough, because a cached token
can be dead while still looking fresh.

**Chapter marks are read from the sidecar, not the mp4.** Parroty embeds chapter
marks, but reading them would mean parsing the moov atom before playback starts, over
the network. The `.txt` is a few hundred bytes and is already sitting next to the
file.

**Playback lives in a service, not a ViewModel.** That is what makes screen-off
listening work, and it is the reason this app exists rather than a shortcut to the
Drive page.

**A local book's id is its `content://` uri**, which is full of slashes and colons.
Navigation routes escape it. Unescaped it parses as extra path segments and the
player never opens.

---

# Colours and type

`ParrotyPalette` in `ui/theme/Color.kt` is a one-to-one mirror of the `:root` block
in Parroty's `app/static/style.css`. Field names match the CSS custom property names,
so if `--spine` changes, change `Spine`. Nothing else in the project hardcodes a
colour.

| CSS | Where it lands |
|---|---|
| `--paper` | background, status and navigation bars |
| `--paper-deep` | cards, the playing chapter row |
| `--rule` | dividers, slider and progress tracks |
| `--spine` | play button, the 4px rule on the playing chapter, errors |
| `--spine-deep` | pressed state |
| `--gilt` | border on notices |
| `--ok` | offline badge |
| `--warn-bg` | notice background |

The one duplicate is `res/values/colors.xml`, which holds the window background for
the split second before Compose draws. Keep it in step with `--paper`.

**No dark theme, on purpose.** Parroty does not have one, and flipping the phone to
dark mode should not repaint a book interior.

**Type.** `index.html` asks Google Fonts for exactly these, and `Type.kt` cuts the
same weights and no others:

```
Fraunces:opsz,wght@9..144,400;9..144,600;9..144,900
Spline+Sans:wght@400;500;600
Spline+Sans+Mono:wght@400;500;600
```

All three are variable fonts, which matters before you touch `Type.kt`. **Fraunces
ships with axis defaults of wght=900 and opsz=9**, so dropping it in without pinning
axes renders every title in Black at display contrast. Each weight sets its own
`FontVariation.Settings`. The browser resolves `opsz` against the rendered size on
its own; Android has no equivalent, so `fraunces(size)` builds a family with `opsz`
pinned to the size it is used at, which is why there is a family per size.

`Type.kt` opts in to `ExperimentalTextApi`. Compose still marks `FontVariation`
experimental and it is the only way to reach the axes.

Fraunces Italic is not bundled; nothing renders italic serif and it was the largest
file in the set. Add roman-numeral chapter headings like the desktop `.chapter-roman`
and it needs to come back.

**The icon** is Parroty's own `parrot-mark`, the SVG inlined at the top of
`index.html`, converted to `res/drawable/parrot_mark.xml`. SVG `circle` and `line`
have no VectorDrawable equivalent so they became paths, and the CSS variables became
literal hex. `ic_launcher_foreground.xml` is the same artwork in a `<group>` scaled
into the adaptive icon's safe zone. Android cannot reference one vector from another,
so the paths are duplicated: change the mark and both files need the edit.

No monochrome icon is declared. Themed icons render the drawable as a flat
silhouette and a parrot reading a book collapses into an unreadable blob. Android
falls back to the full-colour icon.

---

# Licence

MIT, same as Parroty. See `LICENSE`.

The bundled fonts are **not** covered by that grant. All three are SIL Open Font
License 1.1 and their licences must ship with any build you distribute:

- **Fraunces**, copyright The Fraunces Project Authors. `licenses/Fraunces-OFL.txt`
- **Spline Sans**, copyright The Spline Sans Project Authors. `licenses/SplineSans-OFL.txt`
- **Spline Sans Mono**, copyright The Spline Sans Mono Project Authors. `licenses/SplineSansMono-OFL.txt`
