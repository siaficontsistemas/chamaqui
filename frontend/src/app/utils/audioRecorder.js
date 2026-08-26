const AUDIO_MIME_TYPES = [
  'audio/ogg;codecs=opus',
  'audio/ogg',
  'audio/webm;codecs=opus',
  'audio/webm',
]

export function getSupportedAudioMimeType() {
  if (typeof MediaRecorder === 'undefined') return ''
  return AUDIO_MIME_TYPES.find((mimeType) => MediaRecorder.isTypeSupported(mimeType)) || ''
}

export function buildAudioFile(blob, mimeType) {
  const extension = mimeType.includes('ogg') ? 'ogg' : 'webm'
  return new File([blob], `audio-${Date.now()}.${extension}`, {
    type: mimeType || blob.type || 'audio/webm',
    lastModified: Date.now(),
  })
}
