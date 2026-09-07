import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import type {
  PushRegistrationProvider,
  PushRegistrationTarget,
} from './pushRegistrationCoordinator';

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

export class ExpoPushRegistrationProvider implements PushRegistrationProvider {
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
    if (!isAuthorized(permission)) {
      return { platform, permission: 'DENIED', pushToken: null };
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
    return { platform, permission: 'GRANTED', pushToken };
  }

  subscribeToTokenChanges(listener: () => void) {
    return Notifications.addPushTokenListener(() => listener());
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
