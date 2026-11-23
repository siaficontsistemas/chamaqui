/**
 * Máscaras e validações do cadastro de clientes (pré-cadastro).
 * Mantidas em sincronia com ClientPreRegistrationService no backend.
 */

export const EMPTY_CLIENT_USER = {
  fullName: '',
  phoneNumber: '',
  email: '',
  documentNumber: '',
  companyOwnerId: '',
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

/** Remove tudo que não for dígito. */
export function onlyDigits(value) {
  return String(value || '').replace(/\D/g, '')
}

/** Formata telefone brasileiro: (11) 91234-5678 ou (11) 3456-7890. */
export function formatPhone(value) {
  let digits = onlyDigits(value)
  if (digits.length > 11 && digits.startsWith('55')) {
    digits = digits.slice(2)
  }
  digits = digits.slice(0, 11)
  if (digits.length <= 2) return digits ? `(${digits}` : ''
  const areaCode = digits.slice(0, 2)
  const number = digits.slice(2)
  const splitAt = number.length > 8 ? 5 : 4
  if (number.length <= splitAt) return `(${areaCode}) ${number}`
  return `(${areaCode}) ${number.slice(0, splitAt)}-${number.slice(splitAt)}`
}

/** Formata CPF: 000.000.000-00. */
export function formatCpf(value) {
  const digits = onlyDigits(value).slice(0, 11)
  return digits
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2')
}

/** Valida CPF pelos dígitos verificadores. */
export function isValidCpf(value) {
  const cpf = onlyDigits(value)
  if (cpf.length !== 11 || /^(\d)\1{10}$/.test(cpf)) return false
  const checkDigit = (length) => {
    let sum = 0
    for (let index = 0; index < length; index += 1) {
      sum += Number(cpf[index]) * (length + 1 - index)
    }
    const remainder = (sum * 10) % 11
    return remainder === 10 ? 0 : remainder
  }
  return checkDigit(9) === Number(cpf[9]) && checkDigit(10) === Number(cpf[10])
}

/** Telefone com DDD: 10 (fixo) ou 11 (celular) dígitos, com ou sem 55. */
export function isValidPhone(value) {
  let digits = onlyDigits(value)
  if ((digits.length === 12 || digits.length === 13) && digits.startsWith('55')) {
    digits = digits.slice(2)
  }
  return digits.length === 10 || digits.length === 11
}

export function isValidEmail(value) {
  return EMAIL_PATTERN.test(String(value || '').trim())
}

/**
 * Valida o formulário e devolve um objeto { campo: mensagem }.
 * Objeto vazio significa formulário válido.
 */
export function validateClientUser(values) {
  const errors = {}
  const fullName = values.fullName.trim().replace(/\s+/g, ' ')

  if (!fullName) {
    errors.fullName = 'Informe o nome do cliente.'
  } else if (fullName.length < 3) {
    errors.fullName = 'O nome precisa ter pelo menos 3 letras.'
  }

  if (!onlyDigits(values.phoneNumber)) {
    errors.phoneNumber = 'Informe o telefone (WhatsApp) do cliente.'
  } else if (!isValidPhone(values.phoneNumber)) {
    errors.phoneNumber = 'Telefone inválido. Use DDD + número, por exemplo (11) 91234-5678.'
  }

  if (values.email.trim() && !isValidEmail(values.email)) {
    errors.email = 'E-mail inválido. Confira o formato, por exemplo nome@empresa.com.br.'
  }

  if (onlyDigits(values.documentNumber) && !isValidCpf(values.documentNumber)) {
    errors.documentNumber = 'CPF inválido. Confira os 11 dígitos.'
  }

  if (!values.companyOwnerId) {
    errors.companyOwnerId = 'Selecione a empresa cliente.'
  }

  return errors
}

/** Monta o corpo enviado à API a partir dos valores do formulário. */
export function toClientUserPayload(values) {
  return {
    fullName: values.fullName.trim().replace(/\s+/g, ' '),
    phoneNumber: onlyDigits(values.phoneNumber),
    email: values.email.trim().toLowerCase(),
    documentNumber: onlyDigits(values.documentNumber),
    companyOwnerId: values.companyOwnerId,
  }
}

/** Converte um pré-cadastro vindo da API nos valores do formulário. */
export function toClientUserFormValues(client) {
  return {
    fullName: client?.fullName || '',
    phoneNumber: formatPhone(client?.phoneNumber || ''),
    email: client?.email || '',
    documentNumber: formatCpf(client?.documentNumber || ''),
    companyOwnerId: client?.companyOwnerId || '',
  }
}
