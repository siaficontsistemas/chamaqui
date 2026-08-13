const DEFAULT_NOTIFICATION_URL = '/tickets'

self.addEventListener('push', (event) => {
  let payload = {}

  try {
    payload = event.data?.json?.() || {}
  } catch {
    payload = { body: event.data?.text?.() || 'Você tem uma nova atualização no ChamAqui.' }
  }

  const title = payload.title || 'ChamAqui Helpdesk'
  const options = {
    body: payload.body || 'Você tem uma nova atualização.',
    data: { url: payload.url || DEFAULT_NOTIFICATION_URL },
    tag: payload.tag || `chamaqui-${Date.now()}`,
    renotify: true,
  }

  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = new URL(event.notification.data?.url || DEFAULT_NOTIFICATION_URL, self.location.origin).href

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      const existingClient = clientList.find((client) => client.url.startsWith(self.location.origin))
      if (existingClient) {
        return existingClient.navigate(targetUrl).then(() => existingClient.focus())
      }
      return self.clients.openWindow(targetUrl)
    })
  )
})
