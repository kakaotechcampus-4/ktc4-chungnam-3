// 저장 결과 모달. 분석 중 / 확인 필요 / 장소 없음 / 실패 상태별로 렌더한다.
// 서버를 거쳐야 하는 버튼(저장·직접 찾기·장소 붙이기·보관·다시 시도)은 아직 동작하지 않는다.
import { type RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { saveResultsMock } from '../../shared/api/mock';
import type { BadgeStatus } from '../../shared/ui/Badge';
import Button from '../../shared/ui/Button';
import Icon from '../../shared/ui/Icon';
import { colors, metrics, size, spacing, typography } from '../../shared/ui/theme';
import AnalyzingSkeleton from './components/AnalyzingSkeleton';
import PlaceOption from './components/PlaceOption';
import SourceHeader, { type SourceHeaderProps } from './components/SourceHeader';

// UI 타입이다. 서버 이름과의 대응은 백엔드 계약이 생기면 mappers 에서 한다.
type SaveResultState = 'analyzing' | 'needsConfirmation' | 'noPlace' | 'failed';

type Source = Omit<SourceHeaderProps, 'status'>;
type Candidate = { id: string; name: string; address: string };

type SaveResult =
  | { state: 'needsConfirmation'; source: Source; candidates: readonly Candidate[] }
  | { state: Exclude<SaveResultState, 'needsConfirmation'>; source: Source };

const results: Readonly<Record<string, SaveResult>> = saveResultsMock;

// 02 에는 배지가 없다 (Figma).
const BADGE: Record<SaveResultState, BadgeStatus | undefined> = {
  analyzing: 'analyzing',
  needsConfirmation: undefined,
  noPlace: 'noPlace',
  failed: 'failed',
};

const COUNT_WORDS = ['한', '두', '세', '네', '다섯'];

// 1~5 는 "두 곳", 그 이상은 "6곳".
function placeCount(n: number): string {
  const word = COUNT_WORDS[n - 1];
  return word ? `${word} 곳` : `${n}곳`;
}

// 받침이 있으면 "으로", 없거나 ㄹ 받침이면 "로". 마지막 글자가 한글이 아니면 "(으)로".
function withRo(word: string): string {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 0xd7a3 - 0xac00) return `${word}(으)로`;
  const jong = code % 28;
  return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`;
}

export default function SaveResultScreen() {
  const navigation = useNavigation();
  // RootParamList 는 interface 라 ParamListBase 제약을 못 맞춘다. Pick 으로 타입 별칭을 만든다.
  const route = useRoute<RouteProp<Pick<ReactNavigation.RootParamList, 'SaveResult'>, 'SaveResult'>>();
  const insets = useSafeAreaInsets();

  const result = results[route.params.resultId];
  const candidates = result?.state === 'needsConfirmation' ? result.candidates : [];
  const [selectedId, setSelectedId] = useState(candidates[0]?.id);

  if (!result) return null;

  const close = () => navigation.goBack();
  const selected = candidates.find((candidate) => candidate.id === selectedId);

  return (
    <ScrollView
      contentContainerStyle={[
        styles.container,
        { paddingTop: insets.top, paddingBottom: insets.bottom + spacing.xl },
      ]}
    >
      <View style={styles.topBar}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="닫기"
          hitSlop={metrics.saveResult.closeHitSlop}
          onPress={close}
        >
          <Icon name="close" size={size.iconMd} />
        </Pressable>
      </View>

      <SourceHeader {...result.source} status={BADGE[result.state]} />

      {result.state === 'needsConfirmation' && (
        <>
          <Message
            heading="이 영상, 어디였을까요?"
            body={`영상에서 ${placeCount(candidates.length)}을 찾았어요. 맞는 곳을 골라주세요.`}
          />
          <View style={styles.options} accessibilityRole="radiogroup">
            {candidates.map((candidate) => (
              <PlaceOption
                key={candidate.id}
                name={candidate.name}
                address={candidate.address}
                selected={candidate.id === selectedId}
                onPress={() => setSelectedId(candidate.id)}
              />
            ))}
            <Pressable accessibilityRole="button" hitSlop={metrics.directOption.hitSlop} style={styles.direct}>
              <Icon name="pencil" size={size.iconSm} />
              <Text style={styles.directLabel}>여기 없어요, 직접 찾을게요</Text>
            </Pressable>
          </View>
        </>
      )}

      {result.state === 'analyzing' && (
        <>
          <Message
            heading="영상 속 장소를 찾고 있어요"
            body="보통 10초 안에 끝나요. 이 화면을 닫아도 계속 찾아요."
          />
          <AnalyzingSkeleton />
        </>
      )}

      {result.state === 'noPlace' && (
        <Message
          heading="이 영상에선 장소를 못 찾았어요"
          body="장소 이름이 나오지 않는 영상이었어요. 어딘지 알고 있다면 직접 붙여둘 수 있어요."
        />
      )}

      {result.state === 'failed' && (
        <Message
          heading="영상을 불러오지 못했어요"
          body="비공개로 바뀌었거나 연결이 잠깐 불안정했어요. 링크는 그대로 보관해둘게요."
        />
      )}

      <View style={styles.spacer} />

      <View style={styles.actions}>
        {result.state === 'needsConfirmation' && (
          <>
            {selected && <Button kind="primary" label={`${withRo(selected.name)} 저장`} />}
            <Button kind="text" label="나중에 고를게요" onPress={close} />
          </>
        )}
        {result.state === 'analyzing' && <Button kind="secondary" label="닫고 기다릴게요" onPress={close} />}
        {result.state === 'noPlace' && (
          <>
            <Button kind="primary" label="장소 직접 붙이기" />
            <Button kind="text" label="장소 없이 보관할게요" />
          </>
        )}
        {result.state === 'failed' && (
          <>
            <Button kind="secondary" label="다시 시도" />
            <Button kind="text" label="나중에 할게요" onPress={close} />
          </>
        )}
      </View>
    </ScrollView>
  );
}

function Message({ heading, body }: { heading: string; body: string }) {
  return (
    <View style={styles.message}>
      <Text style={styles.heading}>{heading}</Text>
      <Text style={styles.body}>{body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
  },
  topBar: {
    flexDirection: 'row',
    padding: spacing.md,
  },
  message: {
    gap: spacing.sm,
    paddingTop: spacing.xl,
    paddingHorizontal: spacing.lg,
  },
  heading: {
    ...typography.headingScreen,
    color: colors.text.primary,
  },
  body: {
    ...typography.bodyDefault,
    color: colors.text.secondary,
  },
  options: {
    gap: spacing.sm,
    paddingTop: spacing.lg,
    paddingHorizontal: spacing.lg,
  },
  direct: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: spacing.sm,
    paddingHorizontal: metrics.directOption.paddingHorizontal,
    paddingVertical: spacing.md,
  },
  directLabel: {
    ...typography.labelButton,
    color: colors.text.onSubtle,
  },
  spacer: {
    flex: 1,
  },
  actions: {
    gap: spacing.xs,
    paddingHorizontal: spacing.lg,
  },
});
