import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

import {
  PushStorageRepository,
  encodeBase64Url,
  type InstallationCredentials,
  type KeyValueStorage,
} from './pushStorage';

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

export async function createInstallationCredentials(): Promise<InstallationCredentials> {
  const installationKeyBytes = await Crypto.getRandomBytesAsync(32);

  return {
    installationId: Crypto.randomUUID(),
    installationKey: encodeBase64Url(installationKeyBytes),
    createdAt: new Date().toISOString(),
    tokenVersion: 0,
  };
}
