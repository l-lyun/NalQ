import {
  getLearningMaterial,
  getLearningMaterials,
  updateLearningMaterial,
} from './learningMaterial.api'
import { quizRuntimeMode } from '../../quiz/model/quizFeature'
import type { QuizRuntimeMode } from '../../quiz/model/quizFeature'
import type {
  GetLearningMaterialsParams,
  LearningMaterialDetail,
  LearningMaterialPage,
  UpdateLearningMaterialRequest,
} from './learningMaterial.types'

export type LearningMaterialManagementMode = QuizRuntimeMode

export const learningMaterialManagementMode: LearningMaterialManagementMode =
  quizRuntimeMode

const mockMaterials: LearningMaterialDetail[] = [
  {
    materialId: 'material-operating-system',
    title: '운영체제 정리: 프로세스와 스레드, 스케줄링의 차이',
    content:
      '프로세스는 실행 중인 프로그램이며 각자 독립된 주소 공간을 가진다. 스레드는 프로세스 안에서 실행 흐름을 공유한다. 스케줄러는 준비 상태의 작업 가운데 CPU를 사용할 대상을 선택한다.',
    contentLength: 108,
    sourceType: 'NOTION',
    contentEditStatus: 'EDITABLE',
    createdAt: '2026-08-20T01:00:00Z',
    updatedAt: '2026-08-28T01:00:00Z',
  },
  {
    materialId: 'material-network',
    title: '네트워크 기초',
    content:
      'TCP는 연결 지향 전송을 제공하고 UDP는 연결 설정 없이 데이터그램을 전송한다. IP는 네트워크 사이에서 패킷을 전달한다.',
    contentLength: 72,
    sourceType: 'PASTE',
    contentEditStatus: 'LOCKED_GENERATING',
    createdAt: '2026-08-21T02:00:00Z',
    updatedAt: '2026-08-27T04:30:00Z',
  },
  {
    materialId: 'material-data-structure',
    title: '자료구조 핵심 개념',
    content:
      '스택은 후입선출, 큐는 선입선출 구조다. 트리는 계층 관계를, 그래프는 일반적인 정점과 간선 관계를 표현한다.',
    contentLength: 65,
    sourceType: 'PASTE',
    contentEditStatus: 'EDITABLE',
    createdAt: '2026-08-18T03:00:00Z',
    updatedAt: '2026-08-26T03:00:00Z',
  },
]

function ensureUpdateAvailable() {
  if (learningMaterialManagementMode === 'disabled') {
    throw new Error('학습자료 수정 API가 아직 배포되지 않았어요.')
  }
}

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException('요청이 취소되었습니다.', 'AbortError')
}

export async function listManagedLearningMaterials(
  params: GetLearningMaterialsParams,
  signal?: AbortSignal,
): Promise<LearningMaterialPage> {
  if (learningMaterialManagementMode !== 'mock') return getLearningMaterials(params, signal)
  throwIfAborted(signal)
  const query = params.query?.trim().toLocaleLowerCase('ko-KR') ?? ''
  const size = params.size ?? 6
  const filtered = mockMaterials.filter((item) =>
    item.title.toLocaleLowerCase('ko-KR').includes(query),
  )
  const start = (params.page - 1) * size
  return {
    items: filtered.slice(start, start + size).map(({ content: _content, contentLength: _length, createdAt: _createdAt, ...summary }) => summary),
    page: params.page,
    size,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
  }
}

export async function getManagedLearningMaterial(
  materialId: string,
  signal?: AbortSignal,
): Promise<LearningMaterialDetail> {
  if (learningMaterialManagementMode !== 'mock') return getLearningMaterial(materialId, signal)
  throwIfAborted(signal)
  const material = mockMaterials.find((item) => item.materialId === materialId)
  if (!material) throw new Error('학습자료를 찾지 못했어요.')
  return { ...material }
}

export async function updateManagedLearningMaterial(
  materialId: string,
  payload: UpdateLearningMaterialRequest,
): Promise<LearningMaterialDetail> {
  ensureUpdateAvailable()
  if (learningMaterialManagementMode === 'api') {
    return updateLearningMaterial(materialId, payload)
  }
  const index = mockMaterials.findIndex((item) => item.materialId === materialId)
  if (index < 0) throw new Error('학습자료를 찾지 못했어요.')
  const previous = mockMaterials[index]
  if (previous.contentEditStatus === 'LOCKED_GENERATING' && payload.content !== undefined) {
    throw new Error('문제를 만드는 동안에는 본문을 수정할 수 없어요.')
  }
  const content = payload.content ?? previous.content
  const updated: LearningMaterialDetail = {
    ...previous,
    ...payload,
    content,
    contentLength: [...content].length,
    updatedAt: new Date().toISOString(),
  }
  mockMaterials[index] = updated
  return { ...updated }
}
