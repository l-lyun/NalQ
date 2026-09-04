const DISCLOSURE_PREFIX = 'openmd.quiz-generation.disclosure.v1'

type QuizGenerationDisclosureRecord = {
  version: 1
  contentRevision: string
  confirmedAt: string
}

function key(userId: number, materialId: string) {
  return `${DISCLOSURE_PREFIX}:${userId}:${materialId}`
}

function isDisclosureRecord(value: unknown): value is QuizGenerationDisclosureRecord {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<QuizGenerationDisclosureRecord>
  return candidate.version === 1
    && typeof candidate.contentRevision === 'string'
    && typeof candidate.confirmedAt === 'string'
}

export async function getLearningMaterialContentRevision(content: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(content))
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export function hasConfirmedQuizGenerationDisclosure(
  userId: number,
  materialId: string,
  contentRevision: string,
) {
  try {
    const raw = localStorage.getItem(key(userId, materialId))
    if (!raw) return false
    const parsed: unknown = JSON.parse(raw)
    if (!isDisclosureRecord(parsed)) return false
    return parsed.contentRevision === contentRevision
  } catch {
    return false
  }
}

export function confirmQuizGenerationDisclosure(
  userId: number,
  materialId: string,
  contentRevision: string,
) {
  try {
    const record: QuizGenerationDisclosureRecord = {
      version: 1,
      contentRevision,
      confirmedAt: new Date().toISOString(),
    }
    localStorage.setItem(key(userId, materialId), JSON.stringify(record))
  } catch {
    // 저장소가 차단돼도 방금 확인한 생성 요청은 진행하고 다음 화면에서는 다시 확인한다.
  }
}
