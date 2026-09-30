export const MAX_ATTACHMENT_FILE_SIZE_BYTES = 50 * 1024 * 1024
export const MAX_ATTACHMENT_REQUEST_SIZE_BYTES = 60 * 1024 * 1024

export function getAttachmentSizeError(files) {
  const oversizedFile = files.find((file) => file.size > MAX_ATTACHMENT_FILE_SIZE_BYTES)

  if (oversizedFile) {
    return `O arquivo ${oversizedFile.name} excede o limite de 50 MB.`
  }

  const totalSize = files.reduce((sum, file) => sum + file.size, 0)
  if (totalSize > MAX_ATTACHMENT_REQUEST_SIZE_BYTES) {
    return 'A soma dos anexos excede o limite de 60 MB por envio.'
  }

  return ''
}
