const segmenter = typeof Intl !== 'undefined' && 'Segmenter' in Intl ? new Intl.Segmenter('ko', { granularity: 'grapheme' }) : null
const EMOJI_ONLY = /^(?:\p{Extended_Pictographic}|\p{Emoji_Modifier}|\p{Emoji_Component}|‍|️|\s)+$/u

/** 카카오톡처럼 이모티콘만 1~3개 보낸 메시지는 말풍선 없이 크게 보여준다 */
export function isBigEmoji(text: string) {
  const trimmed = text.trim()
  if (!trimmed || !EMOJI_ONLY.test(trimmed) || /^[\d#*\s]+$/.test(trimmed)) return false
  const count = segmenter ? [...segmenter.segment(trimmed.replace(/\s/g, ''))].length : [...trimmed].length
  return count <= 3
}
