import {
  createCipheriv, createDecipheriv, randomBytes
} from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

const TOKEN_FILE = process.env.GOOGLE_HOME_TOKEN_FILE || "./data/google-home-token.enc";

function encryptionKey() {
  const hex = process.env.HOME_TOKEN_ENCRYPTION_KEY || "";
  if (!/^[a-f0-9]{64}$/i.test(hex)) {
    throw new Error("HOME_TOKEN_ENCRYPTION_KEY must be a 32-byte (64 hex character) key.");
  }
  return Buffer.from(hex, "hex");
}

export async function loadTokens() {
  try {
    const envelope = JSON.parse(await readFile(TOKEN_FILE, "utf8"));
    if (envelope.version !== 1 || !envelope.iv || !envelope.tag || !envelope.data) {
      throw new Error("Invalid encrypted Google token file.");
    }
    const decipher = createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(envelope.iv, "base64"));
    decipher.setAuthTag(Buffer.from(envelope.tag, "base64"));
    const plaintext = Buffer.concat([
      decipher.update(Buffer.from(envelope.data, "base64")),
      decipher.final()
    ]).toString("utf8");
    return JSON.parse(plaintext);
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }
}

export async function saveTokens(tokens) {
  const key = encryptionKey();
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(JSON.stringify(tokens), "utf8"),
    cipher.final()
  ]);
  const envelope = JSON.stringify({
    version: 1,
    iv: iv.toString("base64"),
    tag: cipher.getAuthTag().toString("base64"),
    data: ciphertext.toString("base64")
  });
  await mkdir(dirname(TOKEN_FILE), { recursive: true, mode: 0o700 });
  const temp = `${TOKEN_FILE}.${randomBytes(8).toString("hex")}.tmp`;
  await writeFile(temp, envelope, { mode: 0o600 });
  await rename(temp, TOKEN_FILE);
}

export async function clearTokens() {
  const { unlink } = await import("node:fs/promises");
  try { await unlink(TOKEN_FILE); } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
}
