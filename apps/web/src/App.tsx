import { lazy, Suspense, useEffect, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { PageLoader, Spinner } from './components/ui'
import { useAuth } from './providers/authContext'
import { ChatSocketProvider } from './providers/ChatSocketProvider'

// 화면마다 코드를 나눠서 처음에는 지금 보는 화면만 받는다 (모든 화면을 한 파일로 받으면 첫 로딩이 느림)
const pages = {
  auth: () => import('./pages/AuthPages'),
  findAccount: () => import('./pages/FindAccountPage'),
  discover: () => import('./pages/DiscoverPage'),
  gatherings: () => import('./pages/GatheringsPage'),
  gatheringDetail: () => import('./pages/GatheringDetailPage'),
  community: () => import('./pages/CommunityPage'),
  postDetail: () => import('./pages/PostDetailPage'),
  userProfile: () => import('./pages/UserProfilePage'),
  admin: () => import('./pages/AdminPage'),
  requests: () => import('./pages/RequestsPage'),
  chats: () => import('./pages/ChatsPage'),
  profile: () => import('./pages/ProfilePage'),
}
const LoginPage = lazy(() => pages.auth().then((m) => ({ default: m.LoginPage })))
const SignupPage = lazy(() => pages.auth().then((m) => ({ default: m.SignupPage })))
const FindAccountPage = lazy(() => pages.findAccount().then((m) => ({ default: m.FindAccountPage })))
const DiscoverPage = lazy(() => pages.discover().then((m) => ({ default: m.DiscoverPage })))
const GatheringsPage = lazy(() => pages.gatherings().then((m) => ({ default: m.GatheringsPage })))
const GatheringDetailPage = lazy(() => pages.gatheringDetail().then((m) => ({ default: m.GatheringDetailPage })))
const CommunityPage = lazy(() => pages.community().then((m) => ({ default: m.CommunityPage })))
const PostDetailPage = lazy(() => pages.postDetail().then((m) => ({ default: m.PostDetailPage })))
const UserProfilePage = lazy(() => pages.userProfile().then((m) => ({ default: m.UserProfilePage })))
const AdminPage = lazy(() => pages.admin().then((m) => ({ default: m.AdminPage })))
const RequestsPage = lazy(() => pages.requests().then((m) => ({ default: m.RequestsPage })))
const ChatsPage = lazy(() => pages.chats().then((m) => ({ default: m.ChatsPage })))
const ProfilePage = lazy(() => pages.profile().then((m) => ({ default: m.ProfilePage })))

/**
 * 로그인하면 브라우저가 한가할 때 나머지 화면 코드를 미리 받아 둔다.
 * 화면을 옮길 때 코드를 받는 동안 이전 화면이 그대로 보이는 틈을 없앤다 (첫 화면 용량은 그대로)
 */
function usePrefetchPages(loggedIn: boolean) {
  useEffect(() => {
    if (!loggedIn) return
    const prefetch = () => Object.values(pages).forEach((load) => void load().catch(() => undefined))
    if ('requestIdleCallback' in window) {
      const id = window.requestIdleCallback(prefetch, { timeout: 3000 })
      return () => window.cancelIdleCallback(id)
    }
    const timer = setTimeout(prefetch, 1500) // Safari에는 requestIdleCallback이 없다
    return () => clearTimeout(timer)
  }, [loggedIn])
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { loggedIn } = useAuth()
  return loggedIn ? children : <Navigate to="/login" replace />
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { loggedIn, afterLoginPath } = useAuth()
  return loggedIn ? <Navigate to={afterLoginPath} replace /> : children
}

export function App() {
  const { ready, loggedIn } = useAuth()
  usePrefetchPages(loggedIn)
  // 새로고침 직후 쿠키로 로그인 상태를 확인하는 동안은 로그인 화면으로 튕기지 않게 기다린다
  if (!ready) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner className="size-8" />
      </div>
    )
  }
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
          <Route path="/signup" element={<GuestOnly><SignupPage /></GuestOnly>} />
          <Route path="/find-account" element={<GuestOnly><FindAccountPage /></GuestOnly>} />
          <Route
            element={
              <RequireAuth>
                <ChatSocketProvider>
                  <AppLayout />
                </ChatSocketProvider>
              </RequireAuth>
            }
          >
            <Route index element={<DiscoverPage />} />
            <Route path="gatherings" element={<GatheringsPage />} />
            <Route path="gatherings/:gatheringId" element={<GatheringDetailPage />} />
            <Route path="community" element={<CommunityPage />} />
            <Route path="community/:postId" element={<PostDetailPage />} />
            <Route path="users/:userId" element={<UserProfilePage />} />
            <Route path="admin" element={<AdminPage />} />
            <Route path="requests" element={<RequestsPage />} />
            <Route path="chats" element={<ChatsPage />} />
            <Route path="chats/:roomId" element={<ChatsPage />} />
            <Route path="profile" element={<ProfilePage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
