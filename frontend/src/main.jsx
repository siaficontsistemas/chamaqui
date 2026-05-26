import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import PlatformAdminApp from './admin/PlatformAdminApp.jsx'
import { TenantBrandingProvider } from './app/context/TenantBrandingContext'
import { isPlatformAdminHost } from './app/platformAdminHost'

const shouldRenderPlatformAdmin = isPlatformAdminHost()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      {shouldRenderPlatformAdmin ? (
        <PlatformAdminApp />
      ) : (
        <TenantBrandingProvider>
          <App />
        </TenantBrandingProvider>
      )}
    </BrowserRouter>
  </StrictMode>,
)
