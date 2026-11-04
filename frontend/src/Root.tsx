import { Center, Loader } from '@mantine/core'
import { Suspense, lazy, useEffect, useState, type ComponentType } from 'react'
import App from './App.tsx'

// Hidden page at #/architecture, loaded as a separate chunk only when opened.
const ArchitecturePage = lazy(() => import('./architecture/ArchitecturePage.tsx'))

// Optional local-only page (gitignored). import.meta.glob yields {} when the file is absent,
// so fresh clones and CI build without it.
const localPages = import.meta.glob<{ default: ComponentType }>('./architecture/InternalArchitecturePage.tsx')
const loadLocalPage = Object.values(localPages)[0]
const LocalPage = loadLocalPage ? lazy(loadLocalPage) : null

/** A few "routes" via the URL hash: no router library needed for hidden pages. */
export default function Root() {
  const [hash, setHash] = useState(window.location.hash)
  useEffect(() => {
    const onChange = () => { setHash(window.location.hash); window.scrollTo(0, 0) }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])

  const page = hash === '#/architecture' ? <ArchitecturePage />
    : hash === '#/internal' && LocalPage ? <LocalPage />
    : null
  if (page) {
    return <Suspense fallback={<Center h="100vh"><Loader /></Center>}>{page}</Suspense>
  }
  return <App />
}
