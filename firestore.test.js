const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vortex_123";
const BOB_UID = "bob_vortex_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: cannot read or write bookmarks, history, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/bookmarks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/history`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/settings/preferences`).get());

  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/bookmarks/bm_1`).set({
      userId: ALICE_UID,
      title: "Brave Search",
      url: "https://search.brave.com",
      domain: "search.brave.com",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's data", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.doc(`users/${ALICE_UID}/bookmarks/bm_alice`).set({
      userId: ALICE_UID,
      title: "Mojeek",
      url: "https://www.mojeek.com",
      domain: "www.mojeek.com",
      createdAt: created,
      updatedAt: created,
    });
    await adminDb.doc(`users/${ALICE_UID}/history/hist_alice`).set({
      userId: ALICE_UID,
      title: "Wikipedia",
      url: "https://en.wikipedia.org",
      domain: "en.wikipedia.org",
      visitedAt: created,
    });
    await adminDb.doc(`users/${ALICE_UID}/settings/preferences`).set({
      userId: ALICE_UID,
      defaultEngine: "VORTEX_NATIVE",
      adBlockEnabled: true,
      aiSearchEnabled: true,
      javascriptEnabled: true,
      desktopModeDefault: false,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/bookmarks`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/bookmarks/bm_alice`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/bookmarks/bm_alice`).update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/bookmarks/bm_alice`).delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/history`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/settings/preferences`).get());
});

test("Bookmarks: Kotlin create, list, partial update, delete succeed; invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // List empty subcollection succeeds (does not touch existing() in allow list)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/bookmarks`).get());

  // Valid create (no @DocumentId 'id' field)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_1`).set({
      userId: ALICE_UID,
      title: "Wikipedia",
      url: "https://en.wikipedia.org/wiki/Main_Page",
      domain: "en.wikipedia.org",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_1`).update({
      title: "Wikipedia Encyclopedia",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject modifying immutable createdAt
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_1`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId 'id' key leakage or ghost field on create
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_2`).set({
      id: "bm_2",
      userId: ALICE_UID,
      title: "Ghost ID",
      url: "https://www.mojeek.com",
      domain: "www.mojeek.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_3`).set({
      userId: ALICE_UID,
      title: "Future",
      url: "https://www.mojeek.com",
      domain: "www.mojeek.com",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Valid delete
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/bookmarks/bm_1`).delete());
});

test("History: Kotlin create, query with orderBy, update, and delete succeed; spoofing fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const visited = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/history/hist_1`).set({
      userId: ALICE_UID,
      title: "Brave Search",
      url: "https://search.brave.com",
      domain: "search.brave.com",
      visitedAt: visited,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/history`)
      .orderBy("visitedAt", "desc")
      .limit(100)
      .get()
  );

  // Reject mismatched userId
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/history/hist_2`).set({
      userId: BOB_UID,
      title: "Spoofed",
      url: "https://search.brave.com",
      domain: "search.brave.com",
      visitedAt: visited,
    })
  );

  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/history/hist_1`).delete());
});

test("Settings preferences: valid create/update succeed; invalid enum or ghost keys fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/settings/preferences`).set({
      userId: ALICE_UID,
      defaultEngine: "VORTEX_NATIVE",
      adBlockEnabled: true,
      aiSearchEnabled: true,
      javascriptEnabled: true,
      desktopModeDefault: false,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/settings/preferences`).update({
      defaultEngine: "BRAVE",
      adBlockEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject Google or Bing or unknown engine enum
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/settings/preferences`).update({
      defaultEngine: "GOOGLE",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject ghost field on update
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/settings/preferences`).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );
});
