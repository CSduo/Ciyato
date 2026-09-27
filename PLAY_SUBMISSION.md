# Getting Ciyato onto Google Play

Written because "12 testers for 14 days" is a real blocker that nobody explains
clearly, and because the order you do things in matters — several steps have
14-day or 7-day waiting periods, and doing them in the wrong order adds weeks.

**What I can and cannot do here.** I can build the app, generate the release
bundle, write the store listing text, fill in the Data Safety answers from
`DATA_INVENTORY.md`, and write the permission declarations from
`PLAY_POSITIONING.md`. I cannot log into Play Console, and I cannot be one of the
12 testers — they have to be real Google accounts belonging to real people who
opt in. Every step below is marked with who does it.

---

## The 12-tester rule, plainly

If the developer account was created **after 13 November 2023** and is a
**personal** (not organisation) account, Google requires this before it will let
you publish to production:

> Run a **closed test** with at least **12 testers** who have been **opted in
> continuously for 14 days**, then apply for production access.

Three things people get wrong about it:

1. **It is 12 testers *opted in*, not 12 testers who installed.** They have to
   accept the opt-in link. Whether they open the app much afterwards is not
   counted, though Google does look at whether the test was genuine.
2. **The 14 days are continuous.** If you drop to 11 testers on day 9, the clock
   restarts. So recruit 14–15 to have slack.
3. **It runs in parallel with everything else.** You do not have to be finished
   to start it. This is the single most useful thing to know: **start the closed
   test early**, because the 14 days will otherwise be 14 days added to the end.

Check which rules apply to you: Play Console → **Policy and programmes** →
**Programme status**, or the banner on the dashboard. If the account is older than
November 2023, or is an organisation account, none of this applies and you can
skip to §4.

---

## 1. Before the closed test — what has to be true (mine)

The closed test needs a **signed release bundle**, which needs the signing key.

- [x] Release build assembles with R8 and resource shrinking — verified
- [x] Merged manifest gated against an allowlist — `verifyReleaseManifest`
- [x] Data Safety answers traceable to `DATA_INVENTORY.md`
- [x] Permission justifications written — `PLAY_POSITIONING.md`
- [ ] **Open-source licences screen in Settings** — `THIRD_PARTY_NOTICES.md`
- [ ] Upload keystore created and its password stored somewhere safe **(yours)**
- [ ] Device checks run: predictive back, 16 KB pages, foldable **(yours)**

The 16 KB page-size check (F-188) is the one that would actually stop a release:
on an Android 16 device with 16 KB memory pages, a misaligned native library means
the app **does not start at all**. Nothing in a build can detect it. It has to be
installed on such a device, or an emulator image configured for it.

---

## 2. Creating the upload key (yours, once, ~5 minutes)

Do this on your own machine and **never put the result in this repository** —
`.gitignore` already excludes keystores, and the build fails rather than
debug-signing if it cannot find one.

```bash
keytool -genkey -v -keystore ciyato-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias ciyato
```

