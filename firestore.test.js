const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_taskflow_123";
const BOB_UID = "bob_taskflow_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function validTaskPayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    title: "Selesaikan laporan mingguan",
    description: "Review angka Q4 dan kirim email",
    status: "TODO",
    priority: "HIGH",
    category: "Work",
    recurrence: "WEEKLY",
    tags: ["work", "weekly"],
    subtasks: [{ id: "st_1", title: "Kumpulkan data", done: false }],
    archived: false,
    deleted: false,
    revision: 1,
    deviceId: "android-a1b2c3d4",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validNotePayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    title: "Catatan Rapat Produk",
    content: "# Agenda\n- [ ] Finalisasi jadwal rilis",
    category: "Work",
    tags: ["meeting"],
    linkedTaskIds: ["tsk_1"],
    pinned: false,
    favorite: false,
    archived: false,
    deleted: false,
    wordCount: 7,
    revision: 1,
    deviceId: "android-a1b2c3d4",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validProjectPayload(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    name: "Peluncuran MVP",
    description: "Target rilis kuartal ini",
    colorHex: "#0F5257",
    iconName: "folder",
    archived: false,
    deleted: false,
    revision: 1,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

// --- 1. UNAUTHENTICATED DENIAL ---

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("tsk_unauth").set(validTaskPayload(ALICE_UID))
  );
});

// --- 2. USER PROFILE ---

test("Owner can create and update own profile; cannot list /users or spoof another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      lastBackupAtMillis: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // Listing /users is denied
  await assertFails(aliceDb.collection("users").get());
  // Bob cannot read or modify Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(1000),
    })
  );
});

// --- 3. TASKS CRUD, PARTIAL UPDATES, IMMUTABILITY & SECURITY BOUNDS ---

test("Owner can create, list, partially update, and complete tasks; invalid payloads rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  // List empty subcollection succeeds (proves allow list does not touch existing())
  await assertSucceeds(tasksCol.get());

  // Valid create (no @DocumentId `id` field)
  await assertSucceeds(tasksCol.doc("tsk_001").set(validTaskPayload(ALICE_UID)));

  // Valid partial completion update (matches toggleTaskCompletion & ReminderReceiver)
  await assertSucceeds(
    tasksCol.doc("tsk_001").update({
      status: "COMPLETED",
      completedAtMillis: Date.now(),
      revision: 2,
      deviceId: "android-a1b2c3d4",
      updatedAt: pastDate(1000),
    })
  );

  // Valid full edit update
  await assertSucceeds(
    tasksCol.doc("tsk_001").update({
      title: "Laporan mingguan selesai direvisi",
      priority: "URGENT",
      revision: 3,
      updatedAt: pastDate(500),
    })
  );

  // Reject ghost field ("Shadow Update Test")
  await assertFails(
    tasksCol.doc("tsk_001").update({
      title: "Shadow",
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject @DocumentId `id` key on create
  await assertFails(
    tasksCol.doc("tsk_bad_id_field").set(validTaskPayload(ALICE_UID, { id: "tsk_bad_id_field" }))
  );

  // Reject immutable createdAt modification
  await assertFails(
    tasksCol.doc("tsk_001").update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    tasksCol.doc("tsk_future").set(validTaskPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject invalid enum status
  await assertFails(
    tasksCol.doc("tsk_bad_enum").set(validTaskPayload(ALICE_UID, { status: "INVALID_STATE" }))
  );

  // Reject oversized title (> 160 chars)
  await assertFails(
    tasksCol.doc("tsk_big_title").set(validTaskPayload(ALICE_UID, { title: "x".repeat(161) }))
  );
});

// --- 4. CROSS-USER ISOLATION ---

test("Cross-user reads and writes are strictly denied across tasks, notes, and projects", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t1").set(validTaskPayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("notes").doc("bob_n1").set(validNotePayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("projects").doc("bob_p1").set(validProjectPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t1").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_t1").update({
      title: "Stolen",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("notes").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
});

// --- 5. NOTES, PROJECTS, REMINDERS & BACKUPS ---

test("Owner can manage notes, projects, reminders, and immutable cloud backups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Projects
  await assertSucceeds(userDoc.collection("projects").doc("prj_1").set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(
    userDoc.collection("projects").doc("prj_1").update({
      archived: true,
      revision: 2,
      updatedAt: pastDate(500),
    })
  );

  // Notes
  await assertSucceeds(userDoc.collection("notes").doc("nte_1").set(validNotePayload(ALICE_UID)));
  await assertSucceeds(
    userDoc.collection("notes").doc("nte_1").update({
      pinned: true,
      favorite: true,
      revision: 2,
      deviceId: "android-a1b2c3d4",
      updatedAt: pastDate(500),
    })
  );

  // Reminders
  const ts = pastDate(3000);
  await assertSucceeds(
    userDoc.collection("reminders").doc("rem_1").set({
      userId: ALICE_UID,
      taskId: "tsk_001",
      title: "Pengingat Tugas",
      message: "Waktunya review laporan",
      reminderType: "TASK_REMINDER",
      scheduledAtMillis: Date.now() + 60000,
      dismissed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    userDoc.collection("reminders").doc("rem_1").update({
      dismissed: true,
      updatedAt: pastDate(500),
    })
  );

  // Cloud Backups (create & read allowed; update immutable -> denied!)
  await assertSucceeds(
    userDoc.collection("backups").doc("bkp_1").set({
      userId: ALICE_UID,
      formatVersion: 1,
      label: "Backup Harian",
      taskCount: 1,
      noteCount: 1,
      projectCount: 1,
      checksum: "abcdef1234567890",
      snapshotData: {
        projects: [],
        tasks: [],
        notes: [],
      },
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(userDoc.collection("backups").get());
  await assertFails(
    userDoc.collection("backups").doc("bkp_1").update({
      label: "Tampered",
      updatedAt: pastDate(500),
    })
  );
});
