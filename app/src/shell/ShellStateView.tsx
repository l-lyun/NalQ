import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

export type ShellState =
  | 'loading'
  | 'configuration-error'
  | 'load-error'
  | 'renderer-error';

interface ShellStateViewProps {
  onRetry?: () => void;
  state: ShellState;
}

const COPY: Record<
  Exclude<ShellState, 'loading'>,
  { description: string; title: string }
> = {
  'configuration-error': {
    title: '앱 설정을 확인해 주세요',
    description: 'OpenMD 웹 주소가 올바르지 않습니다.',
  },
  'load-error': {
    title: 'OpenMD를 불러오지 못했어요',
    description: '페이지를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  },
  'renderer-error': {
    title: '화면을 다시 불러와야 해요',
    description: '웹 화면이 중단되었습니다. 다시 시도하면 처음 화면부터 복구합니다.',
  },
};

export function ShellStateView({ onRetry, state }: ShellStateViewProps) {
  if (state === 'loading') {
    return (
      <View
        accessibilityLabel="OpenMD를 불러오는 중입니다"
        accessibilityLiveRegion="polite"
        accessibilityRole="progressbar"
        style={styles.container}
      >
        <ActivityIndicator color="#FF6600" size="large" />
        <Text style={styles.loadingText}>OpenMD를 불러오는 중이에요</Text>
      </View>
    );
  }

  const copy = COPY[state];

  return (
    <View
      accessibilityLiveRegion="polite"
      style={styles.container}
    >
      <Text accessibilityRole="header" style={styles.title}>
        {copy.title}
      </Text>
      <Text style={styles.description}>{copy.description}</Text>
      {onRetry ? (
        <Pressable
          accessibilityHint="OpenMD 첫 화면을 다시 불러옵니다"
          accessibilityRole="button"
          hitSlop={8}
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.retryButtonPressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    bottom: 0,
    justifyContent: 'center',
    left: 0,
    paddingHorizontal: 24,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 2,
  },
  loadingText: {
    color: '#5F6672',
    fontSize: 16,
    lineHeight: 24,
    marginTop: 16,
    textAlign: 'center',
  },
  title: {
    color: '#191C20',
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 30,
    textAlign: 'center',
  },
  description: {
    color: '#5F6672',
    fontSize: 16,
    lineHeight: 24,
    marginTop: 8,
    maxWidth: 320,
    textAlign: 'center',
  },
  retryButton: {
    alignItems: 'center',
    backgroundColor: '#FF6600',
    borderRadius: 10,
    justifyContent: 'center',
    marginTop: 24,
    minHeight: 48,
    minWidth: 128,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  retryButtonPressed: {
    backgroundColor: '#E14D00',
  },
  retryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 22,
  },
});
