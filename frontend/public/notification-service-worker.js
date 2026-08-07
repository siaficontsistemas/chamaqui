self.addEventListener('push', (event) => {
  let payload = {}
  try { payload = event.data ? event.data.json() : {} } catch { payload = { title: 'ChamAqui Helpdesk', body: event.data?.text() || '' } }
  event.waitUntil(self.registration.showNotification(payload.title || 'ChamAqui Helpdesk', {
    body: payload.body || '',
    icon: '/logo_chamaqui.png',
    badge: '/favicon.svg',
    tag: payload.tag || 'chamaqui-update',
    data: { url: payload.url || '/' },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
    const client = clients.find((item) => item.url.startsWith(self.location.origin))
    if (client) {
      client.navigate(targetUrl)
      return client.focus()
    }
    return self.clients.openWindow(targetUrl)
  }))
})
