import { getWebPushPublicKey, saveWebPushSubscription } from './api'

const DISMISSED_KEY = 'helpdesk.browser-notifications.dismissed'
const ACTIVE_KEY = 'helpdesk.web-push.active'

export function shouldOfferBrowserNotifications() {
  if (typeof window === 'undefined' || !('Notification' in window) || !('serviceWorker' in navigator) || !('PushManager' in window) || Notification.permission !== 'default') return false
  try { return localStorage.getItem(DISMISSED_KEY) !== 'true' } catch { return true }
}

export function dismissBrowserNotificationOffer() {
  try { localStorage.setItem(DISMISSED_KEY, 'true') } catch { /* somente nesta sessão */ }
}

function urlBase64ToUint8Array(value) {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0))
}

export async function enableBrowserNotifications() {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission

  const keyResponse = await getWebPushPublicKey()
  if (!keyResponse?.configured || !keyResponse.publicKey) {
    throw new Error('As notificações fora do navegador ainda não foram configuradas no servidor.')
  }

  const registration = await navigator.serviceWorker.register('/notification-service-worker.js')
  const existing = await registration.pushManager.getSubscription()
  const subscription = existing || await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(keyResponse.publicKey),
  })
  const json = subscription.toJSON()
  await saveWebPushSubscription({
    endpoint: subscription.endpoint,
    p256dh: json.keys?.p256dh || '',
    auth: json.keys?.auth || '',
  })
  try { localStorage.setItem(ACTIVE_KEY, 'true') } catch { /* segue ativo no navegador */ }
  return permission
}

export async function restoreBrowserPushSubscription() {
  if (Notification.permission !== 'granted' || !('serviceWorker' in navigator) || !('PushManager' in window)) return
  await enableBrowserNotifications()
}

export async function showTicketBrowserNotification(notification, userRole) {
  try {
    if (localStorage.getItem(ACTIVE_KEY) === 'true') return
  } catch { /* usa o fallback local */ }
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  const protocol = notification.ticketProtocol || 'sem protocolo'
  const ticketTitle = notification.ticketTitle || 'Chamado'
  let title
  let body = notification.messagePreview || ticketTitle
  if (notification.type === 'ticket-assignment') {
    title = `Novo chamado ${protocol}`
    body = `${notification.requesterName || 'Um cliente'} abriu: ${ticketTitle}`
  } else if (notification.type === 'ticket-closure') title = `Chamado ${protocol} foi fechado`
  else if (notification.type === 'ticket-reply') title = userRole === 'user' ? `Chamado ${protocol} foi respondido` : `Nova réplica em ${protocol}`
  else return
  const registration = await navigator.serviceWorker.register('/notification-service-worker.js')
  await registration.showNotification(title, {
    body, icon: '/logo_chamaqui.png', badge: '/favicon.svg',
    tag: `ticket-${notification.type}-${notification.id}`,
    data: { url: notification.ticketId ? `/tickets/${notification.ticketId}` : '/' },
  })
}