It asks for a password twice and some identifying details. Then, in
`~/.gradle/gradle.properties` (**not** the project's):

```properties
CIYATO_UPLOAD_STORE_FILE=C:/Users/ADMIN/keys/ciyato-upload.jks
CIYATO_UPLOAD_STORE_PASSWORD=<the password you chose>
CIYATO_UPLOAD_KEY_ALIAS=ciyato
CIYATO_UPLOAD_KEY_PASSWORD=<the password you chose>
```

**Back the `.jks` file up somewhere that is not this computer.** If you lose it you
cannot update the app ever again — you would have to publish a new listing under a
new package name and lose every install and review. Play Signing (which you should
enable when prompted) protects the *app* signing key, not this upload key.

Tell me once it is in place and I will build the signed bundle.

---

## 3. Setting up the closed test (yours, ~20 minutes, then 14 days of waiting)

Play Console → your app → **Testing** → **Closed testing** → **Create track**.

1. **Create an email list.** Testing → Closed testing → Testers tab →
   **Create email list**. Paste in 14–15 Gmail addresses, one per line. They must
   be the Google account each person actually uses on their phone.
2. **Upload the bundle** to that track. I produce
   `app/build/outputs/bundle/release/app-release.aab`.
3. **Copy the opt-in link** Play gives you (it looks like
   `https://play.google.com/apps/testing/com.ciyato.launcher`).
4. **Send it to your testers** with a sentence telling them what to do, because
   the link alone confuses people:

   > This is a private test of an Android launcher I've built. Open this link on
   > your phone, tap "Become a tester", then install from Play as normal. You
   > don't have to use it much — just please don't leave the test for two weeks,
   > because Google counts the days. Tell me anything that looks broken.

5. **Check the count after a day or two**, in the track's Testers tab. Chase
   anyone who has not opted in. You need 12 *accepted*, and you want 14 so a
   dropout does not restart the clock.
6. **Wait 14 days.** Push updates to the same track during it — that is fine and
   expected, and it is how the device-testing findings get verified on real
   hardware.
7. **Apply for production access** when Play offers it. It asks what you learned
   from the test; answer honestly and specifically. "12 testers, no crashes, two
   layout issues on foldables which are fixed" reads as a real test. Vague answers
   get rejected and cost another round.

**Who to ask:** anyone with an Android phone and a Gmail address. Family,
friends, colleagues. They do not need technical skill. Buying testers from a
service is against Play policy and is detectable.

### Can this be scheduled or automated?

Not the testing part. The opt-ins are human actions and the 14 days are wall
clock. What *can* be automated is the upload — the Google Play Developer API
publishes bundles to a track from CI, so once the key exists I can wire
`.github/workflows/` to push each build to the closed track automatically. Say the
word and I will; it needs a service-account JSON from Play Console, which is a
credential and stays with you.

---

## 4. Store listing and declarations (mine to draft, yours to submit)

I will produce these from the documents that already exist, so they cannot
contradict the code:

| Item | Source |
|---|---|
| Short and long description | `PLAY_POSITIONING.md` §3 — launcher first, organizer second, Labs absent |
| Data Safety form | `DATA_INVENTORY.md` §5, which maps each question to its answer |
| `QUERY_ALL_PACKAGES` declaration | `PLAY_POSITIONING.md` §1 — a launcher is a documented exception |
| All files access declaration | `PLAY_POSITIONING.md` §2 |
| Photo and video permissions declaration | `PLAY_POSITIONING.md` §2 |
| Privacy policy | Needs a public URL. See below |

**The privacy policy needs to be hosted somewhere public** before submission —
Play requires a URL, not a file. A GitHub Pages page on this repository is free
and sufficient. I can write the content from `DATA_INVENTORY.md`; you would enable
Pages and give me the URL to put in the listing and in Settings.

You will also need, and I cannot produce: a feature graphic (1024×500), at least
two screenshots per form factor, and a 512×512 icon. The icon exists. Screenshots
have to come from a real device, which is another reason to start the closed test
early — you will be running the app anyway.

---

## 5. The order that saves the most time

```
now        → create the upload key                     (you, 5 min)
           → I build the signed bundle
           → create the closed track, invite 15 people (you, 20 min)
           → ***the 14 days start here***
during     → run the device checks on your own phone   (you)
             F-188 16 KB pages, F-186 predictive back,
             F-046 foldable, font scale 1.3x / 2.0x
           → I fix whatever they turn up, push to the same track
           → I draft the listing, Data Safety, declarations
           → you host the privacy policy, take screenshots
day 14     → apply for production access               (you)
after      → submit for review                         (you)
```

The waiting period is the long pole and it costs nothing to start. Everything else
fits inside it.

---

## What I need from you, and when

| When | What | Why |
|---|---|---|
| Whenever | Upload keystore in place, path and confirmation | I cannot build a signed bundle without it, and the password must not reach me |
| Whenever | Confirmation you want the closed test started | It begins a 14-day clock |
| Before submission | The hosted privacy-policy URL | Play requires a URL; I can write the content |
| Before submission | Feature graphic and screenshots | Needs a real device and a design choice |
| Optionally | Play service-account JSON, kept by you | Only if you want CI to upload builds automatically |

Nothing else. The rest is mine.
