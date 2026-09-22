# Play positioning and permission strategy

Google Play judges restricted permissions against the app's **core user-facing
purpose**. That makes positioning an engineering document, not a marketing one:
the store listing, the onboarding, the permission declarations and the
information architecture have to describe the same product, or the declaration
fails against its own listing (F-210).

Ciyato's breadth is the risk. It is a launcher, a file manager, a photo
organiser, a cleanup tool and a set of experiments. A listing that leads with
"AI utilities" weakens the case for package visibility and file access; a
listing that claims all of it is core is not believable.

---

## 1. The core purpose

> **Ciyato is a premium Android launcher that organises the phone it runs on.**

Everything below follows from that sentence, including which permissions are
defensible.

### Core — named in the listing, declared as core functionality

| Capability | Why it is core to *this* product |
|---|---|
| **Home screen and app library** | This is the product. Ciyato replaces the home screen; app enumeration, categorisation, hiding, locking and launching are the thing itself. |
| **Files** | "Organises the phone" includes its files. Device-wide search, categorisation, duplicate detection and cleanup are the organiser half of the product, not an add-on. |
| **Photos** | The largest thing on most phones, and the one people most want organised. Collections, duplicate cleanup and on-device labelling are whole-library operations. |

### Secondary — present, supported, not part of the permission argument

Weather, Agenda, Focus, Sticky Notes, Insights, the breach checker, Safe
Browsing helper, Photos-to-PDF, Secure Vault. They use permissions they ask for
at the moment they are used, and none of them is a justification for a
restricted permission.

### Not in the store listing at all

Anything the maturity ledger marks as experimental. A restricted permission must
never be justified by a Labs feature, and Labs must not appear in screenshots or
the short description (F-210).

---

## 2. The two restricted decisions

The audit framed both as open questions (F-192, F-193). They are decided.

### All files access — **kept, and declared**

`MANAGE_EXTERNAL_STORAGE` stays. Files is a core capability, not an optional
mode, and that is the position taken in the declaration.

The conservative alternative — a Play flavour without it and a separately
distributed advanced flavour — was considered and rejected. It would mean
shipping two products, testing two capability matrices, and asking people which
Ciyato they downloaded. Worse, it would make the Play build's Files screen a
weaker thing that still had to explain itself.

What the declaration must say, and what the code must back up:

- SAF can open a file the person points at. It cannot answer "which files on
  this device are duplicates", "what is taking up my storage", or "find the PDF
  I saved last month" — all of which are the feature.
- The scoped path is real and it is not a stub: without All files access, Files
  falls back to the document picker and MediaStore, works for what it can reach,
  **and says what it cannot reach**. It never presents a partial scan as a
  complete one.
- Nothing read from storage leaves the device. `DATA_INVENTORY.md` §1 lists
  every host the app contacts, and storage is not involved in any of them.

**The word "optional" must not appear next to it in UI copy.** Describing a
capability as optional while arguing to Play that it is core is the drift the
finding warned about. The honest framing, which is also the true one: Files
works without it and works properly with it.

### Broad photo access — **kept, and declared**

`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` stay. Photos is a whole-library
organiser.

The system photo picker is the privacy-friendly alternative Play asks about, and
it is genuinely insufficient here for one reason that is easy to state: **a
picker returns a selection, and the feature's job is to tell you things about the
set you did not select.** You cannot ask someone to pick their duplicates.

Partial access is supported, honoured and disclosed:

- `READ_MEDIA_VISUAL_USER_SELECTED` is declared and handled.
- When only some photos are granted, totals, duplicate results and cleanup
  figures describe **only** the granted photos, and the UI says so rather than
  presenting a partial count as a library-wide one.
- The AI labelling pass states its coverage before it runs, not after.

**Broad access is never requested for a novelty.** Duplicate cleanup and library
organisation justify it; nothing else is allowed to be the reason.

---

## 3. What the listing must say

The first three screenshots, the short description, the long description, the
onboarding and the permission declarations all have to describe the same
hierarchy. The order is not cosmetic — it is the argument.

1. **Launcher first.** Home screen, app library, categories, gestures,
   customisation. A reviewer should know within one screenshot that this is a
   launcher.
2. **Organiser second.** Files and Photos, as what the launcher does with the
   phone's contents.
3. **Everything else third or absent.** No feature list that reads as "and 80
   more". Breadth without hierarchy is what makes a restricted-permission
   argument implausible.

The short description must not lead with "AI". `AI Phone Organizer` is the
current wordmark subtitle; as a store lead it describes a category Play cannot
evaluate and invites the question of what the AI is. The AI in Ciyato is
on-device image labelling and local heuristics, which is a fine thing to say in
the body and a weak thing to lead with (F-209).

---

## 4. Pre-submission checklist

Permission strategy:

- [ ] Merged **release** manifest contains exactly the permissions in
      `DATA_INVENTORY.md` §2 and nothing else. `PermissionRegistryTest` proves
      the registry and the manifest agree; a human still reads the merged file.
- [ ] Declaration form completed for `QUERY_ALL_PACKAGES`,
      `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, using
      §1's "why it is core" column.
- [ ] No UI copy calls a restricted capability "optional".
- [ ] Partial photo access verified on a device: totals and duplicate results
      state their scope.

Consistency:

- [ ] Store listing hierarchy matches §3.
- [ ] Onboarding's privacy page matches `DATA_INVENTORY.md` §1 exactly — same
      number of exceptions, same description of each.
- [ ] No blanket privacy language anywhere: no "everything stays on your
      device", "never leaves", "100% offline", "no data ever". `CLAUDE_VALIDATION.md`
      records the string audit that established this.
- [ ] Privacy policy is live at a public URL and linked from inside the app.

Platform (device work — cannot be done from source):

- [ ] Predictive back on API 36 (F-045, F-186).
- [ ] Large-screen and foldable behaviour (F-046, F-187).
- [ ] 16 KB page-size compatibility for the release AAB (F-188).
- [ ] Closed testing with the required tester count and duration (F-197).
- [ ] Packet trace of a release build: four hosts, nothing else.
