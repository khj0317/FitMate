const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export class ImageError extends Error {}

/**
 * 업로드 전에 브라우저에서 사진을 줄인다.
 * - 휴대폰 원본(수 MB)을 그대로 보내지 않아 업로드가 빠르다
 * - createImageBitmap이 EXIF 회전 정보를 적용해 주므로, 서버가 메타데이터를 지워도 사진이 눕지 않는다
 * - 서버는 이 결과를 다시 검증·인코딩하므로 여기서 실패해도 보안에는 영향이 없다
 */
export async function compressImage(file: File, maxSide: number): Promise<Blob> {
  if (!file.type.startsWith('image/')) {
    throw new ImageError('사진 파일만 올릴 수 있어요')
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    // HEIC처럼 브라우저가 못 읽는 형식
    throw new ImageError('이 사진 형식은 지원하지 않아요. JPG나 PNG로 올려 주세요')
  }

  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')!
  context.fillStyle = '#ffffff' // 투명 배경은 흰색으로
  context.fillRect(0, 0, width, height)
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
  if (!blob) throw new ImageError('사진을 처리하지 못했어요')
  if (blob.size > MAX_UPLOAD_BYTES) throw new ImageError('사진이 너무 커요. 10MB 이하로 올려 주세요')
  return blob
}
