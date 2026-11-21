const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validBackupPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Katana Slash Loop",
    projectType: "ANIMATION",
    fps: 12,
    aspectRatio: "16:9",
    sourceUpdatedAtMs: Date.now(),
    onionSkin: {
      enabled: true,
      showBefore: true,
      showAfter: true,
      beforeCount: 1,
      afterCount: 1,
      beforeOpacity: 0.35,
      afterOpacity: 0.25,
      tintEnabled: true,
    },
    frames: [
      {
        id: "frame_1",
        frameNumber: 1,
        holdFrames: 1,
        layers: [
          {
            id: "layer_1",
            name: "Ink",
            visible: true,
            opacity: 1.0,
            locked: false,
            strokes: [],
          },
        ],
      },
    ],
    videoClips: [],
    audioClips: [],
    framesCount: 1,
    videoClipsCount: 0,
    audioClipsCount: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write project backups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("project_backups").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("project_backups").doc("proj_1").set(
      validBackupPayload(ALICE_UID)
    )
  );
});

test("Authenticated owner can create, partial-update, list, and delete project backups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("project_backups");
  const docRef = colRef.doc("proj_katana_01");

  await assertSucceeds(docRef.set(validBackupPayload(ALICE_UID)));
  await assertSucceeds(colRef.get());
  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      title: "Katana Slash v2",
      fps: 24,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field injection
  await assertFails(
    colRef.doc("proj_ghost").set(validBackupPayload(ALICE_UID, { id: "proj_ghost" }))
  );

  // Reject immutable createdAt modification
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid projectType enum or oversized title
  await assertFails(
    colRef.doc("proj_bad_type").set(validBackupPayload(ALICE_UID, { projectType: "UNKNOWN" }))
  );
  await assertFails(
    colRef.doc("proj_bad_title").set(validBackupPayload(ALICE_UID, { title: "A".repeat(120) }))
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user access to another user's project backups is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("project_backups")
      .doc("bob_proj")
      .set(validBackupPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobCol = aliceDb.collection("users").doc(BOB_UID).collection("project_backups");
  await assertFails(bobCol.get());
  await assertFails(bobCol.doc("bob_proj").get());
  await assertFails(bobCol.doc("bob_proj").update({ title: "Hijacked" }));
  await assertFails(bobCol.doc("bob_proj").delete());
});
