import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import * as Location from 'expo-location';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import { useShareIntent } from 'expo-share-intent';

// 포그라운드에서도 배너가 뜨도록.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

// 안드로이드는 채널이 없으면 배너/소리가 무시된다. API 26+ 필수.
const CHANNEL_ID = 'test-default';

async function ensureAndroidChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
    name: '테스트 알림',
    importance: Notifications.AndroidImportance.MAX,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: '#2563EB',
    sound: 'default',
  });
}

// 에뮬레이터는 좌표를 한 번도 주입받지 않으면 응답 없이 멈춘다.
function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error(`${ms / 1000}초 내 응답 없음`)), ms),
    ),
  ]);
}

const now = () => new Date().toLocaleTimeString();

export default function App() {
  return (
    <SafeAreaProvider>
      <TestScreen />
    </SafeAreaProvider>
  );
}

function TestScreen() {
  // 알림
  const [notiPermission, setNotiPermission] = useState<string>('확인 전');
  const [channelReady, setChannelReady] = useState<boolean>(false);
  const [scheduledCount, setScheduledCount] = useState<number>(0);
  const [notiLog, setNotiLog] = useState<string[]>([]);

  // 위치
  const [coords, setCoords] = useState<Location.LocationObjectCoords | null>(null);
  const [coordsSource, setCoordsSource] = useState<string | null>(null);
  const [locationLoading, setLocationLoading] = useState<boolean>(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [servicesEnabled, setServicesEnabled] = useState<boolean | null>(null);

  // 공유 수신
  const { hasShareIntent, shareIntent, resetShareIntent, error: shareError } =
    useShareIntent();

  const pushLog = useCallback((line: string) => {
    setNotiLog((prev) => [`[${now()}] ${line}`, ...prev].slice(0, 12));
  }, []);

  const refreshNotificationState = useCallback(async () => {
    const { status } = await Notifications.getPermissionsAsync();
    setNotiPermission(status);
    const scheduled = await Notifications.getAllScheduledNotificationsAsync();
    setScheduledCount(scheduled.length);
  }, []);

  useEffect(() => {
    (async () => {
      await ensureAndroidChannel();
      setChannelReady(true);
      await refreshNotificationState();
    })().catch((e) => pushLog(`초기화 실패: ${String(e)}`));

    fetchLocation();
  }, []);

  // 알림이 실제로 도착했는지 앱 안에서도 확인한다.
  useEffect(() => {
    const received = Notifications.addNotificationReceivedListener((n) => {
      pushLog(`수신: ${n.request.content.title ?? '(제목 없음)'}`);
    });
    const responded = Notifications.addNotificationResponseReceivedListener((r) => {
      pushLog(`탭함: ${r.notification.request.content.title ?? '(제목 없음)'}`);
    });
    return () => {
      received.remove();
      responded.remove();
    };
  }, [pushLog]);

  const ensurePermission = async (): Promise<boolean> => {
    const current = await Notifications.getPermissionsAsync();
    let status = current.status;
    if (status !== 'granted') {
      const requested = await Notifications.requestPermissionsAsync();
      status = requested.status;
    }
    setNotiPermission(status);
    if (status !== 'granted') {
      Alert.alert(
        '알림 권한 없음',
        Platform.OS === 'android'
          ? 'Android 13+ 는 POST_NOTIFICATIONS 권한이 필요합니다. 설정 > 앱 > 알림에서 허용하세요.'
          : '설정에서 알림을 허용하세요.',
      );
      return false;
    }
    return true;
  };

  const handleQuickNotification = async () => {
    try {
      if (!(await ensurePermission())) return;
      await ensureAndroidChannel();
      await Notifications.scheduleNotificationAsync({
        content: {
          title: '5초 뒤 테스트 알림',
          body: '앱을 홈으로 내려도 도착하는지 확인하세요.',
          sound: true,
        },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
          seconds: 5,
          repeats: false,
          channelId: CHANNEL_ID,
        },
      });
      pushLog('예약: 5초 단발');
      await refreshNotificationState();
    } catch (e) {
      Alert.alert('알림 예약 실패', String(e));
    }
  };

  const handlePeriodicNotification = async () => {
    try {
      if (!(await ensurePermission())) return;
      await ensureAndroidChannel();
      await Notifications.cancelAllScheduledNotificationsAsync();
      await Notifications.scheduleNotificationAsync({
        content: {
          title: '1분 주기 테스트 알림',
          body: '60초마다 반복됩니다.',
          sound: true,
        },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
          seconds: 60,
          repeats: true,
          channelId: CHANNEL_ID,
        },
      });
      pushLog('예약: 60초 반복');
      await refreshNotificationState();
    } catch (e) {
      Alert.alert('알림 예약 실패', String(e));
    }
  };

  const handleCancelAll = async () => {
    await Notifications.cancelAllScheduledNotificationsAsync();
    pushLog('예약 전체 취소');
    await refreshNotificationState();
  };

  async function fetchLocation() {
    setLocationLoading(true);
    setLocationError(null);
    setCoordsSource(null);
    try {
      const enabled = await Location.hasServicesEnabledAsync();
      setServicesEnabled(enabled);
      if (!enabled) {
        setLocationError('기기의 위치 서비스가 꺼져 있습니다.');
        return;
      }

      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setLocationError(`위치 권한 거부됨 (${status})`);
        return;
      }

      try {
        const pos = await withTimeout(
          Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
          10000,
        );
        setCoords(pos.coords);
        setCoordsSource('getCurrentPositionAsync');
      } catch (primaryError) {
        const last = await Location.getLastKnownPositionAsync();
        if (last) {
          setCoords(last.coords);
          setCoordsSource('getLastKnownPositionAsync (폴백)');
        } else {
          throw primaryError;
        }
      }
    } catch (e) {
      setCoords(null);
      setLocationError(String(e));
    } finally {
      setLocationLoading(false);
    }
  }

  const isExpoGo = Constants.executionEnvironment === 'storeClient';

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>기능 검증 테스트</Text>
        <Text style={styles.subtitle}>알림 / 위치 / 공유 수신</Text>

        {/* 실행 환경 */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>0. 실행 환경</Text>
          <Row label="플랫폼" value={`${Platform.OS} (API ${Platform.Version})`} />
          <Row label="기기 종류" value={Device.isDevice ? '실기기' : '에뮬레이터'} />
          <Row label="모델" value={Device.modelName ?? '알 수 없음'} />
          <Row label="실행 환경" value={Constants.executionEnvironment ?? '알 수 없음'} />
          {isExpoGo && (
            <Text style={styles.errorText}>
              Expo Go 로 실행 중입니다. 공유 수신과 원격 푸시는 동작하지 않습니다.
              {'\n'}expo run:android 로 만든 개발 빌드로 실행하세요.
            </Text>
          )}
        </View>

        {/* 알림 */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>1. 알림</Text>
          <Row label="권한" value={notiPermission} />
          <Row
            label="채널"
            value={
              Platform.OS === 'android'
                ? channelReady
                  ? `${CHANNEL_ID} (생성됨)`
                  : '생성 중'
                : '해당 없음 (iOS)'
            }
          />
          <Row label="예약된 알림" value={`${scheduledCount}개`} />

          <View style={styles.buttonGroup}>
            <TouchableOpacity style={styles.buttonPrimary} onPress={handleQuickNotification}>
              <Text style={styles.buttonText}>5초 뒤 알림</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.buttonSecondary} onPress={handlePeriodicNotification}>
              <Text style={styles.buttonSecondaryText}>1분 주기 알림 시작</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.buttonDanger} onPress={handleCancelAll}>
              <Text style={styles.buttonDangerText}>예약 전체 취소</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.logTitle}>수신 로그</Text>
          <View style={styles.logBox}>
            {notiLog.length === 0 ? (
              <Text style={styles.logEmpty}>(아직 없음)</Text>
            ) : (
              notiLog.map((line, i) => (
                <Text key={i} style={styles.logLine}>
                  {line}
                </Text>
              ))
            )}
          </View>
        </View>

        {/* 위치 */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>2. 위치</Text>
          <Row
            label="위치 서비스"
            value={servicesEnabled === null ? '확인 전' : servicesEnabled ? '켜짐' : '꺼짐'}
          />

          {locationLoading ? (
            <ActivityIndicator size="small" color="#2563EB" style={styles.spinner} />
          ) : coords ? (
            <View style={styles.infoBox}>
              <Row label="위도" value={`${coords.latitude.toFixed(6)}°`} />
              <Row label="경도" value={`${coords.longitude.toFixed(6)}°`} />
              <Row label="정확도" value={`약 ${Math.round(coords.accuracy ?? 0)}m`} />
              {coordsSource && <Text style={styles.subText}>출처: {coordsSource}</Text>}
            </View>
          ) : (
            <Text style={styles.errorText}>{locationError ?? '위치 정보 없음'}</Text>
          )}

          {!Device.isDevice && (
            <Text style={styles.hint}>
              에뮬레이터는 좌표가 비어 있으면 응답하지 않습니다.{'\n'}
              에뮬레이터 창의 ··· &gt; Location 에서 좌표를 먼저 주입한 뒤 다시 조회하세요.
            </Text>
          )}

          <TouchableOpacity style={styles.buttonPrimary} onPress={fetchLocation}>
            <Text style={styles.buttonText}>현재 위치 다시 조회</Text>
          </TouchableOpacity>
        </View>

        {/* 공유 수신 */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>3. 공유 수신</Text>
          <Text style={styles.description}>
            Chrome 등 다른 앱에서 공유 &gt; 이 앱을 선택하면 아래에 원본 데이터가 찍힙니다.
          </Text>

          {shareError && <Text style={styles.errorText}>오류: {String(shareError)}</Text>}

          <Row label="수신 여부" value={hasShareIntent ? '수신됨' : '없음'} />
          <Row label="타입" value={shareIntent?.type ?? '-'} />

          <View style={[styles.logBox, hasShareIntent && styles.logBoxActive]}>
            <Text style={styles.logLine} selectable>
              {JSON.stringify(shareIntent, null, 2)}
            </Text>
          </View>

          {hasShareIntent && (
            <TouchableOpacity style={styles.buttonDanger} onPress={() => resetShareIntent()}>
              <Text style={styles.buttonDangerText}>수신 내용 초기화</Text>
            </TouchableOpacity>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue} selectable>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F1F5F9',
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  title: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#0F172A',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#64748B',
    marginBottom: 16,
  },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    elevation: 2,
  },
  cardTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#1E293B',
    marginBottom: 12,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 6,
    gap: 12,
  },
  rowLabel: {
    fontSize: 14,
    color: '#64748B',
  },
  rowValue: {
    flexShrink: 1,
    fontSize: 14,
    fontWeight: '600',
    color: '#0F172A',
    textAlign: 'right',
  },
  infoBox: {
    backgroundColor: '#F8FAFC',
    borderRadius: 8,
    padding: 12,
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  description: {
    fontSize: 13,
    color: '#64748B',
    marginBottom: 10,
    lineHeight: 18,
  },
  subText: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 4,
  },
  hint: {
    fontSize: 12,
    color: '#B45309',
    backgroundColor: '#FEF3C7',
    borderRadius: 8,
    padding: 10,
    marginVertical: 10,
    lineHeight: 18,
  },
  spinner: {
    marginVertical: 12,
  },
  logTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748B',
    marginTop: 14,
    marginBottom: 6,
  },
  logBox: {
    backgroundColor: '#F8FAFC',
    borderRadius: 8,
    padding: 10,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  logBoxActive: {
    backgroundColor: '#EFF6FF',
    borderColor: '#3B82F6',
  },
  logLine: {
    fontSize: 11,
    color: '#334155',
    fontFamily: Platform.OS === 'android' ? 'monospace' : 'Menlo',
    lineHeight: 16,
  },
  logEmpty: {
    fontSize: 11,
    color: '#94A3B8',
  },
  buttonGroup: {
    gap: 8,
    marginTop: 12,
  },
  buttonPrimary: {
    backgroundColor: '#2563EB',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600',
  },
  buttonSecondary: {
    backgroundColor: '#E2E8F0',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonSecondaryText: {
    color: '#334155',
    fontSize: 14,
    fontWeight: '600',
  },
  buttonDanger: {
    backgroundColor: '#FEE2E2',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDangerText: {
    color: '#DC2626',
    fontSize: 14,
    fontWeight: '600',
  },
  errorText: {
    color: '#EF4444',
    fontSize: 13,
    marginVertical: 8,
    lineHeight: 18,
  },
});
