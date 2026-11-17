const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user: cannot read or write any CallFlow collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("call_lists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("leads").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("call_logs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("templates").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("call_lists").doc("list_1").set({
      userId: ALICE_UID,
      name: "Unauthorized List",
      description: "",
      totalLeads: 0,
      dialedLeads: 0,
      status: "ACTIVE",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("AgentProfile: owner create, partial update, reject ghost fields and cross-user read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Agent",
      email: "alice@example.com",
      autoDialDelaySec: 5,
      dailyCallTarget: 50,
      defaultCountryCode: "+91",
      autoAdvanceDialer: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      autoDialDelaySec: 8,
      dailyCallTarget: 75,
      autoAdvanceDialer: false,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject cross-user access
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
});

test("CallList & Lead & CallLog: full Kotlin-mirrored lifecycle and bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Create CallList
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_lists").doc("list_1").set({
      userId: ALICE_UID,
      name: "Inbound SaaS Leads",
      description: "November trial signups",
      totalLeads: 1,
      dialedLeads: 0,
      status: "ACTIVE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra @DocumentId 'id' field in CallList
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_lists").doc("list_2").set({
      id: "list_2",
      userId: ALICE_UID,
      name: "Bad List",
      description: "",
      totalLeads: 0,
      dialedLeads: 0,
      status: "ACTIVE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create Lead
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_1").set({
      userId: ALICE_UID,
      listId: "list_1",
      listName: "Inbound SaaS Leads",
      fullName: "Rohan Mehta",
      phoneNumber: "+919876543210",
      company: "Vertex Logistics",
      email: "rohan@vertex.in",
      status: "PENDING",
      lastDisposition: "",
      notes: "Requested demo on website",
      callAttempts: 0,
      lastDurationSec: 0,
      callbackAtMillis: 0,
      callbackCompleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Record Disposition update on Lead
  const callbackMillis = Date.now() + 3600000;
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_1").update({
      status: "CALLBACK",
      lastDisposition: "CALLBACK",
      notes: "Asked to call back at 4 PM",
      callAttempts: 1,
      lastDurationSec: 94,
      callbackAtMillis: callbackMillis,
      callbackCompleted: false,
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable createdAt tamper on Lead
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("leads").doc("lead_1").update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Create immutable CallLog
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_logs").doc("log_1").set({
      userId: ALICE_UID,
      leadId: "lead_1",
      listId: "list_1",
      leadName: "Rohan Mehta",
      phoneNumber: "+919876543210",
      disposition: "CALLBACK",
      notes: "Asked to call back at 4 PM",
      durationSec: 94,
      callbackAtMillis: callbackMillis,
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // CallLog updates must be rejected (immutable audit log)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_logs").doc("log_1").update({
      disposition: "INTERESTED",
      updatedAt: pastDate(500),
    })
  );

  // Update CallList dialedLeads
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("call_lists").doc("list_1").update({
      dialedLeads: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Query tests matching Kotlin repositories
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("call_lists").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("leads").where("listId", "==", "list_1").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("call_logs").get());

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("leads").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("call_logs").get());
});

test("MessageTemplate: valid CRUD and future timestamp rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_1").set({
      userId: ALICE_UID,
      title: "Intro Follow-Up",
      channel: "WHATSAPP",
      body: "Hi {name}, thanks for speaking with us today!",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_1").update({
      body: "Hi {name}, sharing our proposal link.",
      updatedAt: pastDate(1000),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_2").set({
      userId: ALICE_UID,
      title: "Future Bad",
      channel: "SMS",
      body: "Bad timestamp",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
