import type { QuestionResult, QuizResultResponse, ReviewResultResponse } from '../api/quiz.types'
import type {
  QuizAnswer,
  QuizAnswers,
  QuizQuestion,
  QuizQuestionType,
  QuizResponse,
  QuizResult,
  QuizResultItem,
  QuizSubmissionPayload,
} from '@/pages/quiz/quiz.types'

export function createSubmissionPayload(
  questions: QuizQuestion[],
  answers: QuizAnswers,
): QuizSubmissionPayload {
  const responses = questions.reduce<QuizResponse[]>((responses, question) => {
    const answer = answers[question.questionId]
    if (!answer || answer.type !== question.type) return responses
    if (answer.type === 'MULTIPLE_CHOICE') {
      if (answer.selectedChoiceId) {
        responses.push({ questionId: question.questionId, selectedChoiceId: answer.selectedChoiceId })
      }
      return responses
    }
    if (answer.type === 'FILL_IN_THE_BLANK') {
      const blankAnswers = question.type === 'FILL_IN_THE_BLANK'
        ? question.blanks.flatMap((blank) => {
            const value = answer.blankAnswers[blank.blankId]?.trim()
            return value ? [{ blankId: blank.blankId, answer: value }] : []
          })
        : []
      if (blankAnswers.length > 0) responses.push({ questionId: question.questionId, blankAnswers })
      return responses
    }
    const text = answer.text.trim()
    if (text) responses.push({ questionId: question.questionId, text })
    return responses
  }, [])
  return { responses }
}

export function isQuestionAnswered(question: QuizQuestion, answer?: QuizAnswer) {
  if (!answer || answer.type !== question.type) return false
  if (answer.type === 'MULTIPLE_CHOICE') return answer.selectedChoiceId.length > 0
  if (answer.type === 'FILL_IN_THE_BLANK') {
    return question.type === 'FILL_IN_THE_BLANK'
      ? question.blanks.every((blank) => answer.blankAnswers[blank.blankId]?.trim())
      : false
  }
  return answer.text.trim().length > 0
}

function choiceText(item: QuestionResult, choiceId?: string) {
  return item.choices?.find((choice) => choice.choiceId === choiceId)?.text ?? ''
}

function blankText(
  item: QuestionResult,
  values?: { blankId: string; answer: string }[],
) {
  if (!item.blanks) return ''
  const byId = new Map(values?.map((entry) => [entry.blankId, entry.answer]))
  return item.blanks
    .map((blank) => `${blank.number}번 ${byId.get(blank.blankId)?.trim() || '답하지 않음'}`)
    .join(' · ')
}

function toResultItem(item: QuestionResult): QuizResultItem {
  let answer = ''
  let correctAnswer = ''
  let keyPoints: string[] | undefined
  if (item.type === 'MULTIPLE_CHOICE') {
    const response = item.response && 'selectedChoiceId' in item.response ? item.response : undefined
    const representative = item.representativeAnswer && 'selectedChoiceId' in item.representativeAnswer
      ? item.representativeAnswer
      : undefined
    answer = item.response === null ? '' : choiceText(item, response?.selectedChoiceId)
    correctAnswer = choiceText(item, representative?.selectedChoiceId)
  } else if (item.type === 'FILL_IN_THE_BLANK') {
    const response = item.response && 'blankAnswers' in item.response ? item.response : undefined
    const representative = item.representativeAnswer && 'blankAnswers' in item.representativeAnswer
      ? item.representativeAnswer
      : undefined
    answer = item.response === null ? '' : blankText(item, response?.blankAnswers)
    correctAnswer = blankText(item, representative?.blankAnswers)
  } else if (item.type === 'ESSAY') {
    const response = item.response && 'answer' in item.response ? item.response.answer : ''
    const representative =
      item.representativeAnswer && 'modelAnswer' in item.representativeAnswer
        ? item.representativeAnswer
        : undefined
    answer = item.response === null ? '' : response
    correctAnswer = representative?.modelAnswer ?? ''
    keyPoints = representative?.keyPoints
  } else {
    const response = item.response && 'answer' in item.response ? item.response.answer : ''
    const representative = item.representativeAnswer && 'answer' in item.representativeAnswer
      ? item.representativeAnswer.answer
      : ''
    answer = item.response === null ? '' : response
    correctAnswer = representative
  }
  return {
    questionId: item.questionId,
    number: item.number,
    type: item.type,
    topic: item.topic,
    prompt: item.prompt,
    answer,
    correctAnswer,
    outcome: item.outcome ?? 'INCORRECT',
    keyPoints,
    explanation: item.explanation,
    sourceExcerpt: item.sourceExcerpt,
    editable:
      (item.type === 'SHORT_ANSWER' || item.type === 'FILL_IN_THE_BLANK') &&
      item.response !== null,
  }
}

export function adaptQuizResult(data: QuizResultResponse): QuizResult {
  return {
    kind: 'MAIN',
    status: data.status,
    summary: {
      correctCount: data.summary.scoredGrading.correctQuestionCount,
      gradedCount: data.summary.scoredGrading.gradedQuestionCount,
      essayCorrectCount: data.summary.essaySelfAssessment.correctCount,
      essayPartialCount: data.summary.essaySelfAssessment.partialCount,
      essayIncorrectCount: data.summary.essaySelfAssessment.incorrectCount,
      reviewCount: data.summary.reviewQuestionCount,
    },
    items: data.questionResults.map(toResultItem),
  }
}

export function adaptReviewResult(data: ReviewResultResponse): QuizResult {
  const items = data.questionResults.map(toResultItem)
  const automaticallyGraded = items.filter((item) => item.type !== 'ESSAY')
  const essays = items.filter((item) => item.type === 'ESSAY')
  return {
    kind: 'REVIEW',
    status: data.status,
    // Contract lines 794-799 do not define review summary JSON keys. Derive only the
    // already-visible counts until the shared contract names the server projection.
    summary: {
      correctCount: automaticallyGraded.filter((item) => item.outcome === 'CORRECT').length,
      gradedCount: automaticallyGraded.length,
      essayCorrectCount: essays.filter((item) => item.outcome === 'CORRECT').length,
      essayPartialCount: essays.filter((item) => item.outcome === 'PARTIAL').length,
      essayIncorrectCount: essays.filter((item) => item.outcome === 'INCORRECT').length,
      reviewCount: items.filter((item) => item.outcome !== 'CORRECT').length,
    },
    items,
  }
}

export function includedQuestionTypes(questions: QuizQuestion[]) {
  return [...new Set(questions.map((question) => question.type))] as QuizQuestionType[]
}
