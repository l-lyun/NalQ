import {
  getMessaging,
  getToken as getFcmToken,
  onTokenRefresh,
} from '@react-native-firebase/messaging';
import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import type {
  PushRegistrationProvider,
  PushRegistrationTarget,
} from './pushRegistrationCoordinator';
import { parsePushOpenCandidate, type PushOpenCandidate } from './pushOpenStorage';

const ANDROID_CHANNEL_ID = 'quiz-results';

function isAuthorized(status: Notifications.NotificationPermissionsStatus) {
  if (status.granted) {
    return true;
  }
  const iosStatus = status.ios?.status;
  return iosStatus === Notifications.IosAuthorizationStatus.AUTHORIZED
    || iosStatus === Notifications.IosAuthorizationStatus.PROVISIONAL
    || iosStatus === Notifications.IosAuthorizationStatus.EPHEMERAL;
}

function isUndetermined(status: Notifications.NotificationPermissionsStatus) {
  if (Platform.OS === 'ios') {
    return status.ios?.status === Notifications.IosAuthorizationStatus.NOT_DETERMINED;
  }
  return status.status === 'undetermined';
}

async function prepareAndroidChannel() {
  if (Platform.OS !== 'android') {
    return;
  }
  await Notifications.setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
    name: '퀴즈 생성 결과',
    importance: Notifications.AndroidImportance.DEFAULT,
  });
}

export class NativePushRegistrationProvider implements PushRegistrationProvider {
  private lastNativeTokenSignature: string | null = null;

  async resolve(): Promise<PushRegistrationTarget> {
    if (Platform.OS !== 'ios' && Platform.OS !== 'android') {
      throw new Error('Push registration is available only on iOS and Android.');
    }

    await prepareAndroidChannel();
    let permission = await Notifications.getPermissionsAsync();
    if (isUndetermined(permission)) {
      permission = await Notifications.requestPermissionsAsync();
    }

    const platform = Platform.OS === 'ios' ? 'IOS' as const : 'ANDROID' as const;
    const provider = Platform.OS === 'ios' ? 'FCM' as const : 'EXPO' as const;
    if (!isAuthorized(permission)) {
      return { platform, provider, permission: 'DENIED', pushToken: null };
    }

    if (Platform.OS === 'ios') {
      // expo-notifications가 APNs 등록을 완료한 뒤 Firebase가 그 APNs token에
      // 연결된 FCM registration token을 발급하도록 순서를 보장한다.
      await Notifications.getDevicePushTokenAsync();
      const pushToken = await getFcmToken(getMessaging());
      if (typeof pushToken !== 'string' || pushToken.length === 0) {
        throw new Error('Firebase returned an invalid FCM token.');
      }
      return { platform, provider, permission: 'GRANTED', pushToken };
    }

    const projectId = Constants.expoConfig?.extra?.eas?.projectId
      ?? Constants.easConfig?.projectId;
    if (typeof projectId !== 'string' || projectId.length === 0) {
      throw new Error('EAS projectId is required to issue an Expo push token.');
    }

    const pushToken = (await Notifications.getExpoPushTokenAsync({ projectId })).data;
    if (typeof pushToken !== 'string' || pushToken.length === 0) {
      throw new Error('Expo returned an invalid push token.');
    }
    return { platform, provider, permission: 'GRANTED', pushToken };
  }

  subscribeToTokenChanges(listener: () => void): { remove(): void } {
    if (Platform.OS === 'ios') {
      const unsubscribe = onTokenRefresh(getMessaging(), (token) => {
        if (token === this.lastNativeTokenSignature) {
          return;
        }
        this.lastNativeTokenSignature = token;
        listener();
      });
      return { remove: unsubscribe };
    }

    return Notifications.addPushTokenListener((token) => {
      const signature = JSON.stringify(token);
      if (signature === this.lastNativeTokenSignature) {
        return;
      }
      this.lastNativeTokenSignature = signature;
      listener();
    });
  }
}

function toPushOpenCandidate(
  response: Notifications.NotificationResponse | null,
): PushOpenCandidate | null {
  if (!response || response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) {
    return null;
  }
  const data = response.notification.request.content.data;
  if (!data) {
    return null;
  }
  return parsePushOpenCandidate({
    ...data,
    sdkResponseId: response.notification.request.identifier,
  });
}

export class ExpoNotificationResponseProvider {
  getLastCandidate() {
    return toPushOpenCandidate(Notifications.getLastNotificationResponse());
  }

  subscribe(listener: (candidate: PushOpenCandidate) => void) {
    return Notifications.addNotificationResponseReceivedListener((response) => {
      const candidate = toPushOpenCandidate(response);
      if (candidate) {
        listener(candidate);
      }
    });
  }

  clearLastResponseIfMatches(sdkResponseId: string) {
    const candidate = this.getLastCandidate();
    if (candidate?.sdkResponseId === sdkResponseId) {
      Notifications.clearLastNotificationResponse();
    }
  }
}

export function installForegroundNotificationSuppression() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldPlaySound: false,
      shouldSetBadge: false,
      shouldShowBanner: false,
      shouldShowList: false,
    }),
  });
}
