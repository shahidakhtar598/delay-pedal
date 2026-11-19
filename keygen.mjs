#!/usr/bin/env node
/**
 * DD-7X Vocal Delay Pedal — Zero-Dependency Offline Ed25519 Keygen CLI
 * Usage:
 *   node keygen.mjs init                           # Generate Ed25519 keypair (saves private/public keys)
 *   node keygen.mjs sign SHD-8F4A-91C2             # Sign an offline activation token for a buyer's Device ID
 *   node keygen.mjs verify SHD-8F4A-91C2 <TOKEN>   # Verify an offline activation token
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const KEYS_FILE = path.join(__dirname, "keys.json");

function base64Url(str) {
  return Buffer.from(str, "utf8").toString("base64url");
}

function cmdInit() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ed25519");
  const spkiDer = publicKey.export({ type: "spki", format: "der" });
  // Raw 32-byte Ed25519 public key is the last 32 bytes of SPKI DER (for Android BouncyCastle)
  const rawPub32 = spkiDer.subarray(spkiDer.length - 32).toString("base64");
  const pkcs8Priv = privateKey.export({ type: "pkcs8", format: "der" }).toString("base64");

  const data = {
    created_at: new Date().toISOString(),
    LICENSE_PUBLIC_KEY_ED25519: rawPub32,
    LICENSE_PRIVATE_KEY_ED25519: pkcs8Priv,
  };
  fs.writeFileSync(KEYS_FILE, JSON.stringify(data, null, 2), { mode: 0o600 });
  console.log("Generated Ed25519 keypair ->", KEYS_FILE);
  console.log("Put this in android-app/local.properties:");
  console.log("  LICENSE_PUBLIC_KEY_ED25519=" + rawPub32);
  console.log("Keep LICENSE_PRIVATE_KEY_ED25519 strictly secret (Supabase Secrets / local admin only).");
}

function cmdSign(deviceId, tier = "PRO_LIFETIME") {
  if (!deviceId) {
    console.error("Error: Provide buyer's Device ID, e.g. node keygen.mjs sign SHD-8F4A-91C2");
    process.exit(1);
  }
  const keys = JSON.parse(fs.readFileSync(KEYS_FILE, "utf8"));
  const privKey = crypto.createPrivateKey({
    key: Buffer.from(keys.LICENSE_PRIVATE_KEY_ED25519, "base64"),
    format: "der",
    type: "pkcs8",
  });

  const payloadB64 = base64Url(JSON.stringify({ device_id: deviceId.trim().toUpperCase(), tier }));
  const sig = crypto.sign(null, Buffer.from(payloadB64, "utf8"), privKey).toString("base64");
  const token = `${payloadB64}.${sig}`;
  console.log("OFFLINE ACTIVATION TOKEN (Send to buyer on WhatsApp):");
  console.log(token);
}

function cmdVerify(deviceId, token) {
  const keys = JSON.parse(fs.readFileSync(KEYS_FILE, "utf8"));
  const rawPub = Buffer.from(keys.LICENSE_PUBLIC_KEY_ED25519, "base64");
  // Wrap raw 32-byte key in Ed25519 SPKI header
  const spkiPrefix = Buffer.from("302a300506032b6570032100", "hex");
  const pubKey = crypto.createPublicKey({
    key: Buffer.concat([spkiPrefix, rawPub]),
    format: "der",
    type: "spki",
  });

  const [payloadB64, sigB64] = (token || "").trim().split(".");
  if (!payloadB64 || !sigB64) {
    console.error("INVALID TOKEN FORMAT");
    process.exit(1);
  }
  const ok = crypto.verify(null, Buffer.from(payloadB64, "utf8"), pubKey, Buffer.from(sigB64, "base64"));
  const payload = JSON.parse(Buffer.from(payloadB64, "base64url").toString("utf8"));
  const idMatch = payload.device_id === deviceId.trim().toUpperCase();
  if (ok && idMatch) {
    console.log("[PASS] Signature & Device ID verified:", payload);
  } else {
    console.error("[FAIL] Verification failed:", { ok, idMatch, payload });
    process.exit(1);
  }
}

const [cmd, arg1, arg2] = process.argv.slice(2);
if (cmd === "init") cmdInit();
else if (cmd === "sign") cmdSign(arg1, arg2);
else if (cmd === "verify") cmdVerify(arg1, arg2);
else {
  console.log("Usage: node keygen.mjs <init | sign DEVICE_ID | verify DEVICE_ID TOKEN>");
}
