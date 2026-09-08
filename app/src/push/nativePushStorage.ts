import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

import {
  PushStorageRepository,
  encodeBase64Url,
  type InstallationCredentials,
  type KeyValueStorage,
} from './pushStorage';
import {
  PushOpenStorageRepository,
  resolveRecentRegistrationAckOwner,
} from './pushOpenStorage';

const secureStoreOptions: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
  keychainService: 'com.nalq.app.push',
};

const secureKeyValueStorage: KeyValueStorage = {
  getItem(key) {
    return SecureStore.getItemAsync(key, secureStoreOptions);
  },
  setItem(key, value) {
    return SecureStore.setItemAsync(key, value, secureStoreOptions);
  },
};

export const nativePushStorage = new PushStorageRepository(secureKeyValueStorage);
export const nativePushOpenStorage = new PushOpenStorageRepository(secureKeyValueStorage);

export async function resolveNativeBindingOwner(bindingId: string) {
  const now = new Date().toISOString();
  const recordedOwner = await nativePushOpenStorage.findBindingOwner(
    bindingId,
    now,
  );
  if (recordedOwner !== null) {
    return recordedOwner;
  }
  const state = await nativePushStorage.load();
  if (state?.activeBinding?.bindingId === bindingId) {
    return state.activeBinding.userId;
  }
  return resolveRecentRegistrationAckOwner(bindingId, state?.lastRegistrationAck, now);
}

export function recordNativeBindingOwner(
  bindingId: string,
  userId: number,
  recordedAt = new Date().toISOString(),
) {
  return nativePushOpenStorage.recordBindingOwner(
    bindingId,
    userId,
    recordedAt,
  ).then(() => undefined);
}

export async function seedNativeBindingOwnerHistory() {
  const state = await nativePushStorage.load();
  const candidates = [
    state?.lastRegistrationAck?.bindingId
      ? {
          bindingId: state.lastRegistrationAck.bindingId,
          userId: state.lastRegistrationAck.userId,
          recordedAt: state.lastRegistrationAck.completedAt,
        }
      : null,
    state?.activeBinding
      ? {
          bindingId: state.activeBinding.bindingId,
          userId: state.activeBinding.userId,
          recordedAt: new Date().toISOString(),
        }
      : null,
  ].filter((candidate): candidate is {
    bindingId: string;
    userId: number;
    recordedAt: string;
  } => candidate !== null);

  for (const candidate of candidates) {
    await recordNativeBindingOwner(
      candidate.bindingId,
      candidate.userId,
      candidate.recordedAt,
    );
  }
}

export async function createInstallationCredentials(): Promise<InstallationCredentials> {
  const installationKeyBytes = await Crypto.getRandomBytesAsync(32);

  return {
    installationId: Crypto.randomUUID(),
    installationKey: encodeBase64Url(installationKeyBytes),
    createdAt: new Date().toISOString(),
    tokenVersion: 0,
  };
}
